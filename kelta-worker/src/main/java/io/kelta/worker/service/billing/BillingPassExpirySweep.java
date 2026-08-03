package io.kelta.worker.service.billing;

import io.kelta.runtime.event.BillingEntitlementChangedPayload;
import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.worker.repository.BillingPassRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Flips due one-time passes from ACTIVE to EXPIRED and tells every pod to drop
 * the affected members' cached entitlements.
 *
 * <p><b>This sweep is a tidier, not a gate.</b> {@link EntitlementService}
 * already ignores an expired pass at read time regardless of its stored status,
 * so a member is never over-entitled by a sweep that is late, paused, or failing.
 * What the sweep buys is an accurate stored status for admin screens and reports,
 * and a prompt cache eviction instead of waiting out the TTL.
 *
 * <p>Not destructive: it only advances a status. No dry-run gate, unlike the
 * retention purge.
 *
 * <p>Multi-pod safe — the repository claims rows with {@code FOR UPDATE SKIP
 * LOCKED} inside an {@code UPDATE … RETURNING}, so concurrent pods take disjoint
 * slices and no member is announced twice.
 */
@Service
public class BillingPassExpirySweep {

    private static final Logger log = LoggerFactory.getLogger(BillingPassExpirySweep.class);

    static final String SUBJECT_PREFIX = "kelta.billing.entitlement.changed.";
    static final String EVENT_TYPE = "kelta.billing.entitlement.changed";
    static final String TRIGGER_SUBJECT_PREFIX = "kelta.trigger.";
    static final String TRIGGER_TOPIC = "billing.subscription";

    private final BillingPassRepository passRepository;
    private final EntitlementService entitlementService;
    private final PlatformEventPublisher eventPublisher;
    private final boolean enabled;
    private final int batchSize;

    public BillingPassExpirySweep(
            BillingPassRepository passRepository,
            EntitlementService entitlementService,
            PlatformEventPublisher eventPublisher,
            @Value("${kelta.billing.pass-expiry.enabled:true}") boolean enabled,
            @Value("${kelta.billing.pass-expiry.batch-size:200}") int batchSize) {
        this.passRepository = passRepository;
        this.entitlementService = entitlementService;
        this.eventPublisher = eventPublisher;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${kelta.billing.pass-expiry.poll-interval-ms:60000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        try {
            expireDuePasses();
        } catch (Exception e) {
            log.error("Billing pass expiry sweep failed: {}", e.getMessage(), e);
        }
    }

    /** Package-private so tests can drive one pass without the scheduler. */
    void expireDuePasses() {
        List<Map<String, Object>> expired = passRepository.expireDue(batchSize);
        if (expired.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : expired) {
            String tenantId = str(row.get("tenantId"));
            String userId = str(row.get("userId"));
            if (tenantId == null || userId == null) {
                continue;
            }
            // Same-pod read-after-write, in addition to (never instead of) the
            // broadcast every other pod consumes.
            entitlementService.invalidate(tenantId, userId);
            publish(tenantId, userId);
        }
        log.info("Billing pass expiry sweep: expired {} pass(es)", expired.size());
    }

    private void publish(String tenantId, String userId) {
        BillingEntitlementChangedPayload payload =
                new BillingEntitlementChangedPayload(userId, null, "EXPIRED", "PASS_EXPIRED");
        PlatformEvent<BillingEntitlementChangedPayload> event =
                EventFactory.createEvent(EVENT_TYPE, payload);
        event.setTenantId(tenantId);
        eventPublisher.publish(SUBJECT_PREFIX + tenantId + "." + userId, event);
        // Tenants build "your pass has ended" automations off this.
        eventPublisher.publish(TRIGGER_SUBJECT_PREFIX + tenantId + "." + TRIGGER_TOPIC, event);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
