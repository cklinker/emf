package io.kelta.worker.service;

import com.svix.Svix;
import com.svix.models.ApplicationIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the lifecycle of Svix applications per tenant.
 *
 * <p>Each tenant maps to a Svix application (using the tenant ID as the uid),
 * providing full isolation of webhook endpoints, messages, and delivery logs.
 *
 * @since 1.0.0
 */
public class SvixTenantService {

    private static final Logger log = LoggerFactory.getLogger(SvixTenantService.class);

    private final Svix svix;

    public SvixTenantService(Svix svix) {
        this.svix = svix;
    }

    /**
     * Ensures a Svix application exists for the given tenant.
     * Creates one if it doesn't exist, or updates the name if it does.
     *
     * @param tenantId   the tenant UUID
     * @param tenantName the tenant display name
     */
    public void ensureApplication(String tenantId, String tenantName) {
        try {
            var appIn = new ApplicationIn();
            appIn.setName(tenantName != null ? tenantName : tenantId);
            appIn.setUid(tenantId);
            svix.getApplication().getOrCreate(appIn);
            log.info("Ensured Svix application for tenant '{}' (id={})", tenantName, tenantId);
        } catch (Exception e) {
            log.error("Failed to create Svix application for tenant '{}' (id={}): {}",
                    tenantName, tenantId, e.getMessage());
        }
    }

    /**
     * Deletes the Svix application for a tenant.
     *
     * @param tenantId the tenant UUID
     */
    public void deleteApplication(String tenantId) {
        try {
            svix.getApplication().delete(tenantId);
            log.info("Deleted Svix application for tenant '{}'", tenantId);
        } catch (Exception e) {
            log.error("Failed to delete Svix application for tenant '{}': {}",
                    tenantId, e.getMessage());
        }
    }
}
