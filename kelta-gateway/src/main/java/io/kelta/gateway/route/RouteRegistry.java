package io.kelta.gateway.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory registry for route definitions.
 *
 * Routes are indexed by their path pattern for efficient lookup.
 * All operations are thread-safe using ConcurrentHashMap.
 *
 * This registry is updated dynamically through:
 * - Initial bootstrap from the worker service
 * - Real-time NATS events for configuration changes
 */
@Component
public class RouteRegistry {

    private static final Logger logger = LoggerFactory.getLogger(RouteRegistry.class);

    /**
     * Member-facing paths served by a dedicated controller (API_ACCESS-only at the
     * gateway; the controller owner-scopes the data). Each ALSO backs a system
     * collection whose auto-registered generic route shares this exact path — and,
     * being loaded after the static routes, would overwrite the static override in
     * {@link #routes} (keyed by path) and force per-resource collection Cerbos. A
     * portal member holds no collection role, so that check denies with 403 and the
     * member can never reach their own watches/wins/devices/billing (the dedicated
     * controller they were meant to hit).
     *
     * <p>These paths are therefore authoritative: only a {@code static-} route may
     * occupy them; a dynamic collection route for the same path is ignored. This is
     * deliberately NOT a blanket "static always wins" — config collections
     * (flows, reports, dashboards, …) are also registered as {@code static-} bootstrap
     * routes yet rely on their generic route's per-resource Cerbos for protection, and
     * must keep it. Only the member-controller routes below invert that.
     */
    private static final Set<String> AUTHORITATIVE_STATIC_PATHS = Set.of(
            "/api/watches/**", "/api/wins/**", "/api/devices/**", "/api/billing/**");

    private final ConcurrentHashMap<String, RouteDefinition> routes;

    public RouteRegistry() {
        this.routes = new ConcurrentHashMap<>();
    }

    private static boolean isStaticRoute(RouteDefinition route) {
        return route.getId() != null && route.getId().startsWith("static-");
    }

    /**
     * A dynamic (non-{@code static-}) route targeting an authoritative member path is
     * a shadow of the intended controller override and must be dropped. Returns true
     * when {@code route} should be ignored.
     */
    private boolean shadowsAuthoritativeStaticRoute(RouteDefinition route) {
        if (AUTHORITATIVE_STATIC_PATHS.contains(route.getPath()) && !isStaticRoute(route)) {
            logger.info("Ignoring dynamic route '{}' for authoritative member path '{}' — "
                    + "the static controller route owns it", route.getId(), route.getPath());
            return true;
        }
        return false;
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

        if (shadowsAuthoritativeStaticRoute(route)) {
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
     * Updates an existing route in the registry atomically.
     *
     * <p>The new definition is put first and any stale entry with the same ID at a
     * different path is pruned afterwards. The previous remove-then-add order left a
     * window with no route for the collection at all — a Spring Cloud Gateway route
     * rebuild (RefreshRoutesEvent) landing in that window served 404s for the
     * collection until the NEXT config event arrived. Frequent field-change events
     * (each publishes a collection UPDATED event) made that window easy to hit.
     *
     * @param route The updated route definition
     */
    public void updateRoute(RouteDefinition route) {
        if (route == null) {
            logger.warn("Attempted to update with null route");
            return;
        }
        if (route.getPath() == null || route.getPath().isEmpty()) {
            logger.error("Cannot update route with null or empty path: {}", route);
            return;
        }

        if (shadowsAuthoritativeStaticRoute(route)) {
            return;
        }

        RouteDefinition previous = routes.put(route.getPath(), route);

        // Prune entries for the same collection left at an old path (rename case).
        // Doing this AFTER the put means the collection always has at least one
        // live route; a brief overlap of old+new path is harmless (same backend).
        routes.entrySet().removeIf(entry -> {
            if (route.getId().equals(entry.getValue().getId())
                    && !entry.getKey().equals(route.getPath())) {
                logger.info("Pruned stale route for ID '{}' at old path '{}'", route.getId(), entry.getKey());
                return true;
            }
            return false;
        });

        if (previous != null) {
            logger.info("Updated route for path '{}': {}", route.getPath(), route);
        } else {
            logger.info("Registered route for path '{}': {}", route.getPath(), route);
        }
    }
    
    /**
     * Finds a route whose path pattern matches the given request path.
     * Supports exact match, /** (multi-segment wildcard), and /* (single-segment wildcard).
     *
     * @param path The request path to match against registered route patterns
     * @return Optional containing the matching route if found, empty otherwise
     */
    public Optional<RouteDefinition> findByPath(String path) {
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }

        // Try exact match first (most efficient)
        RouteDefinition exact = routes.get(path);
        if (exact != null) {
            return Optional.of(exact);
        }

        // Try wildcard matching against all registered patterns
        for (RouteDefinition route : routes.values()) {
            if (matchesPath(path, route.getPath())) {
                return Optional.of(route);
            }
        }

        return Optional.empty();
    }

    /**
     * Matches a request path against a route path pattern.
     * Supports /** (multi-segment) and /* (single-segment) wildcards.
     */
    private boolean matchesPath(String requestPath, String routePattern) {
        if (requestPath == null || routePattern == null) {
            return false;
        }

        if (requestPath.equals(routePattern)) {
            return true;
        }

        if (routePattern.endsWith("/**")) {
            // Segment boundary is required: /api/inventory/** must NOT match
            // /api/inventory-items. A raw startsWith lets one collection's
            // route shadow every hyphenated sibling, so authorization runs
            // against the wrong collection (deny at best, cross-collection
            // grant at worst) depending on registry iteration order.
            String prefix = routePattern.substring(0, routePattern.length() - 3);
            return requestPath.equals(prefix) || requestPath.startsWith(prefix + "/");
        }

        if (routePattern.endsWith("/*")) {
            String prefix = routePattern.substring(0, routePattern.length() - 2);
            if (!requestPath.startsWith(prefix + "/")) {
                return false;
            }
            String remainder = requestPath.substring(prefix.length() + 1);
            return !remainder.isEmpty() && !remainder.contains("/");
        }

        return false;
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
