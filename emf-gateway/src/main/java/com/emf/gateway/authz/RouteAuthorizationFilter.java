package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Global filter that enforces route-level authorization policies.
 * Runs after authentication (order 0, after -100) to check if the authenticated
 * principal has permission to access the requested route.
 * 
 * This filter:
 * - Extracts the collection ID from the matched route
 * - Looks up route policies for the collection and HTTP method
 * - Allows requests if no policy exists (default allow behavior)
 * - Evaluates policies against the principal using PolicyEvaluator
 * - Returns 403 Forbidden if the principal doesn't satisfy the policy
 * 
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5
 */
@Component
public class RouteAuthorizationFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationFilter.class);
    
    private final RouteRegistry routeRegistry;
    private final AuthzConfigCache authzConfigCache;
    private final PolicyEvaluator policyEvaluator;
    
    /**
     * Creates a new RouteAuthorizationFilter.
     *
     * @param routeRegistry the route registry for looking up route definitions
     * @param authzConfigCache the authorization config cache
     * @param policyEvaluator the policy evaluator for checking permissions
     */
    public RouteAuthorizationFilter(RouteRegistry routeRegistry,
                                    AuthzConfigCache authzConfigCache,
                                    PolicyEvaluator policyEvaluator) {
        this.routeRegistry = routeRegistry;
        this.authzConfigCache = authzConfigCache;
        this.policyEvaluator = policyEvaluator;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Get the authenticated principal (set by JwtAuthenticationFilter)
        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);
        
        // If no principal, authentication filter should have already rejected the request
        // This is a safety check
        if (principal == null) {
            log.warn("No principal found in exchange for path: {}", exchange.getRequest().getPath().value());
            return forbidden(exchange, "Authentication required");
        }
        
        // Extract request details
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        
        // Find the matching route to get the collection ID
        Optional<RouteDefinition> routeOpt = routeRegistry.findByPath(path);
        
        if (routeOpt.isEmpty()) {
            // No route found - this will be handled by Spring Cloud Gateway (404)
            log.debug("No route found for path: {}, skipping authorization", path);
            return chain.filter(exchange);
        }
        
        RouteDefinition route = routeOpt.get();
        String collectionId = route.getId(); // Route ID is the collection ID
        
        log.debug("Checking authorization for user: {}, collection: {}, method: {}, path: {}",
                principal.getUsername(), collectionId, method, path);
        
        // Lookup authorization config for the collection
        Optional<AuthzConfig> authzConfigOpt = authzConfigCache.getConfig(collectionId);
        
        if (authzConfigOpt.isEmpty()) {
            // No authorization config for this collection - allow by default
            log.debug("No authorization config found for collection: {}, allowing request", collectionId);
            return chain.filter(exchange);
        }
        
        AuthzConfig authzConfig = authzConfigOpt.get();
        
        // Find route policy for this HTTP method
        Optional<RoutePolicy> policyOpt = authzConfig.getRoutePolicies().stream()
                .filter(policy -> policy.getMethod().equalsIgnoreCase(method.name()))
                .findFirst();
        
        if (policyOpt.isEmpty()) {
            // No policy for this HTTP method - allow by default
            log.debug("No route policy found for collection: {}, method: {}, allowing request",
                    collectionId, method);
            return chain.filter(exchange);
        }
        
        RoutePolicy policy = policyOpt.get();
        
        // Evaluate policy against principal
        boolean allowed = policyEvaluator.evaluate(policy, principal);
        
        if (allowed) {
            log.debug("User: {} authorized for collection: {}, method: {}, policy: {}",
                    principal.getUsername(), collectionId, method, policy.getPolicyId());
            return chain.filter(exchange);
        } else {
            log.warn("User: {} denied access to collection: {}, method: {}, policy: {}, user roles: {}",
                    principal.getUsername(), collectionId, method, policy.getPolicyId(), principal.getRoles());
            return forbidden(exchange, "Insufficient permissions to access this resource");
        }
    }
    
    /**
     * Returns a forbidden response with the given error message.
     *
     * @param exchange the server web exchange
     * @param message the error message
     * @return a Mono that completes the response
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        
        String errorJson = String.format(
            "{\"error\":{\"status\":403,\"code\":\"FORBIDDEN\",\"message\":\"%s\",\"path\":\"%s\"}}",
            message,
            exchange.getRequest().getPath().value()
        );
        
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(errorJson.getBytes()))
        );
    }
    
    @Override
    public int getOrder() {
        return 0; // Run after authentication (-100) but before routing
    }
}
