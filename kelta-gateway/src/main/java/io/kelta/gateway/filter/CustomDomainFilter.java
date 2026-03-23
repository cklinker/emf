package io.kelta.gateway.filter;

import io.kelta.gateway.cache.GatewayCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves tenant from custom domain (Host header) before slug-based resolution.
 *
 * <p>When a custom domain is configured (e.g., app.acme.com → tenant "acme"),
 * the tenant is resolved from the Host header, bypassing the slug URL prefix.
 * Unknown domains fall through to {@link TenantSlugExtractionFilter}.
 *
 * @since 1.0.0
 */
@Component
public class CustomDomainFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CustomDomainFilter.class);

    /** Valid domain pattern: alphanumeric, dots, hyphens only. */
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9.-]{0,253}[a-zA-Z0-9]$");

    static final String CUSTOM_DOMAIN_RESOLVED = "custom.domain.resolved";

    private final GatewayCacheManager cacheManager;

    public CustomDomainFilter(GatewayCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String host = exchange.getRequest().getHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            return chain.filter(exchange);
        }

        // Sanitize: strip port, validate format
        String domain = sanitizeHost(host);
        if (domain == null) {
            return chain.filter(exchange);
        }

        // Look up custom domain in cache
        Optional<String> tenantSlug = cacheManager.resolveCustomDomain(domain);
        if (tenantSlug.isEmpty()) {
            return chain.filter(exchange); // Fall through to slug resolution
        }

        // Resolve tenant from custom domain
        String slug = tenantSlug.get();
        log.debug("Custom domain {} resolved to tenant {}", domain, slug);

        // Set tenant attributes (same as TenantSlugExtractionFilter would)
        exchange.getAttributes().put(TenantResolutionFilter.TENANT_SLUG_ATTR, slug);
        exchange.getAttributes().put(CUSTOM_DOMAIN_RESOLVED, true);

        return chain.filter(exchange);
    }

    /**
     * Sanitizes the Host header: strips port, validates domain format.
     * Returns null if invalid.
     */
    static String sanitizeHost(String host) {
        if (host == null) return null;

        // Strip port
        int colonIdx = host.indexOf(':');
        String domain = colonIdx > 0 ? host.substring(0, colonIdx) : host;

        // Remove trailing dot (FQDN)
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }

        domain = domain.toLowerCase().trim();

        // Validate format
        if (domain.isEmpty() || !DOMAIN_PATTERN.matcher(domain).matches()) {
            return null;
        }

        // Reject obviously invalid
        if (domain.contains("..") || domain.startsWith("-") || domain.startsWith(".")) {
            return null;
        }

        return domain;
    }

    @Override
    public int getOrder() {
        return -300; // Before TenantSlugExtractionFilter (-200)
    }
}
