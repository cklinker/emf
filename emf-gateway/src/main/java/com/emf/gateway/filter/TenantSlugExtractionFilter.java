package com.emf.gateway.filter;

import com.emf.gateway.tenant.TenantSlugCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Extracts the tenant slug from the first URL path segment and rewrites the
 * request path to strip it.
 * <p>
 * Incoming: {@code /{slug}/api/users/123} → rewritten to {@code /api/users/123}
 * with tenant attributes set on the exchange.
 * <p>
 * Implemented as a {@link WebFilter} (not a Gateway GlobalFilter) so that
 * path rewriting occurs <em>before</em> Spring Cloud Gateway's route matching.
 * This is essential because route predicates like {@code /internal/**} or
 * {@code /api/**} must see the bare (slug-stripped) path.
 * <p>
 * Platform paths (actuator, etc.) are exempted and pass through without a slug.
 * When {@code emf.gateway.tenant-slug.require-prefix} is {@code false}
 * (migration mode), requests without a slug prefix also pass through.
 */
@Component
public class TenantSlugExtractionFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantSlugExtractionFilter.class);

    /** Tenant slug pattern matching the Tenant entity validation. */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,61}[a-z0-9]$");

    private final TenantSlugCache slugCache;
    private final boolean enabled;
    private final boolean requirePrefix;
    private final List<String> platformPaths;

    public TenantSlugExtractionFilter(
            TenantSlugCache slugCache,
            @Value("${emf.gateway.tenant-slug.enabled:true}") boolean enabled,
            @Value("${emf.gateway.tenant-slug.require-prefix:false}") boolean requirePrefix,
            @Value("${emf.gateway.tenant-slug.platform-paths:/actuator,/platform}") List<String> platformPaths) {
        this.slugCache = slugCache;
        this.enabled = enabled;
        this.requirePrefix = requirePrefix;
        this.platformPaths = platformPaths;
    }

    @Override
    public int getOrder() {
        return -300;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();

        // Platform endpoints bypass slug requirement
        if (isPlatformPath(path)) {
            return chain.filter(exchange);
        }

        // Extract the first path segment
        String firstSegment = extractFirstSegment(path);

        if (firstSegment == null) {
            // Root "/" or empty path
            if (requirePrefix) {
                return notFound(exchange, "A tenant identifier is required in the URL path.");
            }
            return chain.filter(exchange);
        }

        // Check if the first segment looks like a valid slug
        if (!SLUG_PATTERN.matcher(firstSegment).matches()) {
            // Not a slug-shaped segment; pass through in migration mode
            if (!requirePrefix) {
                return chain.filter(exchange);
            }
            return notFound(exchange, "Invalid tenant identifier: " + firstSegment);
        }

        // Strip the slug segment from the path (must happen regardless of cache hit
        // so that downstream route matching sees bare paths like /api/**)
        String strippedPath = stripFirstSegment(path, firstSegment);
        if (strippedPath.isEmpty()) {
            strippedPath = "/";
        }

        // Resolve slug to tenant ID
        Optional<String> tenantId = slugCache.resolve(firstSegment);
        if (tenantId.isEmpty()) {
            if (requirePrefix) {
                return notFound(exchange, "Tenant not found: " + firstSegment);
            }
            // Slug pattern matched but not in cache — strip the segment anyway so
            // route matching works, but don't set tenant attributes. The downstream
            // TenantResolutionFilter or worker will resolve tenant from headers.
            log.warn("Slug '{}' matches pattern but is not in cache; stripping path but no tenant context set", firstSegment);
        } else {
            // Set tenant context on exchange attributes
            exchange.getAttributes().put(TenantResolutionFilter.TENANT_ID_ATTR, tenantId.get());
            log.debug("Resolved tenant slug '{}' (id={}), rewriting path '{}' → '{}'",
                    firstSegment, tenantId.get(), path, strippedPath);
        }

        // Always set the slug attribute so HeaderTransformationFilter can propagate it
        exchange.getAttributes().put(TenantResolutionFilter.TENANT_SLUG_ATTR, firstSegment);

        // Mutate the request with the stripped path
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .path(strippedPath)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Preserve original path for logging/error responses
        mutatedExchange.getAttributes().put("originalPath", path);

        return chain.filter(mutatedExchange);
    }

    /**
     * Extracts the first non-empty path segment.
     * For "/acme/api/users" returns "acme". For "/" returns null.
     */
    private String extractFirstSegment(String path) {
        if (path == null || path.length() <= 1) {
            return null;
        }
        // Skip leading slash
        String withoutLeading = path.substring(1);
        int slashIdx = withoutLeading.indexOf('/');
        return slashIdx > 0 ? withoutLeading.substring(0, slashIdx) : withoutLeading;
    }

    /**
     * Strips the first segment from the path.
     * "/acme/api/users" → "/api/users"
     * "/acme" → "/"
     */
    private String stripFirstSegment(String path, String segment) {
        // The segment starts after the leading "/"
        String prefix = "/" + segment;
        if (path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return path;
    }

    private boolean isPlatformPath(String path) {
        for (String prefix : platformPaths) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> notFound(ServerWebExchange exchange, String detail) {
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"errors\":[{\"status\":\"404\",\"code\":\"TENANT_NOT_FOUND\",\"title\":\"Tenant Not Found\",\"detail\":\"%s\"}]}",
                detail.replace("\"", "\\\""));

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
