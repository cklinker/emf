package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Read-side lookups for {@code record_share} rows (manual per-record shares).
 *
 * <p>Queries run under the request's tenant-bound transaction, so Postgres RLS
 * scopes every lookup to the current tenant. No caching — a revoked share must
 * stop widening access on the very next request.
 */
@Repository
public class RecordShareRepository {

    private final JdbcTemplate jdbcTemplate;

    public RecordShareRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the platform user id for an email, or null when unknown.
     */
    public String findUserIdByEmail(String email) {
        List<String> ids = jdbcTemplate.query(
                "SELECT id FROM platform_user WHERE email = ? LIMIT 1",
                (rs, rowNum) -> rs.getString("id"), email);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * Returns the ids of groups the user is a direct member of.
     */
    public List<String> findGroupIdsForUser(String userId) {
        return jdbcTemplate.query(
                "SELECT group_id FROM group_membership WHERE member_type = 'USER' AND member_id = ?",
                (rs, rowNum) -> rs.getString("group_id"), userId);
    }

    /**
     * Finds shares on the given records (by collection <em>name</em>) granted to
     * the user directly or to any of their groups. Returns one row per matching
     * share: {@code record_id} + {@code access_level}.
     */
    public List<Map<String, Object>> findSharesForPrincipal(String collectionName,
                                                            Collection<String> recordIds,
                                                            String userId,
                                                            Collection<String> groupIds) {
        if (recordIds.isEmpty() || userId == null) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT rs.record_id, rs.access_level
                FROM record_share rs
                JOIN collection c ON c.id = rs.collection_id
                WHERE c.name = ?
                  AND rs.record_id IN (%s)
                  AND (
                    (rs.shared_with_type = 'USER' AND rs.shared_with_id = ?)
                """.formatted(placeholders(recordIds.size())));

        List<Object> args = new java.util.ArrayList<>();
        args.add(collectionName);
        args.addAll(recordIds);
        args.add(userId);

        if (!groupIds.isEmpty()) {
            sql.append("    OR (rs.shared_with_type = 'GROUP' AND rs.shared_with_id IN (")
               .append(placeholders(groupIds.size()))
               .append("))\n");
            args.addAll(groupIds);
        }
        sql.append("  )");

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> Map.of(
                        "recordId", rs.getString("record_id"),
                        "accessLevel", rs.getString("access_level")),
                args.toArray());
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }
}
