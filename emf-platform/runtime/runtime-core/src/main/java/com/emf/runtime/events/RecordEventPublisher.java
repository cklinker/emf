package com.emf.runtime.events;

import com.emf.runtime.event.RecordChangeEvent;

/**
 * Interface for publishing record-level change events.
 *
 * <p>Implementations publish {@link RecordChangeEvent} instances when records are
 * created, updated, or deleted. These events are consumed by the workflow engine
 * and other downstream services (audit, notifications, etc.).
 *
 * <p>Unlike {@link EventPublisher} which publishes per-collection lifecycle events
 * gated by collection-level event configuration, this publisher fires for every
 * record mutation unconditionally — workflow triggers and audit trails must always
 * receive events regardless of the collection's {@code eventsConfig}.
 *
 * <p>This is an optional dependency in {@code DefaultQueryEngine}. When not wired
 * (null), no record change events are published.
 *
 * <p>Implementations must handle failures gracefully — event publishing failures
 * should be logged but must not cause the main CRUD operation to fail.
 *
 * @since 1.0.0
 * @see RecordChangeEvent
 */
public interface RecordEventPublisher {

    /**
     * Publishes a record change event.
     *
     * @param event the record change event to publish
     */
    void publish(RecordChangeEvent event);
}
