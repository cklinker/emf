package io.kelta.runtime.cell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC-backed {@link TenantCellResolver}. Reads {@code tenant.cell_id} and
 * caches the result indefinitely in process. The cache is intentionally
 * unbounded but the per-tenant memory cost is bytes (UUID string → short
 * cell id string) so this scales to the active-tenant ceiling of any
 * single pod.
 *
 * <p>Cache invalidation: cell assignment is operator-driven (a tenant is
 * pinned to a cell when it provisions, rebalanced rarely). When a tenant
 * moves cells, restart the pods that have cached the old assignment, or
 * publish a NATS event and call {@link #invalidate(String)}. We don't ship
 * the eviction listener yet — flagged for the follow-up gateway-side
 * routing PR.
 *
 * @since 1.0.0
 */
public final class JdbcTenantCellResolver implements TenantCellResolver {

    private static final Logger log = LoggerFactory.getLogger(JdbcTenantCellResolver.class);

    private static final String SELECT_CELL =
            "SELECT cell_id FROM tenant WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public JdbcTenantCellResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String cellFor(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return DEFAULT_CELL_ID;
        }
        return cache.computeIfAbsent(tenantId, this::lookup);
    }

    private String lookup(String tenantId) {
        try {
            String cellId = jdbcTemplate.queryForObject(SELECT_CELL, String.class, tenantId);
            return (cellId == null || cellId.isBlank()) ? DEFAULT_CELL_ID : cellId;
        } catch (Exception e) {
            // Empty result, DB hiccup, etc. — default cell is safe.
            log.debug("Cell lookup failed for tenant {} — defaulting to '{}': {}",
                    tenantId, DEFAULT_CELL_ID, e.getMessage());
            return DEFAULT_CELL_ID;
        }
    }

    /**
     * Drops a cached entry so the next {@link #cellFor(String)} hits the
     * database again. Intended for a future NATS-driven invalidation
     * listener (when an operator rebalances a tenant to a new cell).
     */
    public void invalidate(String tenantId) {
        if (tenantId == null) {
            return;
        }
        cache.remove(tenantId);
    }

    /** Drop all cached entries. */
    public void invalidateAll() {
        cache.clear();
    }

    int cachedSize() {
        return cache.size();
    }
}
