package io.kelta.runtime.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Unified event envelope for all Kelta platform events.
 *
 * <p>Wraps a type-safe payload with standard metadata fields (tenant, user, tracing).
 * Used for both record-level data changes and configuration change events.
 *
 * <p>Payload types:
 * <ul>
 *   <li>{@link RecordChangedPayload} — record CRUD events</li>
 *   <li>{@link CollectionChangedPayload} — collection schema changes</li>
 *   <li>{@link ModuleChangedPayload} — module lifecycle changes</li>
 * </ul>
 *
 * @param <T> the payload type
 * @since 1.0.0
 */
public class PlatformEvent<T> {

    private String eventId;
    private String eventType;
    private String tenantId;
    private String correlationId;
    private String userId;
    private Instant timestamp;
    private T payload;

    /**
     * Default constructor for deserialization.
     */
    public PlatformEvent() {
    }

    /**
     * Creates a new PlatformEvent with all fields.
     *
     * @param eventId       unique identifier for this event
     * @param eventType     type of the event (e.g., "record.created", "collection.changed")
     * @param tenantId      tenant context (null for global events)
     * @param correlationId correlation ID for request tracing (nullable)
     * @param userId        user who triggered this event (null for system events)
     * @param timestamp     when the event occurred
     * @param payload       the event payload
     */
    public PlatformEvent(String eventId, String eventType, String tenantId,
                          String correlationId, String userId,
                          Instant timestamp, T payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.correlationId = correlationId;
        this.userId = userId;
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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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
        PlatformEvent<?> that = (PlatformEvent<?>) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "PlatformEvent{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", userId='" + userId + '\'' +
                ", timestamp=" + timestamp +
                ", payload=" + payload +
                '}';
    }
}
