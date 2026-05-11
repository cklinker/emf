package io.kelta.worker.listener;

import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.FeatureChangedPayload;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Broadcasts {@code kelta.config.feature.changed.<tenantId>} when tenant
 * governor limits or feature toggles are updated.
 *
 * <p>Settings like governor limits live on the {@code tenant} row rather than
 * a system collection, so this publisher is invoked directly from
 * {@code GovernorLimitsController} after the JDBC update. The event is
 * consumed by {@code SystemFeatureCacheInvalidationListener} on every worker
 * pod and the gateway's {@code FeatureCacheInvalidationListener} so derived
 * caches (tenant limits, feature gates) are evicted instead of waiting for
 * the 10-minute Caffeine TTL.
 *
 * <p>The subject is keyed by tenant rather than feature id because the
 * downstream caches are tenant-scoped — every change for a tenant should
 * invalidate the same cache entry.
 */
@Component
public class SystemFeatureEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SystemFeatureEventPublisher.class);
    static final String SUBJECT_PREFIX = "kelta.config.feature.changed.";
    private static final String EVENT_TYPE = "kelta.config.feature.changed";

    private final PlatformEventPublisher eventPublisher;

    public SystemFeatureEventPublisher(PlatformEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishUpdated(String tenantId, String scope) {
        publish(tenantId, scope, ChangeType.UPDATED);
    }

    public void publish(String tenantId, String scope, ChangeType changeType) {
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Skipping feature changed event: tenantId is blank");
            return;
        }
        FeatureChangedPayload payload = new FeatureChangedPayload(tenantId, scope, changeType);
        PlatformEvent<FeatureChangedPayload> event =
                EventFactory.createEvent(EVENT_TYPE, payload);
        event.setTenantId(tenantId);
        String subject = SUBJECT_PREFIX + tenantId;
        log.info("Publishing feature {} event for tenant {} (scope={}) to '{}'",
                changeType, tenantId, scope, subject);
        eventPublisher.publish(subject, event);
    }
}
