package io.kelta.worker.listener;

import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.DomainChangedPayload;
import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Broadcasts {@code kelta.config.domain.changed.<domainId>} after a custom
 * tenant domain is registered or removed.
 *
 * <p>Tenant custom domains live in the raw {@code tenant_custom_domain} table
 * rather than a system collection, so this publisher is invoked directly from
 * {@code TenantDomainController} after the JDBC mutation. The event is
 * consumed by {@code CustomDomainCacheInvalidationListener} on every worker
 * pod and by the gateway's {@code DomainCacheInvalidationListener} so the
 * domain &rarr; tenant slug cache is evicted across the fleet instead of
 * waiting for the 10-minute TTL.
 *
 * <p>The {@code domain} field is included in the payload so listeners can
 * evict a single cache entry; if it's null they fall back to draining the
 * entire cache.
 */
@Component
public class CustomDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CustomDomainEventPublisher.class);
    static final String SUBJECT_PREFIX = "kelta.config.domain.changed.";
    private static final String EVENT_TYPE = "kelta.config.domain.changed";

    private final PlatformEventPublisher eventPublisher;

    public CustomDomainEventPublisher(PlatformEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishCreated(String id, String domain, String tenantId) {
        publish(id, domain, tenantId, ChangeType.CREATED);
    }

    public void publishDeleted(String id, String domain, String tenantId) {
        publish(id, domain, tenantId, ChangeType.DELETED);
    }

    private void publish(String id, String domain, String tenantId, ChangeType changeType) {
        if (id == null || id.isBlank()) {
            log.warn("Skipping domain changed event: id is blank");
            return;
        }
        DomainChangedPayload payload = new DomainChangedPayload(id, domain, changeType);
        PlatformEvent<DomainChangedPayload> event =
                EventFactory.createEvent(EVENT_TYPE, payload);
        event.setTenantId(tenantId);
        String subject = SUBJECT_PREFIX + id;
        log.info("Publishing domain {} event for '{}' (id={}) to '{}'",
                changeType, domain, id, subject);
        eventPublisher.publish(subject, event);
    }
}
