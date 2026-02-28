package com.emf.runtime.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that discovers and manages {@link ActionHandler} implementations.
 * <p>
 * Stores a map of {@code actionTypeKey -> ActionHandler}. Handlers can be
 * registered programmatically (by modules) or discovered via Spring dependency injection.
 * <p>
 * Thread-safe: uses ConcurrentHashMap for the handler registry.
 *
 * @since 1.0.0
 */
public class ActionHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActionHandlerRegistry.class);

    private final Map<String, ActionHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ActionHandler>> tenantHandlers = new ConcurrentHashMap<>();

    /**
     * Creates an empty registry.
     */
    public ActionHandlerRegistry() {
    }

    /**
     * Creates the registry and registers an initial list of handlers.
     *
     * @param discoveredHandlers the list of handlers to register
     */
    public ActionHandlerRegistry(List<ActionHandler> discoveredHandlers) {
        if (discoveredHandlers != null) {
            for (ActionHandler handler : discoveredHandlers) {
                register(handler);
            }
        }
        log.info("ActionHandlerRegistry initialized with {} handlers: {}",
            handlers.size(), handlers.keySet());
    }

    /**
     * Registers a handler. If a handler with the same key already exists, it is replaced.
     *
     * @param handler the handler to register
     */
    public void register(ActionHandler handler) {
        String key = handler.getActionTypeKey();
        ActionHandler existing = handlers.put(key, handler);
        if (existing != null) {
            log.warn("Duplicate ActionHandler for key '{}': {} replaced by {}",
                key, existing.getClass().getSimpleName(), handler.getClass().getSimpleName());
        }
        log.info("Registered ActionHandler: {} -> {}", key, handler.getClass().getSimpleName());
    }

    /**
     * Gets the handler for the specified action type key.
     *
     * @param actionTypeKey the action type key (e.g., "FIELD_UPDATE")
     * @return the handler, or empty if no handler is registered for the key
     */
    public Optional<ActionHandler> getHandler(String actionTypeKey) {
        return Optional.ofNullable(handlers.get(actionTypeKey));
    }

    /**
     * Gets the handler for the specified action type key, checking tenant-scoped
     * handlers first, then falling back to global handlers.
     *
     * @param tenantId the tenant ID to check for tenant-scoped handlers
     * @param actionTypeKey the action type key
     * @return the handler, or empty if no handler is registered
     */
    public Optional<ActionHandler> getHandler(String tenantId, String actionTypeKey) {
        // Check tenant-scoped handlers first
        Map<String, ActionHandler> tenantMap = tenantHandlers.get(tenantId);
        if (tenantMap != null) {
            ActionHandler tenantHandler = tenantMap.get(actionTypeKey);
            if (tenantHandler != null) {
                return Optional.of(tenantHandler);
            }
        }
        // Fall back to global handlers
        return Optional.ofNullable(handlers.get(actionTypeKey));
    }

    /**
     * Registers a tenant-scoped handler. These take priority over global handlers
     * for the specified tenant.
     *
     * @param tenantId the tenant ID
     * @param handler the handler to register
     */
    public void registerTenantHandler(String tenantId, ActionHandler handler) {
        String key = handler.getActionTypeKey();
        tenantHandlers.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
            .put(key, handler);
        log.info("Registered tenant-scoped ActionHandler: tenant={}, key={} -> {}",
            tenantId, key, handler.getClass().getSimpleName());
    }

    /**
     * Removes all tenant-scoped handlers for a specific module.
     *
     * @param tenantId the tenant ID
     * @param actionKeys the handler keys to remove
     */
    public void removeTenantHandlers(String tenantId, Set<String> actionKeys) {
        Map<String, ActionHandler> tenantMap = tenantHandlers.get(tenantId);
        if (tenantMap != null) {
            for (String key : actionKeys) {
                tenantMap.remove(key);
                log.info("Removed tenant-scoped ActionHandler: tenant={}, key={}", tenantId, key);
            }
            if (tenantMap.isEmpty()) {
                tenantHandlers.remove(tenantId);
            }
        }
    }

    /**
     * Gets all registered handler keys, including tenant-scoped handlers.
     *
     * @param tenantId the tenant ID (may be null for global-only)
     * @return all available handler keys for the tenant
     */
    public Set<String> getRegisteredKeys(String tenantId) {
        Set<String> keys = new HashSet<>(handlers.keySet());
        if (tenantId != null) {
            Map<String, ActionHandler> tenantMap = tenantHandlers.get(tenantId);
            if (tenantMap != null) {
                keys.addAll(tenantMap.keySet());
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    /**
     * Gets all registered handler keys.
     */
    public Set<String> getRegisteredKeys() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    /**
     * Checks if a handler is registered for the given key.
     */
    public boolean hasHandler(String actionTypeKey) {
        return handlers.containsKey(actionTypeKey);
    }

    /**
     * Returns the number of registered handlers.
     */
    public int size() {
        return handlers.size();
    }
}
