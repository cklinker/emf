package com.emf.controlplane.lifecycle.handlers;

import com.emf.controlplane.lifecycle.BeforeSaveResult;
import com.emf.controlplane.lifecycle.SystemCollectionLifecycleHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Lifecycle handler for the "tenants" system collection.
 *
 * <p>Enforces business rules for tenant CRUD operations:
 * <ul>
 *   <li>Before create: Validate slug format, set defaults</li>
 *   <li>After create: Log tenant creation</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Component
public class TenantLifecycleHandler implements SystemCollectionLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(TenantLifecycleHandler.class);

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
        Map<String, Object> updates = new java.util.HashMap<>();

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
        log.info("System collection lifecycle: tenant '{}' created", record.get("slug"));
    }
}
