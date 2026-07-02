package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for {@code email_campaign} (the "campaigns" read-only system collection).
 *
 * <p>Mutations flow through this repository (not the generic collection router) so the
 * {@code CampaignAdminController} can enforce {@code MANAGE_CAMPAIGNS} and the send governor
 * limit. The runner claims work with a conditional {@code UPDATE ... WHERE status IN (...)},
 * mirroring the {@code SELECT FOR UPDATE SKIP LOCKED} leader election in {@link BulkJobRepository}
 * but robust across pods regardless of autocommit/transaction scoping.
 *
 * @since 1.0.0
 */
@Repository
public class CampaignRepository {

    private final JdbcTemplate jdbcTemplate;

    public CampaignRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserts a DRAFT campaign. Runs under the tenant's RLS context. */
    public String create(String tenantId, Map<String, Object> a, String createdBy) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO email_campaign (id, tenant_id, name, description, subject, body_html,
                    template_id, target_collection, recipient_email_field, filter_json, list_view_id,
                    from_name, from_address, status, scheduled_at, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::timestamptz, ?, NOW(), NOW())
                """,
                id, tenantId,
                a.get("name"), a.get("description"), a.get("subject"), a.get("bodyHtml"),
                a.get("templateId"), a.get("targetCollection"), a.get("recipientEmailField"),
                asJson(a.get("filterJson")), a.get("listViewId"),
                a.get("fromName"), a.get("fromAddress"),
                a.getOrDefault("status", "DRAFT"), a.get("scheduledAt"), createdBy);
        return id;
    }

    /** Updates the editable fields of a DRAFT/SCHEDULED campaign. */
    public int update(String id, String tenantId, Map<String, Object> a, String updatedBy) {
        return jdbcTemplate.update("""
                UPDATE email_campaign SET
                    name = ?, description = ?, subject = ?, body_html = ?, template_id = ?,
                    target_collection = ?, recipient_email_field = ?, filter_json = ?::jsonb,
                    list_view_id = ?, from_name = ?, from_address = ?, scheduled_at = ?::timestamptz,
                    updated_by = ?, updated_at = NOW()
                WHERE id = ? AND tenant_id = ? AND status IN ('DRAFT','SCHEDULED')
                """,
                a.get("name"), a.get("description"), a.get("subject"), a.get("bodyHtml"),
                a.get("templateId"), a.get("targetCollection"), a.get("recipientEmailField"),
                asJson(a.get("filterJson")), a.get("listViewId"),
                a.get("fromName"), a.get("fromAddress"), a.get("scheduledAt"),
                updatedBy, id, tenantId);
    }

    public Optional<Map<String, Object>> findById(String id, String tenantId) {
        var rows = jdbcTemplate.queryForList(
                "SELECT * FROM email_campaign WHERE id = ? AND tenant_id = ?", id, tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<Map<String, Object>> list(String tenantId, int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM email_campaign WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset);
    }

    public int deleteDraft(String id, String tenantId) {
        return jdbcTemplate.update(
                "DELETE FROM email_campaign WHERE id = ? AND tenant_id = ? "
                        + "AND status IN ('DRAFT','SCHEDULED','CANCELLED','FAILED')",
                id, tenantId);
    }

    /**
     * Moves a campaign into a runnable state. {@code QUEUED} = send ASAP; {@code SCHEDULED}
     * with a future {@code scheduled_at} = the poller picks it up when the time arrives.
     * Only DRAFT/SCHEDULED campaigns can (re)enter the queue.
     */
    public int enqueue(String id, String tenantId, String status, Object scheduledAt) {
        return jdbcTemplate.update("""
                UPDATE email_campaign SET status = ?, scheduled_at = ?::timestamptz, updated_at = NOW()
                WHERE id = ? AND tenant_id = ? AND status IN ('DRAFT','SCHEDULED')
                """, status, scheduledAt, id, tenantId);
    }

    public int cancel(String id, String tenantId) {
        return jdbcTemplate.update("""
                UPDATE email_campaign SET status = 'CANCELLED', updated_at = NOW()
                WHERE id = ? AND tenant_id = ? AND status IN ('DRAFT','SCHEDULED','QUEUED')
                """, id, tenantId);
    }

    // --- Runner-side claim + lifecycle (invoked with no tenant context → admin_bypass) ---

    /** Candidate campaigns ready to run: QUEUED now, or SCHEDULED whose time has arrived. */
    public List<Map<String, Object>> findClaimable(int limit) {
        return jdbcTemplate.queryForList("""
                SELECT id, tenant_id FROM email_campaign
                WHERE status = 'QUEUED'
                   OR (status = 'SCHEDULED' AND scheduled_at IS NOT NULL AND scheduled_at <= NOW())
                ORDER BY created_at ASC
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, limit);
    }

    /** Atomically claims a campaign for this pod. Returns true if this pod won the claim. */
    public boolean claim(String id) {
        return jdbcTemplate.update("""
                UPDATE email_campaign SET status = 'SENDING', started_at = NOW(), updated_at = NOW()
                WHERE id = ? AND status IN ('QUEUED','SCHEDULED')
                """, id) == 1;
    }

    public void setTotalRecipients(String id, int total) {
        jdbcTemplate.update(
                "UPDATE email_campaign SET total_recipients = ?, updated_at = NOW() WHERE id = ?",
                total, id);
    }

    public void markSent(String id) {
        jdbcTemplate.update(
                "UPDATE email_campaign SET status = 'SENT', completed_at = NOW(), updated_at = NOW() WHERE id = ?",
                id);
    }

    public void markFailed(String id, String error) {
        jdbcTemplate.update(
                "UPDATE email_campaign SET status = 'FAILED', error_message = ?, "
                        + "completed_at = NOW(), updated_at = NOW() WHERE id = ?",
                truncate(error, 4000), id);
    }

    public void incrementSent(String id)  { bump(id, "sent_count"); }
    public void incrementFailed(String id) { bump(id, "failed_count"); }
    public void incrementOpen(String id)  { bump(id, "open_count"); }
    public void incrementClick(String id) { bump(id, "click_count"); }
    public void incrementUnsubscribe(String id) { bump(id, "unsubscribe_count"); }

    private void bump(String id, String column) {
        // column is a fixed literal from the callers above — never user input.
        jdbcTemplate.update(
                "UPDATE email_campaign SET " + column + " = " + column + " + 1, updated_at = NOW() WHERE id = ?",
                id);
    }

    private static String asJson(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
