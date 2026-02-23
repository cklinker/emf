package com.emf.controlplane.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for collection action handlers.
 *
 * <p>Auto-discovers all {@link CollectionActionHandler} beans at startup and
 * indexes them by "collection:action" key for fast lookup.
 *
 * @since 1.0.0
 */
@Component
public class CollectionActionRegistry {

    private static final Logger logger = LoggerFactory.getLogger(CollectionActionRegistry.class);

    private final Map<String, CollectionActionHandler> handlers;

    /**
     * Creates a new registry, indexing all provided handlers.
     *
     * @param handlerList the list of action handlers discovered by Spring
     */
    public CollectionActionRegistry(List<CollectionActionHandler> handlerList) {
        this.handlers = new HashMap<>();
        for (CollectionActionHandler handler : handlerList) {
            String key = buildKey(handler.getCollectionName(), handler.getActionName());
            handlers.put(key, handler);
            logger.info("Registered action handler: {} -> {}",
                    key, handler.getClass().getSimpleName());
        }
        logger.info("Registered {} action handlers", handlers.size());
    }

    /**
     * Finds an action handler by collection name and action name.
     *
     * @param collection the collection name
     * @param action the action name
     * @return the handler if found
     */
    public Optional<CollectionActionHandler> find(String collection, String action) {
        return Optional.ofNullable(handlers.get(buildKey(collection, action)));
    }

    private String buildKey(String collection, String action) {
        return collection + ":" + action;
    }
}
