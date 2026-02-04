package com.emf.runtime.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Base event class for configuration change events published to Kafka.
 * Contains common fields for all configuration events.
 * 
 * This is a shared event class used across all EMF services (control-plane, gateway, domain services).
 */
public class ConfigEvent<T> {

    private String eventId;
    private String eventType;
    private String correlationId;
    private Instant timestamp;
    private T payload;

    /**
     * Default constructor for deserialization.
     */
    public ConfigEvent() {
    }

    /**
     * Creates a new ConfigEvent with all fields.
     *
     * @param eventId Unique identifier for this event
     * @param eventType Type of the event (e.g., "emf.config.collection.changed")
     * @param correlationId Correlation ID for tracing
     * @param timestamp When the event occurred
     * @param payload The event payload containing the changed entity
     */
    public ConfigEvent(String eventId, String eventType, String correlationId, Instant timestamp, T payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.payload = payload;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigEvent<?> that = (ConfigEvent<?>) o;
        return Objects.equals(eventId, that.eventId) &&
                Objects.equals(eventType, that.eventType) &&
                Objects.equals(correlationId, that.correlationId) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, eventType, correlationId, timestamp, payload);
    }

    @Override
    public String toString() {
        return "ConfigEvent{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", timestamp=" + timestamp +
                ", payload=" + payload +
                '}';
    }
}
