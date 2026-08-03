package io.kelta.worker.listener;

import io.kelta.runtime.event.BillingEntitlementChangedPayload;
import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.runtime.workflow.BeforeSaveHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * After-save hook for the {@code billing-plans} system collection.
 *
 * <p>Editing a plan changes what <em>every</em> member on it is entitled to, so
 * this publishes a tenant-wide invalidation rather than a per-member one: the
 * payload carries no {@code userId}, which
 * {@link BillingEntitlementCacheListener} reads as "evict this whole tenant".
 * Without the broadcast, pods would keep serving the old limits until their
 * cache TTL expired (Critical Rule 1).
 *
 * <p>Subject: {@code kelta.billing.entitlement.changed.<tenantId>._all}.
 */
public class BillingPlanRefreshHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(BillingPlanRefreshHook.class);

    static final String SUBJECT_PREFIX = "kelta.billing.entitlement.changed.";
    static final String EVENT_TYPE = "kelta.billing.entitlement.changed";
    /** Subject suffix standing in for "every member of this tenant". */
    static final String ALL_MEMBERS = "_all";

    private final PlatformEventPublisher eventPublisher;

    public BillingPlanRefreshHook(PlatformEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getCollectionName() {
        return "billing-plans";
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        publish(tenantId, str(record.get("code")), "PLAN_CREATED");
    }

    @Override
    public void afterUpdate(String id, Map<String, Object> record,
                            Map<String, Object> previous, String tenantId) {
        publish(tenantId, str(record.get("code")), "PLAN_UPDATED");
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        // The deleted record is not provided, so the plan code is unavailable —
        // but the tenant-wide eviction does not need it, and members who were on
        // the deleted plan MUST be re-resolved onto the DEFAULT plan.
        publish(tenantId, null, "PLAN_DELETED");
    }

    private void publish(String tenantId, String planCode, String reason) {
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Billing plan changed with no tenant context; skipping broadcast");
            return;
        }
        // userId == null is the signal for "every member of this tenant".
        BillingEntitlementChangedPayload payload =
                new BillingEntitlementChangedPayload(null, planCode, null, reason);
        PlatformEvent<BillingEntitlementChangedPayload> event =
                EventFactory.createEvent(EVENT_TYPE, payload);
        event.setTenantId(tenantId);
        String subject = SUBJECT_PREFIX + tenantId + "." + ALL_MEMBERS;
        log.info("Publishing tenant-wide entitlement invalidation for {} ({})", tenantId, reason);
        eventPublisher.publish(subject, event);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
