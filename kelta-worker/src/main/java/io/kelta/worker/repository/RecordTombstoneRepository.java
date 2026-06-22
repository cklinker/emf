package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads/writes the {@code record_tombstone} deletion log used by the offline-sync changes feed.
 * Runs under the request's tenant context, so Postgres RLS scopes every row to the tenant (the
 * explicit {@code tenant_id} filter on reads is defence-in-depth).
 */
@Repository
public class RecordTombstoneRepository {

    /** Cap on deletions returned by a single changes poll. */
    static final int MAX_DELETIONS = 1000;

    private final JdbcTemplate jdbc;

    public RecordTombstoneRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String tenantId, String collectionName, String recordId) {
        jdbc.update("""
                        INSERT INTO record_tombstone (id, tenant_id, collection_name, record_id, deleted_at)
                        VALUES (?, ?, ?, ?, NOW())
                        """,
                UUID.randomUUID(), tenantId, collectionName, recordId);
    }

    /** Tombstones for a collection deleted strictly after {@code since}, oldest first. */
    public List<Tombstone> findSince(String tenantId, String collectionName, Instant since) {
        return jdbc.query("""
                        SELECT record_id, deleted_at FROM record_tombstone
                        WHERE tenant_id = ? AND collection_name = ? AND deleted_at > ?
                        ORDER BY deleted_at ASC LIMIT ?
                        """,
                (rs, rowNum) -> new Tombstone(
                        rs.getString("record_id"), rs.getTimestamp("deleted_at").toInstant()),
                tenantId, collectionName, Timestamp.from(since), MAX_DELETIONS);
    }

    public record Tombstone(String recordId, Instant deletedAt) {
    }
}
