package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Reads {@code watch}. Watches are written through the slice-5 owner-scoped
 * controller and the owner-guarded generic route, so this repository is read-only
 * plus the expiry sweep.
 *
 * <p>The matcher's hot path is {@link #findLiveForTarget}: one indexed query per
 * availability transition. It filters on status in SQL and leaves criteria
 * evaluation to Java, because criteria is opaque JSONB and pushing it into SQL
 * would couple the query plan to a shape members can change.
 */
@Repository
public class WatchRepository {

    private static final String COLUMNS = """
            id, tenant_id, member_id, target_id, criteria::text AS criteria,
            channels::text AS channels, status, expires_at
            """;

    private static final RowMapper<Watch> MAPPER = (rs, rowNum) -> new Watch(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("member_id"),
            rs.getString("target_id"),
            rs.getString("criteria"),
            rs.getString("channels"),
            rs.getString("status"),
            instant(rs.getTimestamp("expires_at")));

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private final JdbcTemplate jdbc;

    public WatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Every ACTIVE, unexpired watch on a target — the matcher's fan-out set.
     *
     * <p>Expiry is filtered in SQL as well as re-checked in {@link Watch#isLive},
     * so a late expiry sweep cannot cause an alert on a dead watch.
     */
    public List<Watch> findLiveForTarget(String tenantId, String targetId, Instant now) {
        return jdbc.query("SELECT " + COLUMNS + " FROM watch "
                        + "WHERE tenant_id = ? AND target_id = ? AND status = 'ACTIVE' "
                        + "AND (expires_at IS NULL OR expires_at > ?) "
                        + "ORDER BY created_at",
                MAPPER, tenantId, targetId, Timestamp.from(now));
    }

    /** A member's own watches, newest first — backs the slice-5 list endpoint. */
    public List<Watch> findByMember(String tenantId, String memberId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM watch "
                + "WHERE tenant_id = ? AND member_id = ? ORDER BY created_at DESC",
                MAPPER, tenantId, memberId);
    }

    /** Count of a member's watches in a given status (entitlement quota checks). */
    public int countByMemberAndStatus(String tenantId, String memberId, String status) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM watch WHERE tenant_id = ? AND member_id = ? AND status = ?",
                Integer.class, tenantId, memberId, status);
        return count == null ? 0 : count;
    }

    /**
     * Moves due watches to EXPIRED, returning the affected members so their caches
     * and notifications can be updated. {@code SKIP LOCKED} keeps concurrent pods
     * on disjoint slices.
     */
    public List<String> expireDue(int batchLimit) {
        return jdbc.query("""
                        UPDATE watch
                           SET status = 'EXPIRED', updated_at = NOW()
                         WHERE id IN (
                               SELECT id FROM watch
                                WHERE status = 'ACTIVE'
                                  AND expires_at IS NOT NULL
                                  AND expires_at <= NOW()
                                ORDER BY expires_at
                                LIMIT ?
                                FOR UPDATE SKIP LOCKED)
                     RETURNING id
                        """,
                (rs, i) -> rs.getString("id"), batchLimit);
    }
}
