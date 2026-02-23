package com.emf.controlplane.service.workflow;

import com.emf.controlplane.entity.WorkflowActionType;
import com.emf.controlplane.repository.WorkflowActionTypeRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that discovers and manages {@link ActionHandler} implementations.
 * <p>
 * On startup, scans the Spring context for all beans implementing {@link ActionHandler},
 * builds a map of {@code actionTypeKey -> ActionHandler}, and cross-references against
 * the {@code workflow_action_type} database table to warn about unregistered handlers.
 * <p>
 * Thread-safe: uses ConcurrentHashMap for the handler registry.
 */
@Component
public class ActionHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActionHandlerRegistry.class);

    private final Map<String, ActionHandler> handlers = new ConcurrentHashMap<>();
    private final List<ActionHandler> discoveredHandlers;
    private final WorkflowActionTypeRepository actionTypeRepository;

    public ActionHandlerRegistry(List<ActionHandler> discoveredHandlers,
                                  WorkflowActionTypeRepository actionTypeRepository) {
        this.discoveredHandlers = discoveredHandlers != null ? discoveredHandlers : List.of();
        this.actionTypeRepository = actionTypeRepository;
    }

    @PostConstruct
    void initialize() {
        log.info("Initializing ActionHandlerRegistry with {} discovered handlers", discoveredHandlers.size());

        // Register all discovered handlers
        for (ActionHandler handler : discoveredHandlers) {
            String key = handler.getActionTypeKey();
            ActionHandler existing = handlers.put(key, handler);
            if (existing != null) {
                log.warn("Duplicate ActionHandler for key '{}': {} replaced by {}",
                    key, existing.getClass().getSimpleName(), handler.getClass().getSimpleName());
            }
            log.info("Registered ActionHandler: {} -> {}", key, handler.getClass().getSimpleName());
        }

        // Cross-reference against database to warn about unregistered handlers
        try {
            List<WorkflowActionType> registeredTypes = actionTypeRepository.findByActiveTrue();
            for (WorkflowActionType type : registeredTypes) {
                if (!handlers.containsKey(type.getKey())) {
                    log.warn("Action type '{}' ({}) is registered in database but no handler found on classpath. " +
                             "Expected handler class: {}", type.getKey(), type.getName(), type.getHandlerClass());
                }
            }

            // Log handlers that exist but aren't in the DB
            for (String key : handlers.keySet()) {
                boolean inDb = registeredTypes.stream().anyMatch(t -> t.getKey().equals(key));
                if (!inDb) {
                    log.warn("ActionHandler '{}' found on classpath but not registered in workflow_action_type table",
                        key);
                }
            }
        } catch (Exception e) {
            log.warn("Could not cross-reference handlers with database (database may not be ready): {}",
                e.getMessage());
        }

        log.info("ActionHandlerRegistry initialized with {} handlers: {}",
            handlers.size(), handlers.keySet());
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

    /**
     * Refreshes the handler registry. Called when action type configuration changes
     * are received via Kafka events.
     */
    public void refresh() {
        log.info("Refreshing ActionHandlerRegistry");
        initialize();
    }
}
