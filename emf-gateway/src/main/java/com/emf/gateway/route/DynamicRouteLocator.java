package com.emf.gateway.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Dynamic route locator that bridges the RouteRegistry with Spring Cloud Gateway's routing system.
 * 
 * This class converts our RouteDefinition objects into Spring Cloud Gateway Route objects
 * and provides them to the gateway's routing mechanism through a reactive Flux.
 * 
 * Routes are loaded dynamically from the RouteRegistry, which is updated through:
 * - Initial bootstrap from the control plane
 * - Real-time Kafka events for configuration changes
 * 
 * Validates: Requirements 1.3
 */
@Component
public class DynamicRouteLocator implements RouteLocator {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicRouteLocator.class);
    
    private final RouteRegistry routeRegistry;
    
    public DynamicRouteLocator(RouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
    }
    
    /**
     * Returns a Flux of Route objects from the RouteRegistry.
     * 
     * This method is called by Spring Cloud Gateway to discover available routes.
     * It converts each RouteDefinition from our registry into a Spring Cloud Gateway Route.
     * 
     * @return Flux<Route> containing all active routes
     */
    @Override
    public Flux<Route> getRoutes() {
        logger.debug("Loading routes from registry");
        
        return Flux.fromIterable(routeRegistry.getAllRoutes())
                .map(this::convertToRoute)
                .doOnNext(route -> logger.debug("Loaded route: {}", route.getId()))
                .doOnComplete(() -> logger.debug("Completed loading {} routes", routeRegistry.size()));
    }
    
    /**
     * Converts a RouteDefinition to a Spring Cloud Gateway Route.
     * 
     * The conversion process:
     * 1. Creates a Route with the same ID as the RouteDefinition
     * 2. Sets the URI to the backend service URL
     * 3. Configures a path predicate to match incoming requests
     * 4. Preserves the original path when forwarding to the backend
     *
     * <p>Note: Collection API path rewriting ({@code /api/xxx} → {@code /api/collections/xxx})
     * is handled by {@link com.emf.gateway.filter.CollectionPathRewriteFilter}, a GlobalFilter
     * that runs after route URL resolution but before the request is sent upstream.
     *
     * @param routeDefinition The route definition to convert
     * @return A Spring Cloud Gateway Route object
     */
    private Route convertToRoute(RouteDefinition routeDefinition) {
        try {
            URI uri = URI.create(routeDefinition.getBackendUrl());

            // Create a path matching async predicate
            AsyncPredicate<ServerWebExchange> pathPredicate = exchange -> {
                String requestPath = exchange.getRequest().getURI().getPath();
                boolean matches = matchesPath(requestPath, routeDefinition.getPath());
                return Mono.just(matches);
            };

            Route route = Route.async()
                    .id(routeDefinition.getId())
                    .uri(uri)
                    .asyncPredicate(pathPredicate)
                    .build();

            logger.trace("Converted RouteDefinition to Route: id={}, path={}, uri={}",
                    routeDefinition.getId(), routeDefinition.getPath(), uri);

            return route;

        } catch (IllegalArgumentException e) {
            logger.error("Failed to convert RouteDefinition to Route: invalid backend URL '{}' for route '{}'",
                    routeDefinition.getBackendUrl(), routeDefinition.getId(), e);
            throw new IllegalStateException("Invalid route configuration for route: " + routeDefinition.getId(), e);
        }
    }
    
    /**
     * Matches a request path against a route path pattern.
     * 
     * Supports Spring Cloud Gateway path patterns:
     * - Exact match: "/api/users" matches only "/api/users"
     * - Wildcard match: "/api/users/**" matches "/api/users/123", "/api/users/123/posts", etc.
     * - Single segment wildcard: "/api/users/*" matches "/api/users/123" but not "/api/users/123/posts"
     * 
     * @param requestPath The incoming request path
     * @param routePattern The route path pattern
     * @return true if the request path matches the pattern
     */
    private boolean matchesPath(String requestPath, String routePattern) {
        if (requestPath == null || routePattern == null) {
            return false;
        }
        
        // Exact match
        if (requestPath.equals(routePattern)) {
            return true;
        }
        
        // Handle /** wildcard (matches any number of path segments)
        if (routePattern.endsWith("/**")) {
            String prefix = routePattern.substring(0, routePattern.length() - 3);
            return requestPath.startsWith(prefix);
        }
        
        // Handle /* wildcard (matches single path segment)
        if (routePattern.endsWith("/*")) {
            String prefix = routePattern.substring(0, routePattern.length() - 2);
            if (!requestPath.startsWith(prefix)) {
                return false;
            }
            String remainder = requestPath.substring(prefix.length());
            // Should not contain additional slashes (only one segment)
            return !remainder.isEmpty() && !remainder.substring(1).contains("/");
        }
        
        return false;
    }
}
