package com.emf.controlplane.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for system collection lifecycle handlers.
 *
 * <p>Auto-discovers all {@link SystemCollectionLifecycleHandler} beans in the
 * Spring application context and registers them by collection name. Provides
 * thread-safe lookup for the {@link com.emf.controlplane.service.workflow.WorkflowEngine}
 * to invoke during before-save and after-save evaluation.
 *
 * @since 1.0.0
 */
@Component
public class SystemLifecycleHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(SystemLifecycleHandlerRegistry.class);

    private final Map<String, SystemCollectionLifecycleHandler> handlers;

    /**
     * Creates the registry and registers all discovered handlers.
     *
     * @param discoveredHandlers the list of lifecycle handlers discovered by Spring
     */
    public SystemLifecycleHandlerRegistry(List<SystemCollectionLifecycleHandler> discoveredHandlers) {
        this.handlers = new ConcurrentHashMap<>();

        if (discoveredHandlers != null) {
            for (SystemCollectionLifecycleHandler handler : discoveredHandlers) {
                String collectionName = handler.getCollectionName();
                SystemCollectionLifecycleHandler existing = handlers.put(collectionName, handler);
                if (existing != null) {
                    log.warn("Duplicate lifecycle handler for collection '{}': {} replaced by {}",
                            collectionName, existing.getClass().getSimpleName(),
                            handler.getClass().getSimpleName());
                }
                log.info("Registered lifecycle handler for system collection '{}': {}",
                        collectionName, handler.getClass().getSimpleName());
            }
        }

        log.info("System lifecycle handler registry initialized with {} handlers",
                handlers.size());
    }

    /**
     * Checks if a lifecycle handler is registered for the given collection.
     *
     * @param collectionName the collection name
     * @return true if a handler is registered
     */
    public boolean hasHandler(String collectionName) {
        return handlers.containsKey(collectionName);
    }

    /**
     * Gets the lifecycle handler for the given collection.
     *
     * @param collectionName the collection name
     * @return the handler, or empty if not registered
     */
    public Optional<SystemCollectionLifecycleHandler> getHandler(String collectionName) {
        return Optional.ofNullable(handlers.get(collectionName));
    }

    /**
     * Returns the names of all collections that have lifecycle handlers.
     *
     * @return set of collection names
     */
    public java.util.Set<String> getRegisteredCollections() {
        return java.util.Collections.unmodifiableSet(handlers.keySet());
    }

    /**
     * Returns the total number of registered handlers.
     *
     * @return handler count
     */
    public int getHandlerCount() {
        return handlers.size();
    }
}
