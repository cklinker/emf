package io.kelta.worker.listener;

import io.kelta.worker.service.billing.BillingEntitlementRuleCache;
import io.kelta.worker.service.billing.EntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Subscribes to {@code kelta.billing.entitlement.changed.>} (broadcast) and drops
 * the affected member's cached entitlements, so every pod resolves fresh values
 * after a subscription, pass, or plan change anywhere in the fleet.
 *
 * <p>Subject is {@code kelta.billing.entitlement.changed.<tenantId>.<userId>}; the
 * tenant comes from the {@link io.kelta.runtime.event.PlatformEvent} envelope and
 * the member from the payload. A payload with <b>no</b> {@code userId} means
 * "everyone in this tenant" — what a plan edit publishes, since it changes what
 * every member on that plan is entitled to.
 */
@Component
public class BillingEntitlementCacheListener {

    private static final Logger log = LoggerFactory.getLogger(BillingEntitlementCacheListener.class);

    private final EntitlementService entitlementService;
    private final BillingEntitlementRuleCache ruleCache;
    private final ObjectMapper objectMapper;

    public BillingEntitlementCacheListener(EntitlementService entitlementService,
                                           BillingEntitlementRuleCache ruleCache,
                                           ObjectMapper objectMapper) {
        this.entitlementService = entitlementService;
        this.ruleCache = ruleCache;
        this.objectMapper = objectMapper;
    }

    public void handleEntitlementChanged(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);

            String tenantId = textOrNull(root.get("tenantId"));
            JsonNode payload = root.get("payload");
            if (tenantId == null && payload != null && !payload.isNull()) {
                tenantId = textOrNull(payload.get("tenantId"));
            }
            if (tenantId == null) {
                log.warn("Skipping entitlement invalidation: missing tenantId in event");
                return;
            }

            String userId = payload == null || payload.isNull()
                    ? null
                    : textOrNull(payload.get("userId"));
            String reason = payload == null || payload.isNull()
                    ? null
                    : textOrNull(payload.get("reason"));

            if (BillingEntitlementRuleRefreshHook.REASON_RULES_CHANGED.equals(reason)) {
                // A rule change alters what the quota hook enforces, not what any
                // member is entitled to — evict the rules, leave entitlements warm.
                log.info("Billing entitlement rules changed for {} — evicting rule cache", tenantId);
                ruleCache.invalidate(tenantId);
                return;
            }

            if (userId == null) {
                log.info("Billing entitlements changed tenant-wide for {} — evicting all", tenantId);
                entitlementService.invalidateTenant(tenantId);
            } else {
                log.debug("Billing entitlements changed for member {} of tenant {} — evicting",
                        userId, tenantId);
                entitlementService.invalidate(tenantId, userId);
            }
        } catch (Exception e) {
            log.error("Failed to process billing entitlement changed event: {}", e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.stringValue();
        return value.isBlank() ? null : value;
    }
}
