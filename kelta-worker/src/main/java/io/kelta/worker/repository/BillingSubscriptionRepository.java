package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes {@code billing_subscription} — subscription state mirrored
 * from verified processor webhooks.
 *
 * <p>Written from the webhook path, which runs with no tenant context bound, so
 * every method takes {@code tenantId} explicitly and filters on it.
 */
@Repository
public class BillingSubscriptionRepository {

    private static final String COLUMNS = """
            id, tenant_id, user_id, plan_id, stripe_subscription_id, stripe_customer_id,
            status, current_period_end, cancel_at_period_end, canceled_at
            """;

    private static final RowMapper<BillingSubscription> MAPPER =
            (rs, rowNum) -> new BillingSubscription(
                    rs.getString("id"),
                    rs.getString("tenant_id"),
                    rs.getString("user_id"),
                    rs.getString("plan_id"),
                    rs.getString("stripe_subscription_id"),
                    rs.getString("stripe_customer_id"),
                    rs.getString("status"),
                    instant(rs.getTimestamp("current_period_end")),
                    rs.getBoolean("cancel_at_period_end"),
                    instant(rs.getTimestamp("canceled_at")));

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private final JdbcTemplate jdbc;

    public BillingSubscriptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BillingSubscription> findByUserId(String tenantId, String userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_subscription "
                        + "WHERE tenant_id = ? AND user_id = ?", MAPPER, tenantId, userId)
                .stream().findFirst();
    }

    public Optional<BillingSubscription> findByStripeId(String tenantId, String stripeSubscriptionId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_subscription "
                        + "WHERE tenant_id = ? AND stripe_subscription_id = ?",
                        MAPPER, tenantId, stripeSubscriptionId)
                .stream().findFirst();
    }

    /**
     * Mirrors the processor's view of a subscription.
     *
     * <p>Conflict handling is deliberately two-sided. The processor's id is the
     * identity, so a redelivered or out-of-order event updates the same row. But
     * a member may also already hold a different subscription row (the
     * {@code (tenant_id, user_id)} unique constraint), which happens when a
     * subscription is replaced rather than modified — in that case the member's
     * existing row is retargeted onto the new processor id rather than failing.
     */
    public void upsert(String tenantId, String userId, String planId,
                       String stripeSubscriptionId, String stripeCustomerId, String status,
                       Instant currentPeriodEnd, boolean cancelAtPeriodEnd, Instant canceledAt) {
        int updated = jdbc.update("""
                        UPDATE billing_subscription
                           SET user_id = ?, plan_id = ?, stripe_customer_id = ?, status = ?,
                               current_period_end = ?, cancel_at_period_end = ?, canceled_at = ?,
                               updated_at = NOW()
                         WHERE tenant_id = ? AND stripe_subscription_id = ?
                        """,
                userId, planId, stripeCustomerId, status,
                toTimestamp(currentPeriodEnd), cancelAtPeriodEnd, toTimestamp(canceledAt),
                tenantId, stripeSubscriptionId);
        if (updated > 0) {
            return;
        }
        // No row for this processor id. Retarget the member's existing row if they
        // have one (subscription replaced), else insert.
        int retargeted = jdbc.update("""
                        UPDATE billing_subscription
                           SET stripe_subscription_id = ?, plan_id = ?, stripe_customer_id = ?,
                               status = ?, current_period_end = ?, cancel_at_period_end = ?,
                               canceled_at = ?, updated_at = NOW()
                         WHERE tenant_id = ? AND user_id = ?
                        """,
                stripeSubscriptionId, planId, stripeCustomerId, status,
                toTimestamp(currentPeriodEnd), cancelAtPeriodEnd, toTimestamp(canceledAt),
                tenantId, userId);
        if (retargeted > 0) {
            return;
        }
        jdbc.update("""
                        INSERT INTO billing_subscription
                            (id, tenant_id, user_id, plan_id, stripe_subscription_id,
                             stripe_customer_id, status, current_period_end,
                             cancel_at_period_end, canceled_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                        ON CONFLICT (stripe_subscription_id) DO UPDATE
                           SET status = EXCLUDED.status,
                               plan_id = EXCLUDED.plan_id,
                               current_period_end = EXCLUDED.current_period_end,
                               cancel_at_period_end = EXCLUDED.cancel_at_period_end,
                               canceled_at = EXCLUDED.canceled_at,
                               updated_at = NOW()
                        """,
                UUID.randomUUID().toString(), tenantId, userId, planId, stripeSubscriptionId,
                stripeCustomerId, status, toTimestamp(currentPeriodEnd),
                cancelAtPeriodEnd, toTimestamp(canceledAt));
    }

    /** Marks a subscription canceled without touching the rest of its state. */
    public int markCanceled(String tenantId, String stripeSubscriptionId,
                            String status, Instant canceledAt) {
        return jdbc.update("""
                        UPDATE billing_subscription
                           SET status = ?, canceled_at = COALESCE(?, canceled_at), updated_at = NOW()
                         WHERE tenant_id = ? AND stripe_subscription_id = ?
                        """,
                status, toTimestamp(canceledAt), tenantId, stripeSubscriptionId);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
