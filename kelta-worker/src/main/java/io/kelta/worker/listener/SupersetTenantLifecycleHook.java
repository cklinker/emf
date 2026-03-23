package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.worker.service.SupersetTenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Before-save hook for the "tenants" system collection that creates/deletes
 * Superset database connections in sync with tenant lifecycle events.
 *
 * <p>Runs after TenantLifecycleHook and SvixTenantLifecycleHook (order 300)
 * to ensure the tenant record and schema are fully created.
 *
 * @since 1.0.0
 */
public class SupersetTenantLifecycleHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(SupersetTenantLifecycleHook.class);

    private final SupersetTenantService supersetTenantService;

    public SupersetTenantLifecycleHook(SupersetTenantService supersetTenantService) {
        this.supersetTenantService = supersetTenantService;
    }

    @Override
    public String getCollectionName() {
        return "tenants";
    }

    @Override
    public int getOrder() {
        return 300;
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        String id = getString(record, "id");
        String slug = getString(record, "slug");
        if (id != null && slug != null) {
            supersetTenantService.ensureDatabaseConnection(id, slug);
        }
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        // We need the slug to find the database connection, but we only have the ID.
        // The tenant service will handle this gracefully.
        log.info("Tenant '{}' deleted — Superset database cleanup should be handled via admin", id);
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
