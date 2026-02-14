package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
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
 * Global filter that enforces route-level authorization policies.
 * Supports two modes controlled by the emf.gateway.use-profiles feature flag:
 * - Legacy mode (default): Uses role-based RoutePolicy evaluation via PolicyEvaluator
 * - Profile mode: Uses profile-based permissions fetched from control plane via ProfilePolicyEvaluator
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5
 */
@Component
public class RouteAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationFilter.class);

    private final RouteRegistry routeRegistry;
    private final AuthzConfigCache authzConfigCache;
    private final PolicyEvaluator policyEvaluator;
    private final ProfilePolicyEvaluator profilePolicyEvaluator;
    private final boolean useProfiles;

    public RouteAuthorizationFilter(RouteRegistry routeRegistry,
                                    AuthzConfigCache authzConfigCache,
                                    PolicyEvaluator policyEvaluator,
                                    ProfilePolicyEvaluator profilePolicyEvaluator,
                                    @Value("${emf.gateway.use-profiles:false}") boolean useProfiles) {
        this.routeRegistry = routeRegistry;
        this.authzConfigCache = authzConfigCache;
        this.policyEvaluator = policyEvaluator;
        this.profilePolicyEvaluator = profilePolicyEvaluator;
        this.useProfiles = useProfiles;

        if (useProfiles) {
            log.info("Route authorization using profile-based permissions");
        } else {
            log.info("Route authorization using legacy role-based policies");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Allow unauthenticated access to bootstrap endpoints
        if ("/control/bootstrap".equals(path) || "/control/ui-bootstrap".equals(path)) {
            return chain.filter(exchange);
        }

        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);

        if (principal == null) {
            log.warn("No principal found in exchange for path: {}", path);
            return forbidden(exchange, "Authentication required");
        }
        HttpMethod method = exchange.getRequest().getMethod();

        Optional<RouteDefinition> routeOpt = routeRegistry.findByPath(path);

        if (routeOpt.isEmpty()) {
            log.debug("No route found for path: {}, skipping authorization", path);
            return chain.filter(exchange);
        }

        RouteDefinition route = routeOpt.get();
        String collectionId = route.getId();

        log.debug("Checking authorization for user: {}, collection: {}, method: {}, path: {}",
                principal.getUsername(), collectionId, method, path);

        if (useProfiles) {
            return evaluateWithProfiles(exchange, chain, principal, collectionId, method);
        } else {
            return evaluateWithPolicies(exchange, chain, principal, collectionId, method);
        }
    }

    /**
     * Profile-based authorization: check effective permissions from control plane.
     */
    private Mono<Void> evaluateWithProfiles(ServerWebExchange exchange, GatewayFilterChain chain,
                                             GatewayPrincipal principal, String collectionId, HttpMethod method) {
        return profilePolicyEvaluator.evaluate(principal, collectionId, method)
                .flatMap(allowed -> {
                    if (allowed) {
                        log.debug("User: {} authorized (profile) for collection: {}, method: {}",
                                principal.getUsername(), collectionId, method);
                        return chain.filter(exchange);
                    } else {
                        log.warn("User: {} denied (profile) access to collection: {}, method: {}",
                                principal.getUsername(), collectionId, method);
                        return forbidden(exchange, "Insufficient permissions to access this resource");
                    }
                });
    }

    /**
     * Legacy role-based authorization: check route policies from authz config cache.
     * Falls back to profile-based evaluation when the in-memory cache has no entry,
     * which can happen after a gateway restart before Kafka events repopulate the cache.
     */
    private Mono<Void> evaluateWithPolicies(ServerWebExchange exchange, GatewayFilterChain chain,
                                             GatewayPrincipal principal, String collectionId, HttpMethod method) {
        Optional<AuthzConfig> authzConfigOpt = authzConfigCache.getConfig(collectionId);

        if (authzConfigOpt.isEmpty()) {
            log.debug("No authorization config in cache for collection: {}, falling back to profile-based evaluation",
                    collectionId);
            return evaluateWithProfiles(exchange, chain, principal, collectionId, method);
        }

        AuthzConfig authzConfig = authzConfigOpt.get();

        Optional<RoutePolicy> policyOpt = authzConfig.getRoutePolicies().stream()
                .filter(policy -> policy.getMethod().equalsIgnoreCase(method.name()))
                .findFirst();

        if (policyOpt.isEmpty()) {
            log.debug("No route policy found for collection: {}, method: {}, allowing request",
                    collectionId, method);
            return chain.filter(exchange);
        }

        RoutePolicy policy = policyOpt.get();
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
