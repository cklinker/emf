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
 * After-save hook for the {@code billing-entitlement-rules} system collection.
 *
 * <p>A rule change alters what {@link MemberEntitlementQuotaHook} enforces for
 * every member of the tenant, so this publishes a tenant-wide invalidation — the
 * payload carries no {@code userId}, which
 * {@link BillingEntitlementCacheListener} reads as "evict this whole tenant".
 * Without the broadcast, other pods would keep enforcing the old rules until
 * their cache TTL expired (Critical Rule 1).
 *
 * <p>Reason {@code RULES_CHANGED} is what tells the listener to drop the rule
 * cache as well as the entitlement cache.
 *
 * <p>Subject: {@code kelta.billing.entitlement.changed.<tenantId>._all}.
 */
public class BillingEntitlementRuleRefreshHook implements BeforeSaveHook {

    private static final Logger log =
            LoggerFactory.getLogger(BillingEntitlementRuleRefreshHook.class);

    static final String SUBJECT_PREFIX = "kelta.billing.entitlement.changed.";
    static final String EVENT_TYPE = "kelta.billing.entitlement.changed";
    static final String ALL_MEMBERS = "_all";
    /** Reason marker the cache listener keys off to also evict the rule cache. */
    public static final String REASON_RULES_CHANGED = "RULES_CHANGED";

    private final PlatformEventPublisher eventPublisher;

    public BillingEntitlementRuleRefreshHook(PlatformEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getCollectionName() {
        return "billing-entitlement-rules";
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        publish(tenantId);
    }

    @Override
    public void afterUpdate(String id, Map<String, Object> record,
                            Map<String, Object> previous, String tenantId) {
        publish(tenantId);
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        // The deleted row is not provided, but a tenant-wide eviction needs
        // nothing from it — and a removed cap MUST stop being enforced promptly.
        publish(tenantId);
    }

    private void publish(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Entitlement rule changed with no tenant context; skipping broadcast");
            return;
        }
        BillingEntitlementChangedPayload payload =
                new BillingEntitlementChangedPayload(null, null, null, REASON_RULES_CHANGED);
        PlatformEvent<BillingEntitlementChangedPayload> event =
                EventFactory.createEvent(EVENT_TYPE, payload);
        event.setTenantId(tenantId);
        log.info("Publishing tenant-wide entitlement-rule invalidation for {}", tenantId);
        eventPublisher.publish(SUBJECT_PREFIX + tenantId + "." + ALL_MEMBERS, event);
    }
}
