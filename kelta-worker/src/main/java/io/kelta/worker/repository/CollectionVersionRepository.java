package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC access to {@code collection_version} — point-in-time snapshots of a collection's schema
 * used by the schema-migration planner to diff/preview (and, later, roll a collection to) a
 * prior version.
 *
 * <p>Reads/writes are tenant-scoped by Postgres RLS via the request-bound {@code TenantContext},
 * so no explicit {@code tenant_id} filter is added here (RLS is the single source of truth).
 * The {@code schema} column stores the serialized field list (see {@code CollectionVersionService}).
 *
 * @since 1.0.0
 */
@Repository
public class CollectionVersionRepository {

    private final JdbcTemplate jdbcTemplate;

    public CollectionVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Next version number for a collection: {@code max(version) + 1}, or 1 if none exist. */
    public int nextVersion(String collectionId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM collection_version WHERE collection_id = ?",
                Integer.class, collectionId);
        return (max == null ? 0 : max) + 1;
    }

    /** Inserts a new snapshot row. {@code schemaJson} is the serialized field list. */
    public void insertSnapshot(String collectionId, int version, String schemaJson) {
        jdbcTemplate.update(
                "INSERT INTO collection_version (id, collection_id, version, schema) "
                        + "VALUES (?, ?, ?, ?::jsonb)",
                UUID.randomUUID().toString(), collectionId, version, schemaJson);
    }

    /** Lists a collection's versions (newest first) with their id/version/createdAt (no schema body). */
    public List<Map<String, Object>> listVersions(String collectionId) {
        return jdbcTemplate.queryForList(
                "SELECT id, version, created_at FROM collection_version "
                        + "WHERE collection_id = ? ORDER BY version DESC",
                collectionId);
    }

    /** Loads the stored schema JSON for a specific version, if present. */
    public Optional<String> findSchema(String collectionId, int version) {
        List<String> rows = jdbcTemplate.query(
                "SELECT schema FROM collection_version WHERE collection_id = ? AND version = ?",
                (rs, i) -> rs.getString("schema"),
                collectionId, version);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }
}
