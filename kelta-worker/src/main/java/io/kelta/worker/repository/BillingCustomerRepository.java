package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes {@code billing_customer} — the member-to-processor customer
 * mapping.
 *
 * <p>Written from the webhook path, which runs with no tenant context bound, so
 * every method takes {@code tenantId} explicitly and filters on it.
 */
@Repository
public class BillingCustomerRepository {

    private static final String COLUMNS =
            "id, tenant_id, user_id, stripe_customer_id, email";

    private static final RowMapper<BillingCustomer> MAPPER = (rs, rowNum) -> new BillingCustomer(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("user_id"),
            rs.getString("stripe_customer_id"),
            rs.getString("email"));

    private final JdbcTemplate jdbc;

    public BillingCustomerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BillingCustomer> findByUserId(String tenantId, String userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_customer "
                        + "WHERE tenant_id = ? AND user_id = ?", MAPPER, tenantId, userId)
                .stream().findFirst();
    }

    public Optional<BillingCustomer> findByStripeCustomerId(String tenantId, String stripeCustomerId) {
        if (stripeCustomerId == null || stripeCustomerId.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_customer "
                        + "WHERE tenant_id = ? AND stripe_customer_id = ?",
                        MAPPER, tenantId, stripeCustomerId)
                .stream().findFirst();
    }

    /**
     * Records the member-to-customer mapping, tolerating redelivery: a repeated
     * webhook refreshes the email rather than failing on the unique constraint.
     */
    public void upsert(String tenantId, String userId, String stripeCustomerId, String email) {
        int updated = jdbc.update(
                "UPDATE billing_customer SET stripe_customer_id = ?, email = COALESCE(?, email), "
                        + "updated_at = NOW() WHERE tenant_id = ? AND user_id = ?",
                stripeCustomerId, email, tenantId, userId);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO billing_customer "
                            + "(id, tenant_id, user_id, stripe_customer_id, email, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, NOW(), NOW()) "
                            + "ON CONFLICT (tenant_id, user_id) DO UPDATE "
                            + "SET stripe_customer_id = EXCLUDED.stripe_customer_id, "
                            + "email = COALESCE(EXCLUDED.email, billing_customer.email), "
                            + "updated_at = NOW()",
                    UUID.randomUUID().toString(), tenantId, userId, stripeCustomerId, email);
        }
    }
}
