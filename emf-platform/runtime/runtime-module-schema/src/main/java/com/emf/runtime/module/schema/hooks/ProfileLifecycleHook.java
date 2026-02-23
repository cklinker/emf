package com.emf.runtime.module.schema.hooks;

import com.emf.runtime.workflow.BeforeSaveHook;
import com.emf.runtime.workflow.BeforeSaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Lifecycle hook for the "profiles" system collection.
 *
 * <p>Enforces business rules for profile CRUD operations:
 * <ul>
 *   <li>Before create: Validate name is present, set default system=false</li>
 *   <li>After create/update: Log for security audit</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class ProfileLifecycleHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(ProfileLifecycleHook.class);

    @Override
    public String getCollectionName() {
        return "profiles";
    }

    @Override
    public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
        // Validate profile name
        Object nameObj = record.get("name");
        if (nameObj == null || nameObj.toString().isBlank()) {
            return BeforeSaveResult.error("name", "Profile name is required");
        }

        // Set default for system flag
        Map<String, Object> updates = new HashMap<>();
        if (!record.containsKey("system")) {
            updates.put("system", false);
        }

        if (updates.isEmpty()) {
            return BeforeSaveResult.ok();
        }

        return BeforeSaveResult.withFieldUpdates(updates);
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        log.info("Schema lifecycle: profile '{}' created for tenant '{}'",
                record.get("name"), tenantId);
    }

    @Override
    public void afterUpdate(String id, Map<String, Object> record,
                             Map<String, Object> previous, String tenantId) {
        log.info("Schema lifecycle: profile '{}' updated for tenant '{}'", id, tenantId);
    }
}
