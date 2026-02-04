package com.emf.runtime.events;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a lifecycle event for a collection record.
 * 
 * <p>Events are published when records are created, updated, or deleted.
 * Each event contains:
 * <ul>
 *   <li>Event ID - unique identifier for the event</li>
 *   <li>Event type - CREATE, UPDATE, or DELETE</li>
 *   <li>Collection name - the collection the record belongs to</li>
 *   <li>Record ID - the ID of the affected record</li>
 *   <li>Data - the record data (null for DELETE events)</li>
 *   <li>Timestamp - when the event occurred</li>
 * </ul>
 * 
 * @param eventId unique identifier for this event
 * @param eventType the type of event (CREATE, UPDATE, DELETE)
 * @param collectionName the name of the collection
 * @param recordId the ID of the affected record
 * @param data the record data (may be null for DELETE events)
 * @param timestamp when the event occurred
 * 
 * @since 1.0.0
 */
public record CollectionEvent(
    String eventId,
    EventType eventType,
    String collectionName,
    String recordId,
    Map<String, Object> data,
    Instant timestamp
) {
    /**
     * Compact constructor with validation.
     */
    public CollectionEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(collectionName, "collectionName cannot be null");
        Objects.requireNonNull(recordId, "recordId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        // data can be null for DELETE events
        if (data != null) {
            data = Map.copyOf(data);
        }
    }
    
    /**
     * Creates a CREATE event.
     * 
     * @param collectionName the collection name
     * @param recordId the record ID
     * @param data the created record data
     * @return a new CREATE event
     */
    public static CollectionEvent create(String collectionName, String recordId, Map<String, Object> data) {
        return new CollectionEvent(
            UUID.randomUUID().toString(),
            EventType.CREATE,
            collectionName,
            recordId,
            data,
            Instant.now()
        );
    }
    
    /**
     * Creates an UPDATE event.
     * 
     * @param collectionName the collection name
     * @param recordId the record ID
     * @param data the updated record data
     * @return a new UPDATE event
     */
    public static CollectionEvent update(String collectionName, String recordId, Map<String, Object> data) {
        return new CollectionEvent(
            UUID.randomUUID().toString(),
            EventType.UPDATE,
            collectionName,
            recordId,
            data,
            Instant.now()
        );
    }
    
    /**
     * Creates a DELETE event.
     * 
     * @param collectionName the collection name
     * @param recordId the record ID
     * @return a new DELETE event
     */
    public static CollectionEvent delete(String collectionName, String recordId) {
        return new CollectionEvent(
            UUID.randomUUID().toString(),
            EventType.DELETE,
            collectionName,
            recordId,
            null,
            Instant.now()
        );
    }
    
    /**
     * Event types for collection lifecycle events.
     */
    public enum EventType {
        CREATE,
        UPDATE,
        DELETE
    }
}
