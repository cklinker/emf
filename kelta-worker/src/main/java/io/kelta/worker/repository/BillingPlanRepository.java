package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Reads {@code billing_plan}. Plans are authored through the
 * {@code billing-plans} system collection, so this repository is read-only.
 *
 * <p>Runs under the caller's tenant context, so Postgres RLS scopes every row;
 * the explicit {@code tenant_id} filter is defence-in-depth. Hand-written SQL on
 * {@link JdbcTemplate} — no JPA.
 */
@Repository
public class BillingPlanRepository {

    private static final String COLUMNS = """
            id, tenant_id, code, name, kind, stripe_product_id, stripe_price_id,
            entitlements::text AS entitlements, pass_duration_days, active
            """;

    private static final RowMapper<BillingPlan> MAPPER = (rs, rowNum) -> new BillingPlan(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("kind"),
            rs.getString("stripe_product_id"),
            rs.getString("stripe_price_id"),
            rs.getString("entitlements"),
            (Integer) rs.getObject("pass_duration_days"),
            rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public BillingPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All active plans for a tenant, in display order (pricing page). */
    public List<BillingPlan> findActive(String tenantId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_plan "
                + "WHERE tenant_id = ? AND active ORDER BY sort_order, name", MAPPER, tenantId);
    }

    public Optional<BillingPlan> findByCode(String tenantId, String code) {
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_plan "
                        + "WHERE tenant_id = ? AND code = ?", MAPPER, tenantId, code)
                .stream().findFirst();
    }

    public Optional<BillingPlan> findById(String tenantId, String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_plan "
                        + "WHERE tenant_id = ? AND id = ?", MAPPER, tenantId, id)
                .stream().findFirst();
    }

    /**
     * Resolves the plan a processor price id maps to. A partial unique index
     * guarantees at most one match per tenant.
     */
    public Optional<BillingPlan> findByStripePriceId(String tenantId, String stripePriceId) {
        if (stripePriceId == null || stripePriceId.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_plan "
                        + "WHERE tenant_id = ? AND stripe_price_id = ?",
                        MAPPER, tenantId, stripePriceId)
                .stream().findFirst();
    }

    /**
     * The tenant's active DEFAULT plan — the baseline a member falls back to with
     * no subscription, or once one lapses. Empty when the tenant has not defined
     * one, which resolves to "no entitlements" rather than an error.
     */
    public Optional<BillingPlan> findDefault(String tenantId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_plan "
                        + "WHERE tenant_id = ? AND kind = 'DEFAULT' AND active",
                        MAPPER, tenantId)
                .stream().findFirst();
    }
}
