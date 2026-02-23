package com.emf.controlplane.lifecycle.handlers;

import com.emf.controlplane.lifecycle.BeforeSaveResult;
import com.emf.controlplane.lifecycle.SystemCollectionLifecycleHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Lifecycle handler for the "users" system collection.
 *
 * <p>Enforces business rules for user CRUD operations:
 * <ul>
 *   <li>Before create: Validate email format, set defaults (locale, timezone, status)</li>
 *   <li>Before update: Validate email format if changed</li>
 *   <li>After create: Log user creation for security audit</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Component
public class UserLifecycleHandler implements SystemCollectionLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(UserLifecycleHandler.class);

    private static final String EMAIL_PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

    @Override
    public String getCollectionName() {
        return "users";
    }

    @Override
    public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
        // Validate email is present and valid
        Object emailObj = record.get("email");
        if (emailObj == null || emailObj.toString().isBlank()) {
            return BeforeSaveResult.error("email", "Email is required");
        }

        String email = emailObj.toString().trim();
        if (!email.matches(EMAIL_PATTERN)) {
            return BeforeSaveResult.error("email", "Invalid email format");
        }

        // Set defaults
        Map<String, Object> updates = new java.util.HashMap<>();

        if (!record.containsKey("locale") || record.get("locale") == null) {
            updates.put("locale", "en_US");
        }

        if (!record.containsKey("timezone") || record.get("timezone") == null) {
            updates.put("timezone", "UTC");
        }

        if (!record.containsKey("status") || record.get("status") == null) {
            updates.put("status", "ACTIVE");
        }

        // Normalize email to lowercase
        String normalizedEmail = email.toLowerCase();
        if (!normalizedEmail.equals(record.get("email"))) {
            updates.put("email", normalizedEmail);
        }

        if (updates.isEmpty()) {
            return BeforeSaveResult.ok();
        }

        log.debug("Setting defaults for new user with email '{}': {}", normalizedEmail, updates.keySet());
        return BeforeSaveResult.withFieldUpdates(updates);
    }

    @Override
    public BeforeSaveResult beforeUpdate(String id, Map<String, Object> record,
                                          Map<String, Object> previous, String tenantId) {
        // Validate email format if it's being updated
        if (record.containsKey("email")) {
            Object emailObj = record.get("email");
            if (emailObj != null && !emailObj.toString().isBlank()) {
                String email = emailObj.toString().trim();
                if (!email.matches(EMAIL_PATTERN)) {
                    return BeforeSaveResult.error("email", "Invalid email format");
                }

                // Normalize email to lowercase
                String normalizedEmail = email.toLowerCase();
                if (!normalizedEmail.equals(email)) {
                    return BeforeSaveResult.withFieldUpdates(Map.of("email", normalizedEmail));
                }
            }
        }

        return BeforeSaveResult.ok();
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        log.info("System collection lifecycle: user '{}' created for tenant '{}'",
                record.get("email"), tenantId);
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        log.info("System collection lifecycle: user '{}' deleted for tenant '{}'", id, tenantId);
    }
}
