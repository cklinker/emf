package com.emf.gateway.filter;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Global filter that transforms request headers before forwarding to backend services.
 * Runs with order 50 (after authentication and authorization, before forwarding).
 *
 * This filter:
 * - Preserves the Authorization header so downstream services can validate JWT tokens
 * - Adds X-Forwarded-User header with the authenticated principal's username
 * - Adds X-Forwarded-Roles header with comma-separated list of principal's roles
 * - Preserves all other request headers
 *
 * The backend services can use the X-Forwarded-User and X-Forwarded-Roles headers
 * for lightweight identity extraction, or validate the JWT themselves using the
 * preserved Authorization header.
 *
 * Validates: Requirements 9.4, 9.5, 9.6
 */
@Component
public class HeaderTransformationFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(HeaderTransformationFilter.class);
    
    private static final String X_FORWARDED_USER_HEADER = "X-Forwarded-User";
    private static final String X_FORWARDED_ROLES_HEADER = "X-Forwarded-Roles";
    private static final String X_TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String X_TENANT_SLUG_HEADER = "X-Tenant-Slug";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Retrieve the authenticated principal from exchange attributes
        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);
        
        // If no principal is present (unauthenticated request like bootstrap endpoint),
        // still propagate tenant headers if available
        if (principal == null) {
            log.debug("No principal found, skipping user header transformation for path: {}",
                    exchange.getRequest().getPath().value());
            String tenantId = TenantResolutionFilter.getTenantId(exchange);
            String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);
            if (tenantId != null || tenantSlug != null) {
                ServerHttpRequest req = exchange.getRequest().mutate()
                        .headers(headers -> {
                            if (tenantId != null) headers.set(X_TENANT_ID_HEADER, tenantId);
                            if (tenantSlug != null) headers.set(X_TENANT_SLUG_HEADER, tenantSlug);
                        })
                        .build();
                return chain.filter(exchange.mutate().request(req).build());
            }
            return chain.filter(exchange);
        }
        
        // Build the mutated request with transformed headers
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    // Add X-Forwarded-User header
                    headers.set(X_FORWARDED_USER_HEADER, principal.getUsername());
                    
                    // Add X-Forwarded-Roles header with comma-separated roles
                    String roles = principal.getRoles().stream()
                            .collect(Collectors.joining(","));
                    headers.set(X_FORWARDED_ROLES_HEADER, roles);

                    // Add tenant headers from exchange attributes (set by TenantResolutionFilter)
                    String tenantId = TenantResolutionFilter.getTenantId(exchange);
                    String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);
                    if (tenantId != null) {
                        headers.set(X_TENANT_ID_HEADER, tenantId);
                    }
                    if (tenantSlug != null) {
                        headers.set(X_TENANT_SLUG_HEADER, tenantSlug);
                    }
                    // Default to "default" tenant for authenticated requests with no tenant context.
                    // The UI doesn't send X-Tenant-ID/X-Tenant-Slug headers, so we inject
                    // the default slug so the control plane can resolve the proper tenant ID.
                    if (tenantId == null && tenantSlug == null) {
                        headers.set(X_TENANT_SLUG_HEADER, "default");
                        log.debug("No tenant context for authenticated user {}, defaulting to 'default' tenant",
                                principal.getUsername());
                    }

                    log.debug("Added forwarding headers for user: {}, roles: {}, tenantId: {}",
                            principal.getUsername(), roles, tenantId);
                })
                .build();
        
        // Create mutated exchange with the new request
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();
        
        return chain.filter(mutatedExchange);
    }
    
    @Override
    public int getOrder() {
        return 50; // Run after authentication (-100) and authorization (0), before forwarding
    }
}
