package io.kelta.worker.service;

import io.kelta.worker.repository.GovernorLimitsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the effective per-tenant quota map by merging tier defaults from
 * {@link TenantTierQuotas} with customer-specific overrides stored in
 * {@code tenant.limits}. Used by enforcement hooks ({@code BeforeSaveHook}s)
 * that need a single source of truth for "what's this tenant's cap?".
 *
 * <p>Fails open on lookup errors: a database glitch should not block writes.
 * The Governor Limits UI surfaces the same numbers and is the operator's
 * canonical view; the enforcement here is the gate that prevents an
 * over-limit tenant from creating new resources.
 */
@Service
public class TenantQuotaResolver {

    private static final Logger log = LoggerFactory.getLogger(TenantQuotaResolver.class);

    private final GovernorLimitsRepository repository;
    private final ObjectMapper objectMapper;

    public TenantQuotaResolver(GovernorLimitsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the merged tier-defaults + per-tenant overrides for the tenant.
     * Returns the PROFESSIONAL tier defaults if the tenant row is missing or
     * lookup throws.
     */
    public Map<String, Object> resolve(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return TenantTierQuotas.defaultsFor(TenantTierQuotas.Tier.PROFESSIONAL);
        }
        try {
            Optional<GovernorLimitsRepository.EditionAndLimits> row =
                    repository.findEditionAndLimits(tenantId);
            if (row.isEmpty()) {
                return TenantTierQuotas.defaultsFor(TenantTierQuotas.Tier.PROFESSIONAL);
            }
            TenantTierQuotas.Tier tier = TenantTierQuotas.Tier.fromEdition(row.get().edition());
            Map<String, Object> overrides = parseLimits(row.get().limits());
            return TenantTierQuotas.mergeOverrides(tier, overrides);
        } catch (Exception e) {
            log.warn("Failed to resolve quotas for tenant {}, falling back to PROFESSIONAL defaults: {}",
                    tenantId, e.getMessage());
            return TenantTierQuotas.defaultsFor(TenantTierQuotas.Tier.PROFESSIONAL);
        }
    }

    public int intQuota(String tenantId, String key) {
        Object value = resolve(tenantId).get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return Integer.MAX_VALUE;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseLimits(Object limitsObj) {
        if (limitsObj == null) {
            return Map.of();
        }
        if (limitsObj instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        String limitsStr = limitsObj instanceof String s ? s : limitsObj.toString();
        if (limitsStr == null || limitsStr.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(limitsStr,
                    objectMapper.getTypeFactory().constructMapType(
                            HashMap.class, String.class, Object.class));
        } catch (Exception e) {
            return Map.of();
        }
    }
}
