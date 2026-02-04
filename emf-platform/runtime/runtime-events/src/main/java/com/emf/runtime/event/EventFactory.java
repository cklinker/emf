package com.emf.runtime.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Factory for creating ConfigEvent instances with proper metadata.
 * 
 * This helper class is used by services that publish events to Kafka.
 */
public class EventFactory {

    /**
     * Creates a ConfigEvent with the given type and payload.
     * Generates a unique event ID and includes the provided correlation ID.
     *
     * @param eventType The event type (e.g., "emf.config.collection.changed")
     * @param correlationId Correlation ID for tracing (can be null)
     * @param payload The event payload
     * @param <T> The payload type
     * @return The configured event
     */
    public static <T> ConfigEvent<T> createEvent(String eventType, String correlationId, T payload) {
        String eventId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        return new ConfigEvent<>(eventId, eventType, correlationId, timestamp, payload);
    }

    /**
     * Creates a ConfigEvent with the given type and payload, without a correlation ID.
     *
     * @param eventType The event type
     * @param payload The event payload
     * @param <T> The payload type
     * @return The configured event
     */
    public static <T> ConfigEvent<T> createEvent(String eventType, T payload) {
        return createEvent(eventType, null, payload);
    }
}
