package com.emf.gateway.filter;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
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
 * - Removes the Authorization header from forwarded requests (security best practice)
 * - Adds X-Forwarded-User header with the authenticated principal's username
 * - Adds X-Forwarded-Roles header with comma-separated list of principal's roles
 * - Preserves all other request headers
 * 
 * The backend services can use the X-Forwarded-User and X-Forwarded-Roles headers
 * to identify the authenticated user and their permissions without needing to
 * validate JWT tokens themselves.
 * 
 * Validates: Requirements 9.4, 9.5, 9.6
 */
@Component
public class HeaderTransformationFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(HeaderTransformationFilter.class);
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String X_FORWARDED_USER_HEADER = "X-Forwarded-User";
    private static final String X_FORWARDED_ROLES_HEADER = "X-Forwarded-Roles";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Retrieve the authenticated principal from exchange attributes
        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);
        
        // If no principal is present (unauthenticated request like bootstrap endpoint),
        // just continue without header transformation
        if (principal == null) {
            log.debug("No principal found, skipping header transformation for path: {}", 
                    exchange.getRequest().getPath().value());
            return chain.filter(exchange);
        }
        
        // Build the mutated request with transformed headers
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    // Remove Authorization header
                    if (headers.containsKey(AUTHORIZATION_HEADER)) {
                        headers.remove(AUTHORIZATION_HEADER);
                        log.trace("Removed Authorization header for user: {}", principal.getUsername());
                    }
                    
                    // Add X-Forwarded-User header
                    headers.set(X_FORWARDED_USER_HEADER, principal.getUsername());
                    
                    // Add X-Forwarded-Roles header with comma-separated roles
                    String roles = principal.getRoles().stream()
                            .collect(Collectors.joining(","));
                    headers.set(X_FORWARDED_ROLES_HEADER, roles);
                    
                    log.debug("Added forwarding headers for user: {}, roles: {}", 
                            principal.getUsername(), roles);
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
