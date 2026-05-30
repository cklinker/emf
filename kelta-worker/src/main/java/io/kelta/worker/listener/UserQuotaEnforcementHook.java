package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.repository.GovernorLimitsRepository;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Rejects {@code users} create when the tenant is at or above its
 * {@code maxUsers} quota. Mirrors {@link CollectionQuotaEnforcementHook}.
 */
public class UserQuotaEnforcementHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(UserQuotaEnforcementHook.class);

    private final TenantQuotaResolver quotaResolver;
    private final GovernorLimitsRepository repository;

    public UserQuotaEnforcementHook(TenantQuotaResolver quotaResolver,
                                    GovernorLimitsRepository repository) {
        this.quotaResolver = quotaResolver;
        this.repository = repository;
    }

    @Override
    public String getCollectionName() {
        return "users";
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return BeforeSaveResult.ok();
        }
        int limit = quotaResolver.intQuota(tenantId, TenantTierQuotas.KEY_MAX_USERS);
        int active = repository.countActiveUsers(tenantId);
        if (active >= limit) {
            log.info("Rejecting user create for tenant {} — at quota ({}/{})", tenantId, active, limit);
            return BeforeSaveResult.error(null,
                    "Tenant user quota exceeded (" + active + "/" + limit + "). "
                            + "Upgrade the tenant tier or raise tenant.limits.maxUsers.");
        }
        return BeforeSaveResult.ok();
    }
}
