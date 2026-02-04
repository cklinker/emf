package com.emf.runtime.events;

import com.emf.runtime.model.CollectionDefinition;

import java.util.Map;

/**
 * Interface for publishing collection lifecycle events.
 * 
 * <p>Implementations publish events when records are created, updated, or deleted.
 * Events are published to a messaging system (e.g., Kafka) for consumption by
 * other services.
 * 
 * <p>Event publishing is conditional on the collection's events configuration.
 * If events are disabled for a collection, no events are published.
 * 
 * <p>Implementations must handle failures gracefully - event publishing failures
 * should not cause the main operation to fail.
 * 
 * @since 1.0.0
 */
public interface EventPublisher {
    
    /**
     * Publishes a CREATE event for a new record.
     * 
     * @param definition the collection definition
     * @param data the created record data
     */
    void publishCreate(CollectionDefinition definition, Map<String, Object> data);
    
    /**
     * Publishes an UPDATE event for a modified record.
     * 
     * @param definition the collection definition
     * @param data the updated record data
     */
    void publishUpdate(CollectionDefinition definition, Map<String, Object> data);
    
    /**
     * Publishes a DELETE event for a removed record.
     * 
     * @param definition the collection definition
     * @param recordId the ID of the deleted record
     */
    void publishDelete(CollectionDefinition definition, String recordId);
    
    /**
     * Publishes a generic collection event.
     * 
     * @param definition the collection definition
     * @param event the event to publish
     */
    void publish(CollectionDefinition definition, CollectionEvent event);
}
