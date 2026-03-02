package com.emf.runtime.events;

import com.emf.runtime.event.PlatformEvent;
import com.emf.runtime.event.RecordChangedPayload;

/**
 * Interface for publishing record-level change events.
 *
 * <p>Implementations publish {@link PlatformEvent} instances wrapping a
 * {@link RecordChangedPayload} when records are created, updated, or deleted.
 * These events are consumed by the workflow engine and other downstream
 * services (audit, notifications, etc.).
 *
 * <p>This publisher fires for every record mutation unconditionally — workflow
 * triggers and audit trails must always receive events.
 *
 * <p>This is an optional dependency in {@code DefaultQueryEngine}. When not wired
 * (null), no record change events are published.
 *
 * <p>Implementations must handle failures gracefully — event publishing failures
 * should be logged but must not cause the main CRUD operation to fail.
 *
 * @since 1.0.0
 * @see PlatformEvent
 * @see RecordChangedPayload
 */
public interface RecordEventPublisher {

    /**
     * Publishes a record change event.
     *
     * @param event the platform event wrapping a record changed payload
     */
    void publish(PlatformEvent<RecordChangedPayload> event);
}
