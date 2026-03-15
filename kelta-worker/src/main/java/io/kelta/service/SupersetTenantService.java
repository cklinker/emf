package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the lifecycle of Superset database connections per tenant.
 *
 * <p>Each tenant maps to a Superset database connection configured with
 * the tenant's schema on the search_path and RLS session variable
 * ({@code app.current_tenant_id}) set to the tenant UUID.
 *
 * @since 1.0.0
 */
public class SupersetTenantService {

    private static final Logger log = LoggerFactory.getLogger(SupersetTenantService.class);

    private static final String READER_PASSWORD = "superset_reader";

    private final SupersetApiClient apiClient;

    public SupersetTenantService(SupersetApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Ensures a Superset database connection exists for the given tenant.
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

            int dbId = apiClient.createDatabaseConnection(tenantId, tenantSlug, READER_PASSWORD);
            if (dbId > 0) {
                log.info("Created Superset database connection for tenant '{}' (dbId={})",
                        tenantSlug, dbId);
            } else {
                log.error("Failed to create Superset database connection for tenant '{}'", tenantSlug);
            }
        } catch (Exception e) {
            log.error("Failed to ensure Superset database connection for tenant '{}': {}",
                    tenantSlug, e.getMessage());
        }
    }

    /**
     * Deletes the Superset database connection for a tenant.
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
        } catch (Exception e) {
            log.error("Failed to delete Superset database connection for tenant '{}': {}",
                    tenantSlug, e.getMessage());
        }
    }
}
