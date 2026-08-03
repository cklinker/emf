package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and writes {@code billing_pass} — one-time passes granted by a completed
 * checkout.
 *
 * <p>Written from the webhook path, which runs with no tenant context bound, so
 * every method takes {@code tenantId} explicitly and filters on it.
 */
@Repository
public class BillingPassRepository {

    private static final String COLUMNS = """
            id, tenant_id, user_id, plan_id, stripe_checkout_session_id,
            status, starts_at, expires_at
            """;

    private static final RowMapper<BillingPass> MAPPER = (rs, rowNum) -> new BillingPass(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("user_id"),
            rs.getString("plan_id"),
            rs.getString("stripe_checkout_session_id"),
            rs.getString("status"),
            instant(rs.getTimestamp("starts_at")),
            instant(rs.getTimestamp("expires_at")));

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private final JdbcTemplate jdbc;

    public BillingPassRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * All ACTIVE passes for a member. Expiry is judged by the caller at read time
     * ({@link BillingPass#isLive(Instant)}) so a member is never over-entitled by
     * a sweep that has not run yet.
     */
    public List<BillingPass> findActiveByUserId(String tenantId, String userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_pass "
                        + "WHERE tenant_id = ? AND user_id = ? AND status = 'ACTIVE' "
                        + "ORDER BY expires_at DESC NULLS FIRST",
                MAPPER, tenantId, userId);
    }

    /**
     * Grants a pass, idempotently on the checkout session id. Returns true when
     * this call created the row — a redelivered webhook returns false and mints
     * nothing.
     */
    public boolean grant(String tenantId, String userId, String planId,
                         String stripeCheckoutSessionId, String stripePaymentIntentId,
                         Instant startsAt, Instant expiresAt) {
        return jdbc.update("""
                        INSERT INTO billing_pass
                            (id, tenant_id, user_id, plan_id, stripe_checkout_session_id,
                             stripe_payment_intent_id, status, starts_at, expires_at,
                             created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, NOW(), NOW())
                        ON CONFLICT (stripe_checkout_session_id) DO NOTHING
                        """,
                UUID.randomUUID().toString(), tenantId, userId, planId,
                stripeCheckoutSessionId, stripePaymentIntentId,
                Timestamp.from(startsAt == null ? Instant.now() : startsAt),
                expiresAt == null ? null : Timestamp.from(expiresAt)) > 0;
    }

    /**
     * Expires due passes across all tenants, returning the (tenantId, userId) of
     * each so their entitlement caches can be invalidated. Bounded per run and
     * uses {@code SKIP LOCKED} so concurrent pods take disjoint slices.
     */
    public List<Map<String, Object>> expireDue(int batchLimit) {
        return jdbc.query("""
                        UPDATE billing_pass
                           SET status = 'EXPIRED', updated_at = NOW()
                         WHERE id IN (
                               SELECT id FROM billing_pass
                                WHERE status = 'ACTIVE'
                                  AND expires_at IS NOT NULL
                                  AND expires_at <= NOW()
                                ORDER BY expires_at ASC
                                LIMIT ?
                                FOR UPDATE SKIP LOCKED)
                     RETURNING tenant_id, user_id
                        """,
                (rs, i) -> Map.<String, Object>of(
                        "tenantId", rs.getString("tenant_id"),
                        "userId", rs.getString("user_id")),
                batchLimit);
    }
}
