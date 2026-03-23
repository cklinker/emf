package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the lifecycle of Superset database connections per tenant.
 *
 * <p>Each tenant maps to a dedicated PostgreSQL user and a Superset database
 * connection. The PostgreSQL user has:
 * <ul>
 *   <li>Access restricted to {@code public} + tenant schema only</li>
 *   <li>A hardcoded {@code app.current_tenant_id} session variable for RLS</li>
 *   <li>A {@code search_path} scoped to the tenant schema + public</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class SupersetTenantService {

    private static final Logger log = LoggerFactory.getLogger(SupersetTenantService.class);

    private final SupersetApiClient apiClient;
    private final SupersetDatabaseUserService dbUserService;

    public SupersetTenantService(SupersetApiClient apiClient,
                                  SupersetDatabaseUserService dbUserService) {
        this.apiClient = apiClient;
        this.dbUserService = dbUserService;
    }

    /**
     * Ensures a per-tenant PostgreSQL user and Superset database connection exist.
     *
     * @param tenantId   the tenant UUID
     * @param tenantSlug the tenant slug (used as schema name and connection name)
     */
    public void ensureDatabaseConnection(String tenantId, String tenantSlug) {
        try {
            int existingId = apiClient.findDatabaseId(tenantSlug);
            if (existingId > 0) {
                log.info("Superset database connection already exists for tenant '{}' (dbId={})",
                        tenantSlug, existingId);
                return;
            }

            // 1. Create a dedicated PostgreSQL user for this tenant
            String password = dbUserService.ensureTenantUser(tenantId, tenantSlug);
            String username = SupersetDatabaseUserService.toUsername(tenantSlug);

            // 2. Create the Superset database connection using the tenant-specific user
            int dbId = apiClient.createDatabaseConnection(tenantId, tenantSlug, username, password);
            if (dbId > 0) {
                log.info("Created Superset database connection for tenant '{}' (dbId={}, pgUser={})",
                        tenantSlug, dbId, username);
            } else {
                log.error("Failed to create Superset database connection for tenant '{}'", tenantSlug);
            }
        } catch (Exception e) {
            log.error("Failed to ensure Superset database connection for tenant '{}': {}",
                    tenantSlug, e.getMessage());
        }
    }

    /**
     * Deletes the Superset database connection and PostgreSQL user for a tenant.
     *
     * @param tenantSlug the tenant slug
     */
    public void deleteDatabaseConnection(String tenantSlug) {
        try {
            int dbId = apiClient.findDatabaseId(tenantSlug);
            if (dbId > 0) {
                apiClient.deleteDatabaseConnection(dbId);
                log.info("Deleted Superset database connection for tenant '{}'", tenantSlug);
            }

            // Drop the per-tenant PostgreSQL user
            dbUserService.dropTenantUser(tenantSlug);
        } catch (Exception e) {
            log.error("Failed to delete Superset database connection for tenant '{}': {}",
                    tenantSlug, e.getMessage());
        }
    }
}
