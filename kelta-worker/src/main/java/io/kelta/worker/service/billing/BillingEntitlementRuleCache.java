package io.kelta.worker.service.billing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.repository.BillingEntitlementRule;
import io.kelta.worker.repository.BillingEntitlementRuleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Caches each tenant's active entitlement rules, keyed by collection.
 *
 * <p>This exists for one reason: the quota hook is a <b>wildcard</b>
 * {@code BeforeSaveHook}, so it runs on every record create in the system. Most
 * tenants have no rules at all, and most collections are not capped, so the
 * common path must cost nothing — a cache hit returning an empty list, no query.
 *
 * <p>Invalidated fleet-wide over NATS when a rule changes (Critical Rule 1); the
 * TTL is a backstop, not the mechanism.
 */
@Service
public class BillingEntitlementRuleCache {

    private final BillingEntitlementRuleRepository ruleRepository;
    private final Cache<String, Map<String, List<BillingEntitlementRule>>> cache;

    public BillingEntitlementRuleCache(
            BillingEntitlementRuleRepository ruleRepository,
            @Value("${kelta.billing.entitlement-rules.cache.ttl-seconds:300}") long ttlSeconds,
            @Value("${kelta.billing.entitlement-rules.cache.max-size:1000}") long maxSize) {
        this.ruleRepository = ruleRepository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(maxSize)
                .build();
    }

    /**
     * Active rules capping {@code collectionName} for this tenant; empty when the
     * collection is uncapped, which is the overwhelmingly common case.
     */
    public List<BillingEntitlementRule> forCollection(String tenantId, String collectionName) {
        if (tenantId == null || tenantId.isBlank() || collectionName == null) {
            return List.of();
        }
        return cache.get(tenantId, this::load).getOrDefault(collectionName, List.of());
    }

    private Map<String, List<BillingEntitlementRule>> load(String tenantId) {
        // The hook runs inside a request that already has a tenant bound, but bind
        // explicitly so a flow or scheduler path is RLS-scoped too.
        return TenantContext.callWithTenant(tenantId, () ->
                ruleRepository.findActive(tenantId).stream()
                        .collect(Collectors.groupingBy(BillingEntitlementRule::collectionName)));
    }

    /** Drops one tenant's cached rules on this pod. */
    public void invalidate(String tenantId) {
        if (tenantId != null) {
            cache.invalidate(tenantId);
        }
    }
}
