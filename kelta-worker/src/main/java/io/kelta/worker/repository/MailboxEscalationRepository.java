package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Claims and records SLA escalations.
 *
 * @since 1.0.0
 */
@Repository
public class MailboxEscalationRepository {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    /** Statuses that mean the conversation is finished; never escalate one of these. */
    private static final String TERMINAL_STATUSES = "('RESOLVED','CLOSED','SPAM','ARCHIVED')";

    private final JdbcTemplate jdbcTemplate;

    public MailboxEscalationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** One escalation that this pod won the race for and now owes a notification. */
    public record Claimed(String id, String tenantId, String mailboxId, String threadId,
                          String clock, String level, Instant dueAt) {
    }

    /**
     * Atomically claims every thread past the threshold for one (clock, level).
     *
     * <p>The claim is the {@code INSERT}, guarded by the unique constraint. Concurrent pods race
     * for the same rows, exactly one wins each, and the losers get zero rows back. That is why
     * there is no leader election here and no read-then-write window to lose.
     *
     * <p>Runs with <b>no tenant bound</b>, riding the {@code admin_bypass} RLS policy — the sweep
     * is cross-tenant by nature, and binding a tenant would mean N queries and a list of tenants
     * to keep current.
     *
     * <p>{@code offsetMinutes} is negative for the WARN level, which is what makes one query shape
     * serve both "approaching" and "past" thresholds.
     */
    public List<Claimed> claimDue(String clock, String level, int offsetMinutes, int limit) {
        String dueColumn = "FIRST_RESPONSE".equals(clock)
                ? "sla_first_response_due_at" : "sla_resolution_due_at";
        String stateColumn = "FIRST_RESPONSE".equals(clock)
                ? "sla_first_response_state" : "sla_resolution_state";
        // A thread that has already been answered has met its first-response promise, so only
        // the resolution clock keeps running for it.
        String metCondition = "FIRST_RESPONSE".equals(clock)
                ? "AND t.first_response_at IS NULL"
                : "AND t.resolved_at IS NULL";

        String sql = """
                INSERT INTO mailbox_escalation
                    (id, tenant_id, mailbox_id, thread_id, clock, level, sla_due_at,
                     created_at, updated_at)
                SELECT gen_random_uuid()::text, t.tenant_id, t.mailbox_id, t.id, ?, ?,
                       t.%s, now(), now()
                  FROM mailbox_thread t
                 WHERE t.%s = 'PENDING'
                   AND t.%s IS NOT NULL
                   %s
                   AND t.status NOT IN %s
                   AND t.sla_paused_at IS NULL
                   AND now() >= t.%s + make_interval(mins => ?)
                 ORDER BY t.%s
                 LIMIT ?
                    ON CONFLICT (tenant_id, thread_id, clock, level) DO NOTHING
                 RETURNING id, tenant_id, mailbox_id, thread_id, clock, level, sla_due_at
                """.formatted(dueColumn, stateColumn, dueColumn, metCondition,
                TERMINAL_STATUSES, dueColumn, dueColumn);

        return jdbcTemplate.query(sql, (rs, i) -> new Claimed(
                        rs.getString("id"),
                        rs.getString("tenant_id"),
                        rs.getString("mailbox_id"),
                        rs.getString("thread_id"),
                        rs.getString("clock"),
                        rs.getString("level"),
                        rs.getTimestamp("sla_due_at") == null
                                ? null : rs.getTimestamp("sla_due_at").toInstant()),
                clock, level, offsetMinutes, limit);
    }

    /**
     * Moves threads past their due time into the BREACHED state, and answered ones into MET.
     *
     * <p>Separate from claiming so the state column stays truthful even when nobody is configured
     * to be notified. The console reads this; the escalation chain does not depend on it.
     */
    public int settleStates() {
        int met = jdbcTemplate.update("""
                UPDATE mailbox_thread
                   SET sla_first_response_state = 'MET', updated_at = now()
                 WHERE sla_first_response_state = 'PENDING' AND first_response_at IS NOT NULL
                """);
        met += jdbcTemplate.update("""
                UPDATE mailbox_thread
                   SET sla_resolution_state = 'MET', updated_at = now()
                 WHERE sla_resolution_state = 'PENDING' AND resolved_at IS NOT NULL
                """);
        // Terminal statuses are excluded for the same reason claimDue excludes them, and the two
        // MUST agree: if they diverge, a resolved or spam thread shows a permanent BREACHED badge
        // in the console while nobody was ever paged about it. Verified by seeding a resolved and
        // a spam thread past their due time and asserting neither settles to BREACHED.
        int breached = jdbcTemplate.update("""
                UPDATE mailbox_thread
                   SET sla_first_response_state = 'BREACHED', updated_at = now()
                 WHERE sla_first_response_state = 'PENDING'
                   AND first_response_at IS NULL
                   AND sla_first_response_due_at IS NOT NULL
                   AND sla_paused_at IS NULL
                   AND status NOT IN """ + TERMINAL_STATUSES + """
                   AND now() > sla_first_response_due_at
                """);
        breached += jdbcTemplate.update("""
                UPDATE mailbox_thread
                   SET sla_resolution_state = 'BREACHED', updated_at = now()
                 WHERE sla_resolution_state = 'PENDING'
                   AND resolved_at IS NULL
                   AND sla_resolution_due_at IS NOT NULL
                   AND sla_paused_at IS NULL
                   AND status NOT IN """ + TERMINAL_STATUSES + """
                   AND now() > sla_resolution_due_at
                """);
        return met + breached;
    }

    /** Contacts owed a notification at this level, with the channels each opted into. */
    public List<Map<String, Object>> contactsFor(String tenantId, String mailboxId, String level) {
        return jdbcTemplate.queryForList("""
                SELECT user_id, channels FROM mailbox_escalation_contact
                 WHERE tenant_id = ? AND mailbox_id = ? AND level = ?
                 ORDER BY created_at
                """, tenantId, mailboxId, level);
    }

    /**
     * Records what is owed before anything is attempted.
     *
     * @return delivery ids, positionally matching {@code channels}
     */
    public List<String> createPending(String tenantId, String escalationId,
                                      String recipientUserId, List<String> channels) {
        List<String> ids = new ArrayList<>(channels.size());
        for (String channel : channels) {
            String id = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO mailbox_escalation_delivery
                        (id, tenant_id, escalation_id, recipient_user_id, channel, status,
                         created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'PENDING', now(), now())
                    """, id, tenantId, escalationId, recipientUserId, channel);
            ids.add(id);
        }
        return ids;
    }

    public void markSent(String tenantId, String deliveryId) {
        jdbcTemplate.update("""
                UPDATE mailbox_escalation_delivery
                   SET status = 'SENT', sent_at = now(), updated_at = now()
                 WHERE id = ? AND tenant_id = ?
                """, deliveryId, tenantId);
    }

    public void markFailed(String tenantId, String deliveryId, String error) {
        jdbcTemplate.update("""
                UPDATE mailbox_escalation_delivery
                   SET status = 'FAILED', error = ?, updated_at = now()
                 WHERE id = ? AND tenant_id = ?
                """, truncate(error), deliveryId, tenantId);
    }

    /** Escalations on a thread, newest first, for the console's history strip. */
    public List<Map<String, Object>> listForThread(String tenantId, String threadId) {
        return jdbcTemplate.queryForList("""
                SELECT * FROM mailbox_escalation
                 WHERE tenant_id = ? AND thread_id = ?
                 ORDER BY created_at DESC
                """, tenantId, threadId);
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 497) + "...";
    }
}
