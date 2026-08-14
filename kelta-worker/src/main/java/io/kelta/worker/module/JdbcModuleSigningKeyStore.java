package io.kelta.worker.module;

import io.kelta.runtime.module.ModuleSigningKey;
import io.kelta.runtime.module.ModuleSigningKeyStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-backed implementation of {@link ModuleSigningKeyStore}.
 *
 * <p>Every statement filters on {@code tenant_id} even though the table carries an RLS
 * {@code tenant_isolation} policy. The policy's companion {@code admin_bypass} widens reads to
 * every tenant whenever {@code app.current_tenant_id} is unset — which is exactly the state
 * module loading runs in at pod startup — so RLS cannot be the only thing scoping these reads.
 *
 * @since 1.0.0
 */
public class JdbcModuleSigningKeyStore implements ModuleSigningKeyStore {

    private static final String COLUMNS =
            "id, tenant_id, label, algorithm, public_key_pem, fingerprint, active, "
            + "retired_at, created_at, created_by";

    private final JdbcTemplate jdbcTemplate;

    public JdbcModuleSigningKeyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public List<ModuleSigningKey> findActiveByTenant(String tenantId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM tenant_module_signing_key "
                + "WHERE tenant_id = ? AND active = true ORDER BY created_at DESC",
                JdbcModuleSigningKeyStore::mapRow, tenantId);
    }

    @Override
    public List<ModuleSigningKey> findByTenant(String tenantId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM tenant_module_signing_key "
                + "WHERE tenant_id = ? ORDER BY active DESC, created_at DESC",
                JdbcModuleSigningKeyStore::mapRow, tenantId);
    }

    @Override
    public Optional<ModuleSigningKey> findById(String tenantId, String id) {
        List<ModuleSigningKey> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM tenant_module_signing_key "
                + "WHERE tenant_id = ? AND id = ?",
                JdbcModuleSigningKeyStore::mapRow, tenantId, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public String create(ModuleSigningKey key) {
        String id = key.id() != null ? key.id() : UUID.randomUUID().toString();
        jdbcTemplate.update("""
            INSERT INTO tenant_module_signing_key
                (id, tenant_id, label, algorithm, public_key_pem, fingerprint, active,
                 created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            id, key.tenantId(), key.label(), key.algorithm(), key.publicKeyPem(),
            key.fingerprint(), key.active(), key.createdBy());
        return id;
    }

    @Override
    public boolean setActive(String tenantId, String id, boolean active, String updatedBy) {
        // retired_at tracks the transition, so re-activating a key clears it rather than
        // leaving a retirement date on a live key.
        int updated = jdbcTemplate.update("""
            UPDATE tenant_module_signing_key
               SET active = ?,
                   retired_at = CASE WHEN ? THEN NULL ELSE NOW() END,
                   updated_by = ?,
                   updated_at = NOW()
             WHERE tenant_id = ? AND id = ?
            """,
            active, active, updatedBy, tenantId, id);
        return updated > 0;
    }

    @Override
    public boolean delete(String tenantId, String id) {
        return jdbcTemplate.update(
                "DELETE FROM tenant_module_signing_key WHERE tenant_id = ? AND id = ?",
                tenantId, id) > 0;
    }

    @Override
    public int countModulesSignedBy(String tenantId, String fingerprint) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_module "
                + "WHERE tenant_id = ? AND jar_signature_key_fingerprint = ?",
                Integer.class, tenantId, fingerprint);
        return count == null ? 0 : count;
    }

    private static ModuleSigningKey mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ModuleSigningKey(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("label"),
                rs.getString("algorithm"),
                rs.getString("public_key_pem"),
                rs.getString("fingerprint"),
                rs.getBoolean("active"),
                instant(rs.getTimestamp("retired_at")),
                instant(rs.getTimestamp("created_at")),
                rs.getString("created_by"));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
