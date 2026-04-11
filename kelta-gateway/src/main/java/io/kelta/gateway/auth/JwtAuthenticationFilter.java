package io.kelta.gateway.auth;

import io.kelta.gateway.filter.TenantResolutionFilter;
import io.kelta.gateway.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DynamicReactiveJwtDecoder jwtDecoder;
    private final PrincipalExtractor principalExtractor;
    private final PublicPathMatcher publicPathMatcher;
    private final GatewayMetrics metrics;

    /**
     * Creates a new JwtAuthenticationFilter.
     *
     * @param jwtDecoder the JWT decoder for validating tokens (tenant-aware)
     * @param principalExtractor the extractor for creating GatewayPrincipal from JWT
     * @param publicPathMatcher the matcher for public (unauthenticated) paths
     * @param metrics the gateway metrics service
     */
    public JwtAuthenticationFilter(DynamicReactiveJwtDecoder jwtDecoder, PrincipalExtractor principalExtractor,
                                   PublicPathMatcher publicPathMatcher, GatewayMetrics metrics) {
        this.jwtDecoder = jwtDecoder;
        this.principalExtractor = principalExtractor;
        this.publicPathMatcher = publicPathMatcher;
        this.metrics = metrics;
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
            metrics.recordAuthFailure(TenantResolutionFilter.getTenantSlug(exchange), "missing_token");
            return unauthorized(exchange, "Missing Authorization header");
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Invalid Authorization header format for path: {}", path);
            metrics.recordAuthFailure(TenantResolutionFilter.getTenantSlug(exchange), "invalid_format");
            return unauthorized(exchange, "Invalid Authorization header format. Expected 'Bearer <token>'");
        }

        // Extract token from header
        String token = authHeader.substring(BEARER_PREFIX.length());

        // Skip PAT tokens — they are handled by PatAuthenticationFilter
        if (token.startsWith("klt_")) {
            return chain.filter(exchange);
        }

        // Get tenant context (set by TenantSlugExtractionFilter / TenantResolutionFilter)
        // to scope JWT issuer validation to the correct tenant
        String tenantId = TenantResolutionFilter.getTenantId(exchange);
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("No tenant context available for JWT validation on path: {}", path);
            metrics.recordAuthFailure(TenantResolutionFilter.getTenantSlug(exchange), "missing_tenant_context");
            return unauthorized(exchange, "Tenant context required for authentication");
        }

        // Validate JWT with tenant-scoped issuer verification.
        // Error handlers are scoped to the decode+extract phase only — errors
        // from downstream filters (e.g. Cerbos connection issues) must NOT be
        // swallowed as 401 "Authentication failed".
        return jwtDecoder.decode(token, tenantId)
            .map(jwt -> {
                GatewayPrincipal principal = principalExtractor.extractPrincipal(jwt);
                log.debug("Successfully authenticated user: {} for path: {}", principal.getUsername(), path);

                // Enforce that the JWT's tenant_id matches the slug-resolved tenant.
                // This prevents cross-tenant access: a token issued for tenant A must not
                // be accepted on tenant B's slug.
                String jwtTenantId = principal.getTenantId();
                if (jwtTenantId != null && !jwtTenantId.isEmpty() && !jwtTenantId.equals(tenantId)) {
                    log.warn("Cross-tenant access attempt: JWT tenant={} request tenant={} path={}",
                            jwtTenantId, tenantId, path);
                    metrics.recordAuthFailure(TenantResolutionFilter.getTenantSlug(exchange), "tenant_mismatch");
                    throw new JwtException("JWT tenant_id does not match request tenant");
                }

                // Store principal in exchange attributes for downstream filters
                ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(exchange.getRequest())
                    .build();
                mutatedExchange.getAttributes().put(PRINCIPAL_ATTRIBUTE, principal);
                return mutatedExchange;
            })
            .onErrorResume(JwtException.class, e -> {
                log.warn("JWT validation failed for path: {}: {}", path, e.getMessage());
                metrics.recordAuthFailure(TenantResolutionFilter.getTenantSlug(exchange), "invalid_token");
                return unauthorized(exchange, "Invalid or expired JWT token")
                        .then(Mono.empty());
            })
            .onErrorResume(IllegalArgumentException.class, e -> {
                log.error("Failed to extract principal from JWT for path: {}", path, e);
                metrics.recordAuthFailure(TenantResolutionFilter.getTenantSlug(exchange), "invalid_claims");
                return unauthorized(exchange, "Invalid JWT claims: " + e.getMessage())
                        .then(Mono.empty());
            })
            .flatMap(chain::filter);
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

        String path = exchange.getRequest().getPath().value();
        String errorJson;
        try {
            errorJson = OBJECT_MAPPER.writeValueAsString(Map.of(
                    "error", Map.of(
                            "status", 401,
                            "code", "UNAUTHORIZED",
                            "message", message,
                            "path", path
                    )
            ));
        } catch (JacksonException e) {
            log.error("Failed to serialize error response", e);
            errorJson = "{\"error\":{\"status\":401,\"code\":\"UNAUTHORIZED\"}}";
        }

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
