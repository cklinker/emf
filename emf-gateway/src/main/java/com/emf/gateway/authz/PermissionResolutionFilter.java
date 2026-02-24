package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
import com.emf.gateway.filter.TenantResolutionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that resolves the effective permissions for the authenticated user
 * and attaches them to the exchange attributes for downstream filters.
 *
 * <p>Order: -5 (runs after JwtAuthenticationFilter at -100 and TenantResolutionFilter
 * at -200, but before RouteAuthorizationFilter at 0).
 *
 * <p>When {@code emf.gateway.security.permissions-enabled} is false (default),
 * this filter is a no-op.
 *
 * <p>Platform admins (users with PLATFORM_ADMIN role) bypass permission resolution
 * and receive all-permissive permissions.
 */
@Component
public class PermissionResolutionFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PermissionResolutionFilter.class);

    /** Exchange attribute key for resolved permissions. */
    public static final String PERMISSIONS_ATTRIBUTE = "gateway.permissions";

    private final PermissionResolutionService permissionService;
    private final boolean permissionsEnabled;

    public PermissionResolutionFilter(
            PermissionResolutionService permissionService,
            @Value("${emf.gateway.security.permissions-enabled:false}") boolean permissionsEnabled) {
        this.permissionService = permissionService;
        this.permissionsEnabled = permissionsEnabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!permissionsEnabled) {
            return chain.filter(exchange);
        }

        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);
        if (principal == null) {
            return chain.filter(exchange);
        }

        // Platform admins bypass permission resolution
        if (principal.hasRole("PLATFORM_ADMIN")) {
            exchange.getAttributes().put(PERMISSIONS_ATTRIBUTE, ResolvedPermissions.allPermissive());
            return chain.filter(exchange);
        }

        String tenantId = TenantResolutionFilter.getTenantId(exchange);
        if (tenantId == null) {
            return chain.filter(exchange);
        }

        String email = principal.getUsername();
        return permissionService.resolvePermissions(tenantId, email)
                .flatMap(perms -> {
                    exchange.getAttributes().put(PERMISSIONS_ATTRIBUTE, perms);
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return -5;
    }

    /**
     * Utility method for downstream filters to retrieve resolved permissions
     * from the exchange attributes.
     *
     * @param exchange the server web exchange
     * @return the resolved permissions, or null if not resolved
     */
    public static ResolvedPermissions getPermissions(ServerWebExchange exchange) {
        return exchange.getAttribute(PERMISSIONS_ATTRIBUTE);
    }
}
