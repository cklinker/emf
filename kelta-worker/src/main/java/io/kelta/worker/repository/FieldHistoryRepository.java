package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads/writes the {@code field_history} audit log of value changes to tracked fields.
 *
 * <p>Runs under the request's tenant context, so Postgres RLS (enabled on {@code field_history}
 * in V77) scopes every row to the tenant; the explicit {@code tenant_id} filter on reads is
 * defence-in-depth. Follows the hand-written-SQL {@code JdbcTemplate} idiom (see
 * {@link RecordTombstoneRepository}) — no JPA.
 */
@Repository
public class FieldHistoryRepository {

    /** Cap on rows returned by a single history query, mirroring the HTTP page-size clamp. */
    public static final int MAX_PAGE_SIZE = 200;

    private final JdbcTemplate jdbc;

    public FieldHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One field's before/after value for a batch insert. {@code oldValue}/{@code newValue} are raw JSON text (or null). */
    public record Change(String fieldName, String oldValue, String newValue) {
    }

    /**
     * Records a batch of field changes for a single record edit. No-op when {@code changes} is empty.
     * {@code oldValue}/{@code newValue} are pre-serialized JSON strings inserted into the JSONB columns.
     */
    public void recordChanges(String tenantId, String collectionId, String recordId,
                              String changedBy, String changeSource, List<Change> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                        INSERT INTO field_history
                            (id, tenant_id, collection_id, record_id, field_name,
                             old_value, new_value, changed_by, changed_at, change_source)
                        VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, NOW(), ?)
                        """,
                changes,
                changes.size(),
                (ps, change) -> {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, tenantId);
                    ps.setString(3, collectionId);
                    ps.setString(4, recordId);
                    ps.setString(5, change.fieldName());
                    ps.setString(6, change.oldValue());
                    ps.setString(7, change.newValue());
                    ps.setString(8, changedBy);
                    ps.setString(9, changeSource);
                });
    }

    /**
     * Filtered, paginated read. {@code collectionId} is required; {@code recordId}, {@code fieldName},
     * and {@code changedBy} are optional narrowing filters. Newest change first.
     */
    public List<FieldHistoryEntry> find(String tenantId, String collectionId, String recordId,
                                        String fieldName, String changedBy, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, collection_id, record_id, field_name,
                       old_value::text AS old_value, new_value::text AS new_value,
                       changed_by, changed_at, change_source
                FROM field_history
                WHERE tenant_id = ? AND collection_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(collectionId);
        if (recordId != null) {
            sql.append(" AND record_id = ?");
            params.add(recordId);
        }
        if (fieldName != null) {
            sql.append(" AND field_name = ?");
            params.add(fieldName);
        }
        if (changedBy != null) {
            sql.append(" AND changed_by = ?");
            params.add(changedBy);
        }
        sql.append(" ORDER BY changed_at DESC LIMIT ? OFFSET ?");
        params.add(Math.min(limit, MAX_PAGE_SIZE));
        params.add(offset);

        return jdbc.query(sql.toString(), (rs, rowNum) -> new FieldHistoryEntry(
                rs.getString("id"),
                rs.getString("collection_id"),
                rs.getString("record_id"),
                rs.getString("field_name"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getString("changed_by"),
                rs.getTimestamp("changed_at").toInstant(),
                rs.getString("change_source")), params.toArray());
    }

    /** Total rows matching the same filters as {@link #find}, for pagination metadata. */
    public long count(String tenantId, String collectionId, String recordId,
                      String fieldName, String changedBy) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM field_history WHERE tenant_id = ? AND collection_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(collectionId);
        if (recordId != null) {
            sql.append(" AND record_id = ?");
            params.add(recordId);
        }
        if (fieldName != null) {
            sql.append(" AND field_name = ?");
            params.add(fieldName);
        }
        if (changedBy != null) {
            sql.append(" AND changed_by = ?");
            params.add(changedBy);
        }
        Long total = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
        return total != null ? total : 0L;
    }
}
