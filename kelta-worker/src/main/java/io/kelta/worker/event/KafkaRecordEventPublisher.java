package io.kelta.worker.event;

import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.runtime.event.RecordChangedPayload;
import io.kelta.runtime.events.RecordEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * NATS-based implementation of {@link RecordEventPublisher}.
 *
 * <p>Publishes {@link PlatformEvent}{@code <}{@link RecordChangedPayload}{@code >} instances
 * to NATS subjects under {@code kelta.record.changed.{tenantId}.{collectionName}}
 * for per-tenant, per-collection ordering. This ensures all events for a given
 * tenant + collection are processed in order by downstream consumers
 * (workflow engine, search index, real-time bridge, etc.).
 *
 * <p>Failures are handled gracefully by the underlying {@link PlatformEventPublisher}
 * — publishing errors are logged but do not cause the main CRUD operation to fail.
 *
 * @since 1.0.0
 */
@Component
public class KafkaRecordEventPublisher implements RecordEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaRecordEventPublisher.class);

    private static final String SUBJECT_PREFIX = "kelta.record.changed.";

    private final PlatformEventPublisher eventPublisher;

    public KafkaRecordEventPublisher(PlatformEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(PlatformEvent<RecordChangedPayload> event) {
        String subject = SUBJECT_PREFIX + event.getTenantId() + "." +
                event.getPayload().getCollectionName();
        logger.debug("Publishing {} event for record '{}' in collection '{}' (tenant '{}') to '{}'",
                event.getPayload().getChangeType(), event.getPayload().getRecordId(),
                event.getPayload().getCollectionName(), event.getTenantId(), subject);
        eventPublisher.publish(subject, event);
    }
}
