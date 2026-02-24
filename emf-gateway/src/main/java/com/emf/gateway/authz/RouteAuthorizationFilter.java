package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
import com.emf.gateway.auth.PublicPathMatcher;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Global filter that enforces route-level authorization.
 *
 * <p>When {@code emf.gateway.security.permissions-enabled} is false (default),
 * this filter only checks that the user is authenticated (valid JWT required).
 *
 * <p>When permissions are enabled, this filter additionally checks object-level
 * permissions for API paths:
 * <ul>
 *   <li>GET/HEAD/OPTIONS require {@code canRead}</li>
 *   <li>POST requires {@code canCreate}</li>
 *   <li>PUT/PATCH require {@code canEdit}</li>
 *   <li>DELETE requires {@code canDelete}</li>
 * </ul>
 *
 * <p>System permissions {@code VIEW_ALL_DATA} and {@code MODIFY_ALL_DATA} serve
 * as overrides for read and write operations respectively.
 *
 * <p>Actuator and internal paths are excluded from authentication requirements.
 */
@Component
public class RouteAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationFilter.class);

    private final RouteRegistry routeRegistry;
    private final boolean permissionsEnabled;
    private final PublicPathMatcher publicPathMatcher;

    public RouteAuthorizationFilter(
            RouteRegistry routeRegistry,
            @Value("${emf.gateway.security.permissions-enabled:false}") boolean permissionsEnabled,
            PublicPathMatcher publicPathMatcher) {
        this.routeRegistry = routeRegistry;
        this.permissionsEnabled = permissionsEnabled;
        this.publicPathMatcher = publicPathMatcher;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Allow public paths through without principal check
        if (publicPathMatcher.isPublicRequest(exchange)) {
            return chain.filter(exchange);
        }

        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);

        if (principal == null) {
            log.warn("No principal found in exchange for path: {}", path);
            return forbidden(exchange, "Authentication required");
        }

        log.debug("Authenticated user: {} accessing path: {}", principal.getUsername(), path);

        // If permissions enforcement is disabled, allow all authenticated users
        if (!permissionsEnabled) {
            return chain.filter(exchange);
        }

        // Check object-level permissions for API paths
        if (path.startsWith("/api/")) {
            return checkObjectPermissions(exchange, chain, path);
        }

        return chain.filter(exchange);
    }

    private Mono<Void> checkObjectPermissions(ServerWebExchange exchange,
                                               GatewayFilterChain chain,
                                               String path) {
        ResolvedPermissions permissions = PermissionResolutionFilter.getPermissions(exchange);
        if (permissions == null) {
            // No permissions resolved (error or no tenant context) — fail-open
            return chain.filter(exchange);
        }

        // All-permissive bypasses all checks (platform admin, disabled, error fallback)
        if (permissions.isAllPermissive()) {
            return chain.filter(exchange);
        }

        // Check API_ACCESS system permission
        if (!permissions.hasSystemPermission("API_ACCESS")) {
            log.warn("User denied API access (no API_ACCESS permission) for path: {}", path);
            return forbidden(exchange, "API access not permitted");
        }

        // Look up the route to get collection info
        Optional<RouteDefinition> route = routeRegistry.findByPath(path);
        if (route.isEmpty()) {
            // No route found — not a collection API call, allow through
            return chain.filter(exchange);
        }

        String collectionId = route.get().getId();
        String collectionName = route.get().getCollectionName();
        ObjectPermissions objPerms = permissions.getObjectPermissions(collectionId);
        HttpMethod method = exchange.getRequest().getMethod();

        boolean allowed = isAllowed(method, objPerms);

        // System permission overrides
        if (!allowed) {
            if (isReadMethod(method)) {
                allowed = permissions.hasSystemPermission("VIEW_ALL_DATA");
            } else {
                allowed = permissions.hasSystemPermission("MODIFY_ALL_DATA");
            }
        }

        if (!allowed) {
            log.warn("User denied {} on collection '{}' (id={}): insufficient object permissions",
                    method, collectionName, collectionId);
            return forbidden(exchange,
                    "Insufficient permissions for " + method + " on " + collectionName);
        }

        return chain.filter(exchange);
    }

    private boolean isAllowed(HttpMethod method, ObjectPermissions perms) {
        if (method == null) return false;
        if (method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS) {
            return perms.canRead();
        }
        if (method == HttpMethod.POST) {
            return perms.canCreate();
        }
        if (method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            return perms.canEdit();
        }
        if (method == HttpMethod.DELETE) {
            return perms.canDelete();
        }
        return false;
    }

    private boolean isReadMethod(HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS;
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");

        String errorJson = String.format(
            "{\"errors\":[{\"status\":\"403\",\"code\":\"FORBIDDEN\",\"detail\":\"%s\",\"meta\":{\"path\":\"%s\"}}]}",
            message,
            exchange.getRequest().getPath().value()
        );

        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(errorJson.getBytes()))
        );
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
