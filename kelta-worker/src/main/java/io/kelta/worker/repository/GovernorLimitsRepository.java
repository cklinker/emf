package io.kelta.worker.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for governor limits database queries.
 *
 * <p>Encapsulates SQL access for tenant limits, user counts,
 * and collection counts used by the governor limits endpoint.
 */
@Repository
public class GovernorLimitsRepository {

    private static final Logger log = LoggerFactory.getLogger(GovernorLimitsRepository.class);

    private static final String SELECT_TENANT_LIMITS = """
            SELECT limits FROM tenant WHERE id = ?
            """;

    private static final String COUNT_ACTIVE_USERS = """
            SELECT COUNT(*) FROM platform_user WHERE tenant_id = ? AND status = 'ACTIVE'
            """;

    private static final String COUNT_ACTIVE_COLLECTIONS = """
            SELECT COUNT(*) FROM collection
            WHERE tenant_id = ? AND active = true AND (system_collection = false OR system_collection IS NULL)
            """;

    private static final String UPDATE_TENANT_LIMITS = """
            UPDATE tenant SET limits = ?::jsonb, updated_at = NOW() WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public GovernorLimitsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Object> findTenantLimits(String tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_TENANT_LIMITS, tenantId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(0).get("limits"));
    }

    public int countActiveUsers(String tenantId) {
        try {
            Integer count = jdbcTemplate.queryForObject(COUNT_ACTIVE_USERS, Integer.class, tenantId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to count active users for tenant {}: {}", tenantId, e.getMessage());
            return 0;
        }
    }

    public int countActiveCollections(String tenantId) {
        try {
            Integer count = jdbcTemplate.queryForObject(COUNT_ACTIVE_COLLECTIONS, Integer.class, tenantId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to count active collections for tenant {}: {}", tenantId, e.getMessage());
            return 0;
        }
    }

    public void updateTenantLimits(String tenantId, String limitsJson) {
        jdbcTemplate.update(UPDATE_TENANT_LIMITS, limitsJson, tenantId);
    }
}
