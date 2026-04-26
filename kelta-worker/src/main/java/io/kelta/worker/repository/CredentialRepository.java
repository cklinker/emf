package io.kelta.worker.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-side and operational queries for the {@code credential} table.
 *
 * <p>Writes (create/update/delete of credential rows) flow through the
 * dynamic collection router so that {@code BeforeSaveHook} chains run as
 * expected. This repository handles cases where the controller needs direct
 * SQL access — typically for test-result updates, OAuth-tied lookups, and
 * decryption.
 */
@Repository
public class CredentialRepository {

    private final JdbcTemplate jdbcTemplate;

    public CredentialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Loads a single credential by ID, scoped to {@code tenantId}.
     * Returns {@code Optional.empty()} when the row is missing or belongs to
     * another tenant. The returned map includes {@code data_enc} so callers
     * with decryption authority can read the secret blob — masked separately
     * before returning to API clients.
     */
    public Optional<Map<String, Object>> findById(String id, String tenantId) {
        try {
            return Optional.of(jdbcTemplate.queryForMap(
                "SELECT id, tenant_id, name, display_name, description, type, "
                    + "provider_template, data_enc, metadata, last_test_at, "
                    + "last_test_status, last_test_error, active, created_by, "
                    + "created_at, updated_by, updated_at "
                    + "FROM credential WHERE id = ? AND tenant_id = ?",
                id, tenantId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** Finds an active credential by name within a tenant. */
    public Optional<Map<String, Object>> findByName(String name, String tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, tenant_id, name, display_name, description, type, "
                + "provider_template, data_enc, metadata, last_test_at, "
                + "last_test_status, last_test_error, active, created_by, "
                + "created_at, updated_by, updated_at "
                + "FROM credential WHERE name = ? AND tenant_id = ? AND active = TRUE",
            name, tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Records the outcome of a credential test. */
    public void updateTestStatus(String id, String tenantId, String status, String errorMessage) {
        jdbcTemplate.update(
            "UPDATE credential SET last_test_at = ?, last_test_status = ?, "
                + "last_test_error = ?, updated_at = ? "
                + "WHERE id = ? AND tenant_id = ?",
            Timestamp.from(Instant.now()), status, errorMessage,
            Timestamp.from(Instant.now()), id, tenantId);
    }
}
