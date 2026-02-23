package com.emf.runtime.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
