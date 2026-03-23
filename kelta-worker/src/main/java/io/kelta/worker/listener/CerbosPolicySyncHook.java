package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.service.CerbosPolicySyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Before-save hook that triggers Cerbos policy sync when profiles change.
 *
 * <p>Listens for create/update/delete on the "profiles" collection and
 * re-syncs the tenant's Cerbos policies.
 */
public class CerbosPolicySyncHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(CerbosPolicySyncHook.class);

    private final CerbosPolicySyncService syncService;

    public CerbosPolicySyncHook(CerbosPolicySyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public String getCollectionName() {
        return "profiles";
    }

    @Override
    public int getOrder() {
        return 100; // Run after validation and audit hooks
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        log.info("Profile created — syncing Cerbos policies for tenant {}", tenantId);
        syncService.syncTenant(tenantId);
    }

    @Override
    public void afterUpdate(String id, Map<String, Object> record,
                             Map<String, Object> previous, String tenantId) {
        log.info("Profile {} updated — syncing Cerbos policies for tenant {}", id, tenantId);
        syncService.syncTenant(tenantId);
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        log.info("Profile {} deleted — syncing Cerbos policies for tenant {}", id, tenantId);
        syncService.syncTenant(tenantId);
    }
}
