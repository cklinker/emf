package io.kelta.worker.module;

import io.kelta.runtime.module.ModuleManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The HTTP routes each tenant's loaded modules serve, keyed by module and method+path.
 *
 * <p>Tenant-scoped and populated on load, cleared on unload — the same lifecycle the action-handler
 * and hook registries follow, so a disabled module stops answering immediately rather than until the
 * next restart.
 *
 * <p>Routes live under the platform-owned {@code /api/modules/{moduleId}/x/} prefix, which the
 * gateway already treats as an authenticated static route. Nothing here can widen that: a module
 * declares a path and a handler, never a prefix and never an auth mode.
 *
 * @since 1.0.0
 */
@Component
public class ModuleRouteRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModuleRouteRegistry.class);

    /** tenantId -> moduleId -> "METHOD /path" -> handler key. */
    private final Map<String, Map<String, Map<String, String>>> routes = new ConcurrentHashMap<>();

    /** Registers a module's declared routes for one tenant, replacing any previous set. */
    public void register(String tenantId, String moduleId, List<ModuleManifest.RouteManifest> declared) {
        if (declared == null || declared.isEmpty()) {
            return;
        }
        Map<String, String> byKey = new ConcurrentHashMap<>();
        for (ModuleManifest.RouteManifest route : declared) {
            for (String method : route.methods()) {
                byKey.put(key(method, route.path()), route.handlerKey());
            }
        }
        routes.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>()).put(moduleId, byKey);
        log.info("Registered {} module route(s) for '{}' in tenant {}", byKey.size(), moduleId, tenantId);
    }

    /** Removes a module's routes for one tenant. */
    public void remove(String tenantId, String moduleId) {
        Map<String, Map<String, String>> byModule = routes.get(tenantId);
        if (byModule == null) {
            return;
        }
        if (byModule.remove(moduleId) != null) {
            log.info("Removed module routes for '{}' in tenant {}", moduleId, tenantId);
        }
        if (byModule.isEmpty()) {
            routes.remove(tenantId);
        }
    }

    /**
     * The handler key a request maps to, or empty when the module serves no such route.
     *
     * <p>Exact match only — no path parameters or wildcards. A module that wants a dynamic segment
     * reads it from the query string. Pattern matching here would need its own precedence rules,
     * and getting those subtly wrong is how one route silently shadows another.
     */
    public Optional<String> resolve(String tenantId, String moduleId, String method, String path) {
        return Optional.ofNullable(routes.getOrDefault(tenantId, Map.of()))
                .map(byModule -> byModule.get(moduleId))
                .map(byKey -> byKey.get(key(method, path)));
    }

    /** Number of routes registered for a module, for health reporting. */
    public int routeCount(String tenantId, String moduleId) {
        Map<String, Map<String, String>> byModule = routes.get(tenantId);
        if (byModule == null || byModule.get(moduleId) == null) {
            return 0;
        }
        return byModule.get(moduleId).size();
    }

    private static String key(String method, String path) {
        String normalised = path.endsWith("/") && path.length() > 1
                ? path.substring(0, path.length() - 1) : path;
        return method.toUpperCase(Locale.ROOT) + " " + normalised;
    }
}
