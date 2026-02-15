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
 * Global filter that enforces route-level authorization.
 * Uses profile-based permissions fetched from control plane via ProfilePolicyEvaluator.
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5
 */
@Component
public class RouteAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationFilter.class);

    private final RouteRegistry routeRegistry;
    private final ProfilePolicyEvaluator profilePolicyEvaluator;

    public RouteAuthorizationFilter(RouteRegistry routeRegistry,
                                    ProfilePolicyEvaluator profilePolicyEvaluator) {
        this.routeRegistry = routeRegistry;
        this.profilePolicyEvaluator = profilePolicyEvaluator;
        log.info("Route authorization using profile-based permissions");
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

        return profilePolicyEvaluator.evaluate(principal, collectionId, method)
                .flatMap(allowed -> {
                    if (allowed) {
                        log.debug("User: {} authorized for collection: {}, method: {}",
                                principal.getUsername(), collectionId, method);
                        return chain.filter(exchange);
                    } else {
                        log.warn("User: {} denied access to collection: {}, method: {}",
                                principal.getUsername(), collectionId, method);
                        return forbidden(exchange, "Insufficient permissions to access this resource");
                    }
                });
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
