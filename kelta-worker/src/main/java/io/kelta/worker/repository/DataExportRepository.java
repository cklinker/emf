package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for data export persistence and status tracking.
 *
 * @since 1.0.0
 */
@Repository
public class DataExportRepository {

    private final JdbcTemplate jdbcTemplate;

    public DataExportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates a new data export record.
     */
    public String create(String tenantId, String name, String description,
                         String exportScope, String collectionIds,
                         String format, String createdBy) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO data_export (id, tenant_id, name, description, export_scope, " +
                        "collection_ids, format, status, created_by, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, NOW(), NOW())",
                id, tenantId, name, description, exportScope, collectionIds, format, createdBy
        );
        return id;
    }

    /**
     * Finds a data export by ID and tenant.
     */
    public Optional<Map<String, Object>> findByIdAndTenant(String exportId, String tenantId) {
        var results = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, name, description, export_scope, collection_ids, " +
                        "format, status, total_records, records_exported, storage_key, " +
                        "file_size_bytes, created_by, started_at, completed_at, " +
                        "error_message, created_at, updated_at " +
                        "FROM data_export WHERE id = ? AND tenant_id = ?",
                exportId, tenantId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Lists data exports for a tenant with pagination.
     */
    public List<Map<String, Object>> findByTenant(String tenantId, int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT id, tenant_id, name, description, export_scope, collection_ids, " +
                        "format, status, total_records, records_exported, storage_key, " +
                        "file_size_bytes, created_by, started_at, completed_at, " +
                        "error_message, created_at, updated_at " +
                        "FROM data_export WHERE tenant_id = ? " +
                        "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset
        );
    }

    /**
     * Counts data exports for a tenant.
     */
    public int countByTenant(String tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_export WHERE tenant_id = ?",
                Integer.class, tenantId
        );
        return count != null ? count : 0;
    }

    /**
     * Marks a data export as in progress.
     */
    public void markInProgress(String exportId) {
        jdbcTemplate.update(
                "UPDATE data_export SET status = 'IN_PROGRESS', started_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), exportId
        );
    }

    /**
     * Marks a data export as completed.
     */
    public void markCompleted(String exportId, int totalRecords, int recordsExported,
                              String storageKey, long fileSizeBytes) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "UPDATE data_export SET status = 'COMPLETED', total_records = ?, records_exported = ?, " +
                        "storage_key = ?, file_size_bytes = ?, completed_at = ?, updated_at = ? WHERE id = ?",
                totalRecords, recordsExported, storageKey, fileSizeBytes,
                Timestamp.from(now), Timestamp.from(now), exportId
        );
    }

    /**
     * Marks a data export as failed.
     */
    public void markFailed(String exportId, String errorMessage) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "UPDATE data_export SET status = 'FAILED', error_message = ?, " +
                        "completed_at = ?, updated_at = ? WHERE id = ?",
                errorMessage, Timestamp.from(now), Timestamp.from(now), exportId
        );
    }

    /**
     * Updates the progress of a data export.
     */
    public void updateProgress(String exportId, int totalRecords, int recordsExported) {
        jdbcTemplate.update(
                "UPDATE data_export SET total_records = ?, records_exported = ?, updated_at = ? WHERE id = ?",
                totalRecords, recordsExported, Timestamp.from(Instant.now()), exportId
        );
    }

    /**
     * Cancels a pending data export.
     */
    public int cancel(String exportId, String tenantId) {
        return jdbcTemplate.update(
                "UPDATE data_export SET status = 'CANCELLED', updated_at = ? " +
                        "WHERE id = ? AND tenant_id = ? AND status = 'PENDING'",
                Timestamp.from(Instant.now()), exportId, tenantId
        );
    }

    /**
     * Finds a pending export for processing. Uses SKIP LOCKED for multi-pod safety.
     */
    public Optional<Map<String, Object>> findPendingExport(String exportId) {
        var results = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, name, export_scope, collection_ids, format, created_by " +
                        "FROM data_export WHERE id = ? AND status = 'PENDING' " +
                        "FOR UPDATE SKIP LOCKED",
                exportId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
