package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for {@code email_campaign_recipient} — one row per resolved recipient,
 * carrying the send status plus open/click/unsubscribe tracking events.
 *
 * @since 1.0.0
 */
@Repository
public class CampaignRecipientRepository {

    private final JdbcTemplate jdbcTemplate;

    public CampaignRecipientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserts a PENDING recipient row. Returns the new id, or empty if a duplicate email was skipped. */
    public Optional<String> insertPending(String tenantId, String campaignId, String recordId, String email) {
        String id = UUID.randomUUID().toString();
        int inserted = jdbcTemplate.update("""
                INSERT INTO email_campaign_recipient (id, tenant_id, campaign_id, record_id, email,
                    status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', NOW(), NOW())
                ON CONFLICT (campaign_id, email) DO NOTHING
                """, id, tenantId, campaignId, recordId, email);
        return inserted == 1 ? Optional.of(id) : Optional.empty();
    }

    public void markSent(String id, String emailLogId) {
        jdbcTemplate.update("""
                UPDATE email_campaign_recipient
                SET status = 'SENT', email_log_id = ?, sent_at = NOW(), updated_at = NOW()
                WHERE id = ?
                """, emailLogId, id);
    }

    public void markFailed(String id, String error) {
        jdbcTemplate.update("""
                UPDATE email_campaign_recipient
                SET status = 'FAILED', error_message = ?, updated_at = NOW()
                WHERE id = ?
                """, error, id);
    }

    public void markStatus(String id, String status) {
        jdbcTemplate.update(
                "UPDATE email_campaign_recipient SET status = ?, updated_at = NOW() WHERE id = ?",
                status, id);
    }

    /** Looks up a recipient across tenants (tracking endpoints run before the tenant is known). */
    public Optional<Map<String, Object>> findByIdAnyTenant(String id) {
        var rows = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, campaign_id, email, status, opened_at, clicked_at, unsubscribed_at "
                        + "FROM email_campaign_recipient WHERE id = ?", id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Records an open. Returns true if this is the first open for the recipient, so the caller
     * increments the campaign's unique-open counter only once.
     */
    public boolean recordOpen(String id) {
        boolean first = jdbcTemplate.update(
                "UPDATE email_campaign_recipient SET opened_at = NOW() WHERE id = ? AND opened_at IS NULL",
                id) == 1;
        jdbcTemplate.update(
                "UPDATE email_campaign_recipient SET open_count = open_count + 1, updated_at = NOW() WHERE id = ?",
                id);
        return first;
    }

    /** Records a click. Returns true on the first click for the recipient. */
    public boolean recordClick(String id) {
        boolean first = jdbcTemplate.update(
                "UPDATE email_campaign_recipient SET clicked_at = NOW() WHERE id = ? AND clicked_at IS NULL",
                id) == 1;
        jdbcTemplate.update(
                "UPDATE email_campaign_recipient SET click_count = click_count + 1, updated_at = NOW() WHERE id = ?",
                id);
        return first;
    }

    /** Marks the recipient unsubscribed. Returns true if this is the first unsubscribe. */
    public boolean markUnsubscribed(String id) {
        return jdbcTemplate.update(
                "UPDATE email_campaign_recipient SET unsubscribed_at = NOW(), updated_at = NOW() "
                        + "WHERE id = ? AND unsubscribed_at IS NULL",
                id) == 1;
    }

    public List<Map<String, Object>> listByCampaign(String campaignId, String tenantId, int limit, int offset) {
        return jdbcTemplate.queryForList("""
                SELECT * FROM email_campaign_recipient
                WHERE campaign_id = ? AND tenant_id = ?
                ORDER BY created_at ASC LIMIT ? OFFSET ?
                """, campaignId, tenantId, limit, offset);
    }

    /** Count of emails actually sent today for a tenant — used to enforce the daily send governor. */
    public int countSentToday(String tenantId) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM email_campaign_recipient
                WHERE tenant_id = ? AND status = 'SENT' AND sent_at >= date_trunc('day', NOW())
                """, Integer.class, tenantId);
        return n != null ? n : 0;
    }
}
