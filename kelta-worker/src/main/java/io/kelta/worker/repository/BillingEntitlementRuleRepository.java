package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Reads {@code billing_entitlement_rule}. Rules are authored through the
 * {@code billing-entitlement-rules} system collection, so this repository is
 * read-only.
 */
@Repository
public class BillingEntitlementRuleRepository {

    private static final String COLUMNS = """
            id, tenant_id, collection_name, limit_key, count_filter::text AS count_filter,
            applies_to, message, active
            """;

    private static final RowMapper<BillingEntitlementRule> MAPPER =
            (rs, rowNum) -> new BillingEntitlementRule(
                    rs.getString("id"),
                    rs.getString("tenant_id"),
                    rs.getString("collection_name"),
                    rs.getString("limit_key"),
                    rs.getString("count_filter"),
                    rs.getString("applies_to"),
                    rs.getString("message"),
                    rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public BillingEntitlementRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every active rule for a tenant — the unit the quota hook caches. */
    public List<BillingEntitlementRule> findActive(String tenantId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_entitlement_rule "
                + "WHERE tenant_id = ? AND active ORDER BY collection_name, limit_key",
                MAPPER, tenantId);
    }

    /** Active rules capping a specific collection. */
    public List<BillingEntitlementRule> findActiveForCollection(String tenantId, String collectionName) {
        return jdbc.query("SELECT " + COLUMNS + " FROM billing_entitlement_rule "
                + "WHERE tenant_id = ? AND collection_name = ? AND active ORDER BY limit_key",
                MAPPER, tenantId, collectionName);
    }
}
