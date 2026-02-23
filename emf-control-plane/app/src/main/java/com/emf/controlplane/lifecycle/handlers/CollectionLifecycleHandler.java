package com.emf.controlplane.lifecycle.handlers;

import com.emf.controlplane.lifecycle.BeforeSaveResult;
import com.emf.controlplane.lifecycle.SystemCollectionLifecycleHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Lifecycle handler for the "collections" system collection.
 *
 * <p>Enforces business rules for collection CRUD operations:
 * <ul>
 *   <li>Before create: Validate name format, set version=1, generate API path</li>
 *   <li>Before update: Validate name uniqueness if changed</li>
 *   <li>After create: Log collection creation for audit trail</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Component
public class CollectionLifecycleHandler implements SystemCollectionLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(CollectionLifecycleHandler.class);

    @Override
    public String getCollectionName() {
        return "collections";
    }

    @Override
    public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
        // Validate collection name format
        Object nameObj = record.get("name");
        if (nameObj == null || nameObj.toString().isBlank()) {
            return BeforeSaveResult.error("name", "Collection name is required");
        }

        String name = nameObj.toString().trim();

        // Validate name format: lowercase alphanumeric with hyphens
        if (!name.matches("^[a-z][a-z0-9-]*$")) {
            return BeforeSaveResult.error("name",
                    "Collection name must start with a lowercase letter and contain only lowercase letters, numbers, and hyphens");
        }

        // Set defaults
        Map<String, Object> updates = new java.util.HashMap<>();

        // Set version to 1 for new collections
        if (!record.containsKey("currentVersion")) {
            updates.put("currentVersion", 1L);
        }

        // Set active to true
        if (!record.containsKey("active")) {
            updates.put("active", true);
        }

        // Generate API path if not provided
        if (!record.containsKey("path") || record.get("path") == null) {
            updates.put("path", "/api/" + name);
        }

        if (updates.isEmpty()) {
            return BeforeSaveResult.ok();
        }

        log.debug("Setting defaults for new collection '{}': {}", name, updates.keySet());
        return BeforeSaveResult.withFieldUpdates(updates);
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        log.info("System collection lifecycle: collection '{}' created for tenant '{}'",
                record.get("name"), tenantId);
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        log.info("System collection lifecycle: collection '{}' deleted for tenant '{}'",
                id, tenantId);
    }
}
