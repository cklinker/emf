package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Service for recording configuration changes to PostgreSQL.
 *
 * <p>All logging is non-fatal — exceptions are caught and logged as warnings
 * to ensure audit failures never disrupt normal operations.
 */
@Service
public class SetupAuditService {

    private static final Logger log = LoggerFactory.getLogger(SetupAuditService.class);

    private final JdbcTemplate jdbcTemplate;

    public SetupAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(String tenantId, String userId, String action, String section,
                    String entityType, String entityId, String entityName,
                    String oldValue, String newValue) {
        try {
            String id = UUID.randomUUID().toString();
            Timestamp now = Timestamp.from(Instant.now());

            jdbcTemplate.update(
                    "INSERT INTO setup_audit_trail " +
                            "(id, tenant_id, user_id, action, section, entity_type, entity_id, entity_name, " +
                            "old_value, new_value, timestamp, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)",
                    id, tenantId, userId, action, section, entityType, entityId, entityName,
                    oldValue, newValue, now, now, now);

            log.debug("Setup audit logged: {} {} {} {} in tenant {}", action, entityType, entityId, entityName, tenantId);
        } catch (Exception e) {
            log.warn("Failed to write setup audit entry: {} {} {}: {}",
                    action, entityType, entityId, e.getMessage());
        }
    }
}
