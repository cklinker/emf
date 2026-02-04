package com.emf.runtime.model;

import java.util.List;
import java.util.Objects;

/**
 * Event publishing configuration for a collection.
 * 
 * <p>Controls Kafka event publishing for collection lifecycle events.
 * 
 * @param enabled Whether event publishing is enabled for this collection
 * @param topicPrefix Prefix for Kafka topic names (e.g., "emf.collections")
 * @param eventTypes List of event types to publish (e.g., "CREATED", "UPDATED", "DELETED")
 * 
 * @since 1.0.0
 */
public record EventsConfig(
    boolean enabled,
    String topicPrefix,
    List<String> eventTypes
) {
    /**
     * Default topic prefix for EMF collections.
     */
    public static final String DEFAULT_TOPIC_PREFIX = "emf.collections";
    
    /**
     * All supported event types.
     */
    public static final List<String> ALL_EVENT_TYPES = List.of("CREATED", "UPDATED", "DELETED");
    
    /**
     * Compact constructor with defensive copying.
     */
    public EventsConfig {
        eventTypes = eventTypes != null ? List.copyOf(eventTypes) : List.of();
    }
    
    /**
     * Creates an events configuration with event publishing disabled.
     * 
     * @return events configuration with publishing disabled
     */
    public static EventsConfig disabled() {
        return new EventsConfig(false, DEFAULT_TOPIC_PREFIX, List.of());
    }
    
    /**
     * Creates an events configuration with all event types enabled.
     * 
     * @param topicPrefix the topic prefix
     * @return events configuration with all events enabled
     */
    public static EventsConfig allEvents(String topicPrefix) {
        Objects.requireNonNull(topicPrefix, "topicPrefix cannot be null");
        return new EventsConfig(true, topicPrefix, ALL_EVENT_TYPES);
    }
    
    /**
     * Creates an events configuration with specific event types.
     * 
     * @param topicPrefix the topic prefix
     * @param eventTypes the event types to publish
     * @return events configuration with specified events
     */
    public static EventsConfig withEvents(String topicPrefix, List<String> eventTypes) {
        Objects.requireNonNull(topicPrefix, "topicPrefix cannot be null");
        Objects.requireNonNull(eventTypes, "eventTypes cannot be null");
        return new EventsConfig(true, topicPrefix, eventTypes);
    }
}
