package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC access to {@code migration_run} and {@code migration_step} — the audit/progress trail for a
 * schema-migration execute. {@code migration_run} is tenant-scoped (RLS + NOT-NULL {@code tenant_id});
 * {@code migration_step} is isolated transitively through its FK to {@code migration_run} (no
 * {@code tenant_id} column of its own).
 *
 * <p>The end-user UI reads these back through the generic JSON:API routes
 * ({@code /api/migration-runs}, {@code /api/migration-runs/{id}}), so this repository only writes.
 *
 * @since 1.0.0
 */
@Repository
public class MigrationRunRepository {

    private final JdbcTemplate jdbcTemplate;

    public MigrationRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserts a new run row and returns its generated id. */
    public String insertRun(String tenantId, String collectionId, int fromVersion, int toVersion,
                            String status) {
        String id = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO migration_run (id, tenant_id, collection_id, from_version, to_version, "
                        + "status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, collectionId, fromVersion, toVersion, status, now, now);
        return id;
    }

    /** Updates a run's status and (optional) error message. */
    public void updateRunStatus(String runId, String status, String errorMessage) {
        jdbcTemplate.update(
                "UPDATE migration_run SET status = ?, error_message = ?, updated_at = ? WHERE id = ?",
                status, errorMessage, Timestamp.from(Instant.now()), runId);
    }

    /** Inserts a step row under a run. {@code detailsJson} is written to the JSONB {@code details} column. */
    public void insertStep(String runId, int stepNumber, String operation, String status,
                           String detailsJson, String errorMessage) {
        jdbcTemplate.update(
                "INSERT INTO migration_step (id, migration_run_id, step_number, operation, status, "
                        + "details, error_message, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                UUID.randomUUID().toString(), runId, stepNumber, operation, status,
                detailsJson, errorMessage, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
    }

    /** Loads a run row (without steps), or null if absent / not visible under RLS. */
    public Map<String, Object> findRun(String runId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, collection_id, from_version, to_version, status, error_message, "
                        + "created_at, updated_at FROM migration_run WHERE id = ?",
                runId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Lists a run's steps ordered by step number. */
    public List<Map<String, Object>> listSteps(String runId) {
        return jdbcTemplate.queryForList(
                "SELECT step_number, operation, status, details, error_message "
                        + "FROM migration_step WHERE migration_run_id = ? ORDER BY step_number ASC",
                runId);
    }
}
