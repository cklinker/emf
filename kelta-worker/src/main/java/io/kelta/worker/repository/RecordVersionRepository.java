package io.kelta.worker.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Writes the {@code record_version} log of full-record snapshots for collections with
 * collection-level {@code track_history} enabled.
 *
 * <p>Version numbers are 1-based per record and assigned atomically with a single
 * {@code INSERT ... SELECT COALESCE(MAX(version_number), 0) + 1} statement; the
 * {@code uq_record_version} unique constraint catches the rare concurrent-writer race and
 * the insert is retried. Runs under the request's tenant context, so Postgres RLS scopes
 * every row to the tenant. Follows the hand-written-SQL {@code JdbcTemplate} idiom (see
 * {@link FieldHistoryRepository}) — no JPA.
 */
@Repository
public class RecordVersionRepository {

    /** Retries after a {@link DuplicateKeyException} from a concurrent version insert. */
    private static final int MAX_RETRIES = 2;

    private static final String INSERT_VERSION = """
            INSERT INTO record_version
                (id, tenant_id, collection_id, record_id, version_number, change_type,
                 snapshot, changed_fields, changed_by, changed_at, change_source)
            SELECT ?, ?, ?, ?, COALESCE(MAX(version_number), 0) + 1, ?,
                   CAST(? AS jsonb), CAST(? AS jsonb), ?, NOW(), ?
            FROM record_version
            WHERE tenant_id = ? AND collection_id = ? AND record_id = ?
            """;

    private final JdbcTemplate jdbc;

    public RecordVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts the next version for a record. {@code snapshotJson} and {@code changedFieldsJson}
     * are pre-serialized JSON (an object and an array of field names respectively).
     *
     * @throws DuplicateKeyException when concurrent writers exhaust the retries
     */
    public void recordVersion(String tenantId, String collectionId, String recordId,
                              String changeType, String snapshotJson, String changedFieldsJson,
                              String changedBy, String changeSource) {
        DuplicateKeyException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                jdbc.update(INSERT_VERSION,
                        UUID.randomUUID().toString(), tenantId, collectionId, recordId,
                        changeType, snapshotJson, changedFieldsJson, changedBy, changeSource,
                        tenantId, collectionId, recordId);
                return;
            } catch (DuplicateKeyException e) {
                last = e;
            }
        }
        throw last;
    }
}
