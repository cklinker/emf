package com.emf.gateway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that validates JWT tokens and extracts authenticated principal information.
 * Runs early in the filter chain (order -100) before routing decisions are made.
 * 
 * This filter:
 * - Extracts the Authorization header from incoming requests
 * - Validates JWT tokens using Spring Security's ReactiveJwtDecoder
 * - Stores the extracted GatewayPrincipal in ServerWebExchange attributes
 * - Returns 401 Unauthorized for missing, invalid, or expired tokens
 * - Allows unauthenticated access to actuator and platform paths
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PRINCIPAL_ATTRIBUTE = "gateway.principal";

    private final ReactiveJwtDecoder jwtDecoder;
    private final PrincipalExtractor principalExtractor;
    private final PublicPathMatcher publicPathMatcher;

    /**
     * Creates a new JwtAuthenticationFilter.
     *
     * @param jwtDecoder the JWT decoder for validating tokens
     * @param principalExtractor the extractor for creating GatewayPrincipal from JWT
     * @param publicPathMatcher the matcher for public (unauthenticated) paths
     */
    public JwtAuthenticationFilter(ReactiveJwtDecoder jwtDecoder, PrincipalExtractor principalExtractor,
                                   PublicPathMatcher publicPathMatcher) {
        this.jwtDecoder = jwtDecoder;
        this.principalExtractor = principalExtractor;
        this.publicPathMatcher = publicPathMatcher;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // Allow CORS preflight requests through — browsers send OPTIONS without credentials
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            log.debug("Allowing CORS preflight request for path: {}", path);
            return chain.filter(exchange);
        }

        // Allow unauthenticated access to public bootstrap paths (GET/HEAD only)
        if (publicPathMatcher.isPublicRequest(exchange)) {
            log.debug("Allowing unauthenticated access to public path: {}", path);
            return chain.filter(exchange);
        }

        // Extract Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("Missing Authorization header for path: {}", path);
            return unauthorized(exchange, "Missing Authorization header");
        }
        
        if (!authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Invalid Authorization header format for path: {}", path);
            return unauthorized(exchange, "Invalid Authorization header format. Expected 'Bearer <token>'");
        }
        
        // Extract token from header
        String token = authHeader.substring(BEARER_PREFIX.length());
        
        // Validate JWT and extract principal
        return jwtDecoder.decode(token)
            .flatMap(jwt -> {
                try {
                    GatewayPrincipal principal = principalExtractor.extractPrincipal(jwt);
                    log.debug("Successfully authenticated user: {} for path: {}", principal.getUsername(), path);
                    
                    // Store principal in exchange attributes for downstream filters
                    ServerWebExchange mutatedExchange = exchange.mutate()
                        .request(exchange.getRequest())
                        .build();
                    mutatedExchange.getAttributes().put(PRINCIPAL_ATTRIBUTE, principal);
                    
                    return chain.filter(mutatedExchange);
                } catch (IllegalArgumentException e) {
                    log.error("Failed to extract principal from JWT for path: {}", path, e);
                    return unauthorized(exchange, "Invalid JWT claims: " + e.getMessage());
                }
            })
            .onErrorResume(JwtException.class, e -> {
                log.warn("JWT validation failed for path: {}: {}", path, e.getMessage());
                return unauthorized(exchange, "Invalid or expired JWT token");
            })
            .onErrorResume(e -> {
                log.error("Unexpected error during JWT validation for path: {}", path, e);
                return unauthorized(exchange, "Authentication failed");
            });
    }
    
    /**
     * Returns an unauthorized response with the given error message.
     *
     * @param exchange the server web exchange
     * @param message the error message
     * @return a Mono that completes the response
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        
        String errorJson = String.format(
            "{\"error\":{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"%s\",\"path\":\"%s\"}}",
            message,
            exchange.getRequest().getPath().value()
        );
        
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(errorJson.getBytes()))
        );
    }
    
    @Override
    public int getOrder() {
        return -100; // Run early in the filter chain
    }
    
    /**
     * Retrieves the GatewayPrincipal from the ServerWebExchange attributes.
     * This is a utility method for downstream filters to access the authenticated principal.
     *
     * @param exchange the server web exchange
     * @return the GatewayPrincipal, or null if not authenticated
     */
    public static GatewayPrincipal getPrincipal(ServerWebExchange exchange) {
        return exchange.getAttribute(PRINCIPAL_ATTRIBUTE);
    }
}
