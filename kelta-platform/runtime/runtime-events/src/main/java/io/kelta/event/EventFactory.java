package io.kelta.runtime.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Factory for creating {@link PlatformEvent} instances with proper metadata.
 *
 * @since 1.0.0
 */
public class EventFactory {

    /**
     * Creates a PlatformEvent with the given type and payload.
     *
     * @param eventType     the event type (e.g., "kelta.config.collection.changed")
     * @param correlationId correlation ID for tracing (nullable)
     * @param payload       the event payload
     * @param <T>           the payload type
     * @return the configured event
     */
    public static <T> PlatformEvent<T> createEvent(String eventType, String correlationId, T payload) {
        return new PlatformEvent<>(
                UUID.randomUUID().toString(),
                eventType,
                null,
                correlationId,
                null,
                Instant.now(),
                payload
        );
    }

    /**
     * Creates a PlatformEvent with the given type and payload, without a correlation ID.
     *
     * @param eventType the event type
     * @param payload   the event payload
     * @param <T>       the payload type
     * @return the configured event
     */
    public static <T> PlatformEvent<T> createEvent(String eventType, T payload) {
        return createEvent(eventType, null, payload);
    }

    /**
     * Creates a PlatformEvent for a record change with tenant and user context.
     *
     * @param eventType the event type (e.g., "record.created")
     * @param tenantId  the tenant context
     * @param userId    the user who triggered the change (nullable)
     * @param payload   the record changed payload
     * @return the configured event
     */
    public static PlatformEvent<RecordChangedPayload> createRecordEvent(
            String eventType, String tenantId, String userId,
            RecordChangedPayload payload) {
        return new PlatformEvent<>(
                UUID.randomUUID().toString(),
                eventType,
                tenantId,
                null,
                userId,
                Instant.now(),
                payload
        );
    }
}
