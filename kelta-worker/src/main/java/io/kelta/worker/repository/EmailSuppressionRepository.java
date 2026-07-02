package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC persistence for {@code email_suppression} — the per-tenant unsubscribe / suppression list.
 * A {@code (tenant, email)} entry blocks all future campaign sends to that address.
 *
 * @since 1.0.0
 */
@Repository
public class EmailSuppressionRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmailSuppressionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isSuppressed(String tenantId, String email) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_suppression WHERE tenant_id = ? AND lower(email) = lower(?)",
                Integer.class, tenantId, email);
        return n != null && n > 0;
    }

    /** Adds a suppression entry idempotently. Returns true if a new row was created. */
    public boolean add(String tenantId, String email, String reason, String campaignId, String createdBy) {
        return jdbcTemplate.update("""
                INSERT INTO email_suppression (id, tenant_id, email, reason, campaign_id, created_by,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (tenant_id, email) DO NOTHING
                """, UUID.randomUUID().toString(), tenantId, email, reason, campaignId, createdBy) == 1;
    }

    public List<Map<String, Object>> list(String tenantId, int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM email_suppression WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset);
    }

    public int remove(String tenantId, String email) {
        return jdbcTemplate.update(
                "DELETE FROM email_suppression WHERE tenant_id = ? AND lower(email) = lower(?)",
                tenantId, email);
    }
}
