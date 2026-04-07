package io.kelta.runtime.event;

/**
 * Transport-agnostic interface for publishing platform events.
 *
 * <p>Implementations serialize the event and publish it to the configured
 * messaging infrastructure (e.g., NATS JetStream). The subject determines
 * routing and ordering — events on the same subject are delivered in order.
 *
 * <p>Implementations must handle failures gracefully — publishing failures
 * should be logged but must not cause the calling operation to fail.
 *
 * @since 1.0.0
 * @see PlatformEvent
 */
public interface PlatformEventPublisher {

    /**
     * Publishes a platform event to the given subject.
     *
     * @param subject the messaging subject (e.g., "kelta.record.changed.tenant1.contacts")
     * @param event   the platform event to publish
     */
    void publish(String subject, PlatformEvent<?> event);
}
