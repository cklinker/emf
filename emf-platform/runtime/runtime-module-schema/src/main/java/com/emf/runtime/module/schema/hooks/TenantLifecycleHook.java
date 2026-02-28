package com.emf.runtime.module.schema.hooks;

import com.emf.runtime.workflow.BeforeSaveHook;
import com.emf.runtime.workflow.BeforeSaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lifecycle hook for the "tenants" system collection.
 *
 * <p>Enforces business rules for tenant CRUD operations:
 * <ul>
 *   <li>Before create: Validate slug format, normalize to lowercase, set default status</li>
 *   <li>After create: Create PostgreSQL schema for tenant isolation, log creation</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class TenantLifecycleHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(TenantLifecycleHook.class);

    private Consumer<String> schemaCreationCallback;

    /**
     * Sets a callback that is invoked after a tenant is created to create the
     * PostgreSQL schema for tenant-level database isolation.
     *
     * @param callback a consumer that receives the tenant slug and creates the schema
     */
    public void setSchemaCreationCallback(Consumer<String> callback) {
        this.schemaCreationCallback = callback;
    }

    @Override
    public String getCollectionName() {
        return "tenants";
    }

    @Override
    public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
        // Validate slug
        Object slugObj = record.get("slug");
        if (slugObj == null || slugObj.toString().isBlank()) {
            return BeforeSaveResult.error("slug", "Tenant slug is required");
        }

        String slug = slugObj.toString().trim().toLowerCase();

        // Validate slug format
        if (!slug.matches("^[a-z][a-z0-9-]*$")) {
            return BeforeSaveResult.error("slug",
                    "Tenant slug must start with a lowercase letter and contain only lowercase letters, numbers, and hyphens");
        }

        // Set defaults
        Map<String, Object> updates = new HashMap<>();

        if (!record.containsKey("status") || record.get("status") == null) {
            updates.put("status", "ACTIVE");
        }

        // Normalize slug to lowercase
        if (!slug.equals(record.get("slug"))) {
            updates.put("slug", slug);
        }

        if (updates.isEmpty()) {
            return BeforeSaveResult.ok();
        }

        return BeforeSaveResult.withFieldUpdates(updates);
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        String slug = (String) record.get("slug");
        log.info("Schema lifecycle: tenant '{}' created", slug);

        // Create PostgreSQL schema for tenant isolation
        if (schemaCreationCallback != null && slug != null) {
            try {
                schemaCreationCallback.accept(slug);
                log.info("Created PostgreSQL schema '{}' for new tenant", slug);
            } catch (Exception e) {
                log.error("Failed to create PostgreSQL schema '{}' for tenant: {}",
                        slug, e.getMessage(), e);
            }
        }
    }
}
