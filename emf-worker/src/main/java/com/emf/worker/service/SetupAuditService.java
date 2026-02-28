package com.emf.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Service for recording configuration changes to the {@code setup_audit_trail} table.
 *
 * <p>All logging is non-fatal — exceptions are caught and logged as warnings
 * to ensure audit failures never disrupt normal operations.
 *
 * @since 1.0.0
 */
@Service
public class SetupAuditService {

    private static final Logger log = LoggerFactory.getLogger(SetupAuditService.class);

    private final JdbcTemplate jdbcTemplate;

    public SetupAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Logs a setup audit entry.
     *
     * @param tenantId   the tenant ID
     * @param userId     the user who made the change (platform_user UUID)
     * @param action     the action performed (e.g., CREATE, UPDATE, DELETE)
     * @param section    the setup section (e.g., Schema, Profiles, Users)
     * @param entityType the type of entity changed (e.g., collection, field, profile)
     * @param entityId   the ID of the entity (may be null)
     * @param entityName the name of the entity (may be null)
     * @param oldValue   the old value as JSON (may be null)
     * @param newValue   the new value as JSON (may be null)
     */
    public void log(String tenantId, String userId, String action, String section,
                    String entityType, String entityId, String entityName,
                    String oldValue, String newValue) {
        try {
            String id = UUID.randomUUID().toString();
            Timestamp now = Timestamp.from(Instant.now());

            // Use a safe userId fallback — the table requires non-null user_id
            String safeUserId = userId != null ? userId : "system";

            jdbcTemplate.update(
                    "INSERT INTO setup_audit_trail " +
                            "(id, tenant_id, user_id, action, section, entity_type, entity_id, entity_name, " +
                            "old_value, new_value, timestamp, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)",
                    id, tenantId, safeUserId, action, section, entityType, entityId, entityName,
                    oldValue, newValue, now, now, now);

            log.debug("Setup audit logged: {} {} {} {} in tenant {}", action, entityType, entityId, entityName, tenantId);
        } catch (Exception e) {
            log.warn("Failed to write setup audit entry: {} {} {}: {}",
                    action, entityType, entityId, e.getMessage());
        }
    }
}
