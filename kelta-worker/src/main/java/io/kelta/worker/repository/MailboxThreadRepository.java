package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes {@code mailbox_thread}.
 *
 * @since 1.0.0
 */
@Repository
public class MailboxThreadRepository {

    /** Statuses a late reply must not be threaded onto. */
    private static final String CLOSED_STATUSES = "('RESOLVED','CLOSED','SPAM','ARCHIVED')";

    private final JdbcTemplate jdbcTemplate;

    public MailboxThreadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Map<String, Object>> findById(String id, String tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM mailbox_thread WHERE id = ? AND tenant_id = ?", id, tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /**
     * Finds an open thread by normalized subject and requester, within the mailbox's configured
     * window.
     *
     * <p>The {@code requester_email} match is not an optimisation — it is a safety constraint.
     * Threading on subject alone would splice two customers' correspondence into one thread the
     * first time both write "Re: Booking question", showing each of them the other's history.
     * That is a disclosure bug dressed as a convenience heuristic.
     */
    public Optional<String> findBySubject(String tenantId, String mailboxId,
                                          String normalizedSubject, String requesterEmail, int days) {
        if (days <= 0 || normalizedSubject == null || normalizedSubject.isBlank()) {
            return Optional.empty();
        }
        List<String> ids = jdbcTemplate.queryForList("""
                SELECT id FROM mailbox_thread
                 WHERE tenant_id = ?
                   AND mailbox_id = ?
                   AND normalized_subject = ?
                   AND lower(requester_email) = lower(?)
                   AND status NOT IN """ + CLOSED_STATUSES + """
                   AND last_message_at >= NOW() - make_interval(days => ?)
                 ORDER BY last_message_at DESC
                 LIMIT 1
                """, String.class, tenantId, mailboxId, normalizedSubject, requesterEmail, days);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.getFirst());
    }

    /** True when the thread is in a state a new message must not join. */
    public boolean isClosed(String tenantId, String threadId) {
        List<String> status = jdbcTemplate.queryForList(
                "SELECT status FROM mailbox_thread WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, threadId);
        return status.isEmpty()
                || List.of("RESOLVED", "CLOSED", "SPAM", "ARCHIVED").contains(status.getFirst());
    }

    public String create(String tenantId, String mailboxId, String subject, String normalizedSubject,
                         String requesterEmail, String requesterName, boolean requesterVerified,
                         String verificationMethod, String status, String parentThreadId) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO mailbox_thread
                    (id, tenant_id, mailbox_id, subject, normalized_subject, status,
                     requester_email, requester_name, requester_verified, verification_method,
                     parent_thread_id, message_count, last_message_at, last_inbound_at,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW(), NOW(), NOW(), NOW())
                """, id, tenantId, mailboxId, subject, normalizedSubject, status,
                requesterEmail, requesterName, requesterVerified, verificationMethod, parentThreadId);
        return id;
    }

    /** Bumps activity counters after an inbound message is stored. */
    public void recordInbound(String tenantId, String threadId) {
        jdbcTemplate.update("""
                UPDATE mailbox_thread
                   SET message_count   = message_count + 1,
                       last_message_at = NOW(),
                       last_inbound_at = NOW(),
                       updated_at      = NOW()
                 WHERE id = ? AND tenant_id = ?
                """, threadId, tenantId);
    }

    /**
     * Sets the SLA clock, once, at thread creation.
     *
     * <p>Absolute instants rather than a policy reference: the promise is made at a point in time
     * and must not move when an admin later edits the mailbox's policy. Deriving it at read would
     * retroactively breach or un-breach every open thread and rewrite history in every SLA report
     * already run.
     */
    public void setSlaClock(String tenantId, String threadId,
                            Integer firstResponseMinutes, Integer resolutionMinutes,
                            int riskThresholdPct) {
        if (firstResponseMinutes == null && resolutionMinutes == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE mailbox_thread
                   SET sla_first_response_due_at =
                           CASE WHEN ?::int IS NULL THEN NULL
                                ELSE NOW() + make_interval(mins => ?::int) END,
                       sla_first_response_state =
                           CASE WHEN ?::int IS NULL THEN 'NONE' ELSE 'PENDING' END,
                       sla_resolution_due_at =
                           CASE WHEN ?::int IS NULL THEN NULL
                                ELSE NOW() + make_interval(mins => ?::int) END,
                       sla_resolution_state =
                           CASE WHEN ?::int IS NULL THEN 'NONE' ELSE 'PENDING' END,
                       updated_at = NOW()
                 WHERE id = ? AND tenant_id = ?
                """,
                firstResponseMinutes, firstResponseMinutes, firstResponseMinutes,
                resolutionMinutes, resolutionMinutes, resolutionMinutes,
                threadId, tenantId);
    }

    /** Marks a thread as spam and stops any SLA clock on it. */
    public void markSpam(String tenantId, String threadId) {
        // The clock is cleared as well as the status set. Leaving it running would have the
        // escalation sweep page a human at 3am about a spam message.
        jdbcTemplate.update("""
                UPDATE mailbox_thread
                   SET status = 'SPAM',
                       sla_first_response_state = 'NONE', sla_first_response_due_at = NULL,
                       sla_resolution_state = 'NONE',     sla_resolution_due_at = NULL,
                       updated_at = NOW()
                 WHERE id = ? AND tenant_id = ?
                """, threadId, tenantId);
    }

    /**
     * Strips reply and forward prefixes so "Re: Re: Fwd: Booking" threads with "Booking".
     *
     * <p>Covers the common non-English prefixes too — a German client sends {@code AW:} and a
     * French one {@code RE:}/{@code TR:}, and missing them means every reply starts a new thread.
     */
    public static String normalizeSubject(String subject) {
        if (subject == null) {
            return null;
        }
        String s = subject.trim();
        String previous;
        do {
            previous = s;
            s = s.replaceFirst("(?i)^\\s*(re|aw|fw|fwd|tr|antw|sv|vs|vb|res)\\s*(\\[\\d+\\])?\\s*:\\s*", "");
        } while (!s.equals(previous));
        s = s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        return s.length() > 255 ? s.substring(0, 255) : s;
    }
}
