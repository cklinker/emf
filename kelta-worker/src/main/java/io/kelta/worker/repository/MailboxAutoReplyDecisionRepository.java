package io.kelta.worker.repository;

import io.kelta.worker.service.mailbox.MailboxTemplateMatcher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The auto-reply decision ledger.
 *
 * <p>One row per inbound message, whether or not anything was sent. That is what makes shadow mode
 * useful: a boolean on the message would say "we did not auto-reply" without saying whether we
 * would have, which is the only question worth asking before going live.
 *
 * @since 1.0.0
 */
@Repository
public class MailboxAutoReplyDecisionRepository {

    private final JdbcTemplate jdbcTemplate;

    public MailboxAutoReplyDecisionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Records the decision. The unique key on (tenant, message) doubles as the claim, so two pods
     * evaluating the same message produce one row and one send.
     */
    public void record(String tenantId, String mailboxId, String threadId, String messageId,
                       String outcome, String vetoReason,
                       MailboxTemplateMatcher.Match match, String replyMessageId) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO mailbox_auto_reply_decision
                        (id, tenant_id, mailbox_id, thread_id, message_id, outcome, veto_reason,
                         matched_template_id, matched_category, confidence, ambiguous,
                         reply_message_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                    ON CONFLICT (tenant_id, message_id) DO NOTHING
                    """,
                    UUID.randomUUID().toString(), tenantId, mailboxId, threadId, messageId,
                    outcome, vetoReason,
                    match == null ? null : match.templateId(),
                    match == null ? null : match.category(),
                    match == null ? null : match.confidence(),
                    match != null && match.ambiguous(),
                    replyMessageId);
        } catch (DuplicateKeyException e) {
            // Another pod won the race; its decision stands.
        }
    }

    /** Auto-replies actually sent today, for the daily budget. */
    public int countSentToday(String tenantId, String mailboxId) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM mailbox_auto_reply_decision
                 WHERE tenant_id = ? AND mailbox_id = ? AND outcome = 'SENT'
                   AND created_at >= date_trunc('day', now())
                """, Integer.class, tenantId, mailboxId);
        return n == null ? 0 : n;
    }

    /**
     * Outcome counts over a window, for the shadow-mode report.
     *
     * <p>Grouped by outcome and veto reason together: "we withheld 400 replies" is not actionable,
     * whereas "380 of them were LOW_CONFIDENCE" says the keywords need work and "380 were
     * BLOCKED_CATEGORY" says the feature is working as intended.
     */
    public List<Map<String, Object>> summarize(String tenantId, String mailboxId, int days) {
        return jdbcTemplate.queryForList("""
                SELECT outcome, veto_reason, matched_category, count(*) AS count
                  FROM mailbox_auto_reply_decision
                 WHERE tenant_id = ? AND mailbox_id = ?
                   AND created_at >= now() - make_interval(days => ?)
                 GROUP BY outcome, veto_reason, matched_category
                 ORDER BY count DESC
                """, tenantId, mailboxId, days);
    }

    /** Recent decisions, newest first, for the admin UI's audit list. */
    public List<Map<String, Object>> listRecent(String tenantId, String mailboxId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT d.*, t.subject, t.requester_email
                  FROM mailbox_auto_reply_decision d
                  JOIN mailbox_thread t ON t.id = d.thread_id
                 WHERE d.tenant_id = ? AND d.mailbox_id = ?
                 ORDER BY d.created_at DESC
                 LIMIT ?
                """, tenantId, mailboxId, limit);
    }

    /**
     * How often an auto-reply was followed by the customer writing back.
     *
     * <p>The number that keeps the SLA dashboard honest. Auto-replying "thanks for writing!" to
     * everything scores 100% first-response compliance while helping nobody; a high follow-up rate
     * is what exposes that.
     */
    public Map<String, Object> followUpRate(String tenantId, String mailboxId, int days) {
        return jdbcTemplate.queryForMap("""
                SELECT count(*) AS auto_replied,
                       count(*) FILTER (WHERE EXISTS (
                           SELECT 1 FROM mailbox_message m
                            WHERE m.thread_id = d.thread_id
                              AND m.direction = 'INBOUND'
                              AND m.received_at > d.created_at
                              AND m.received_at < d.created_at + interval '72 hours')) AS followed_up
                  FROM mailbox_auto_reply_decision d
                 WHERE d.tenant_id = ? AND d.mailbox_id = ? AND d.outcome = 'SENT'
                   AND d.created_at >= now() - make_interval(days => ?)
                """, tenantId, mailboxId, days);
    }
}
