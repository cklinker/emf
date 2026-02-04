package com.emf.gateway.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory registry for route definitions.
 * 
 * Routes are indexed by their path pattern for efficient lookup.
 * All operations are thread-safe using ConcurrentHashMap.
 * 
 * This registry is updated dynamically through:
 * - Initial bootstrap from the control plane
 * - Real-time Kafka events for configuration changes
 */
@Component
public class RouteRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(RouteRegistry.class);
    
    private final ConcurrentHashMap<String, RouteDefinition> routes;
    
    public RouteRegistry() {
        this.routes = new ConcurrentHashMap<>();
    }
    
    /**
     * Adds a new route to the registry.
     * If a route with the same path already exists, it will be replaced.
     * 
     * @param route The route definition to add
     */
    public void addRoute(RouteDefinition route) {
        if (route == null) {
            logger.warn("Attempted to add null route to registry");
            return;
        }
        
        if (route.getPath() == null || route.getPath().isEmpty()) {
            logger.error("Cannot add route with null or empty path: {}", route);
            return;
        }
        
        RouteDefinition previous = routes.put(route.getPath(), route);
        if (previous != null) {
            logger.info("Updated existing route for path '{}': {}", route.getPath(), route);
        } else {
            logger.info("Added new route for path '{}': {}", route.getPath(), route);
        }
    }
    
    /**
     * Removes a route from the registry by its route ID.
     * 
     * @param routeId The unique identifier of the route to remove
     */
    public void removeRoute(String routeId) {
        if (routeId == null || routeId.isEmpty()) {
            logger.warn("Attempted to remove route with null or empty ID");
            return;
        }
        
        // Find and remove the route with matching ID
        routes.entrySet().removeIf(entry -> {
            if (routeId.equals(entry.getValue().getId())) {
                logger.info("Removed route with ID '{}' at path '{}'", routeId, entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * Updates an existing route in the registry.
     * This is equivalent to adding a route, as it will replace any existing route
     * with the same path.
     * 
     * @param route The updated route definition
     */
    public void updateRoute(RouteDefinition route) {
        if (route == null) {
            logger.warn("Attempted to update with null route");
            return;
        }
        
        // First remove any existing route with the same ID but different path
        removeRoute(route.getId());
        
        // Then add the updated route
        addRoute(route);
    }
    
    /**
     * Finds a route by its path pattern.
     * 
     * @param path The path pattern to search for
     * @return Optional containing the route if found, empty otherwise
     */
    public Optional<RouteDefinition> findByPath(String path) {
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }
        
        return Optional.ofNullable(routes.get(path));
    }
    
    /**
     * Returns all routes currently registered.
     * 
     * @return A list of all route definitions (copy to prevent external modification)
     */
    public List<RouteDefinition> getAllRoutes() {
        return new ArrayList<>(routes.values());
    }
    
    /**
     * Clears all routes from the registry.
     * This is typically used during testing or full configuration reloads.
     */
    public void clear() {
        int count = routes.size();
        routes.clear();
        logger.info("Cleared {} routes from registry", count);
    }
    
    /**
     * Returns the number of routes currently registered.
     */
    public int size() {
        return routes.size();
    }
    
    /**
     * Checks if the registry is empty.
     */
    public boolean isEmpty() {
        return routes.isEmpty();
    }
}
