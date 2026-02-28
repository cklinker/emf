package com.emf.worker.module;

import com.emf.runtime.module.ModuleStore;
import com.emf.runtime.module.TenantModuleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * JDBC-backed implementation of {@link ModuleStore}.
 *
 * @since 1.0.0
 */
public class JdbcModuleStore implements ModuleStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcModuleStore.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcModuleStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public String createModule(TenantModuleData data) {
        String id = data.id() != null ? data.id() : UUID.randomUUID().toString();
        jdbcTemplate.update("""
            INSERT INTO tenant_module
                (id, tenant_id, module_id, name, version, description, source_url,
                 jar_checksum, jar_size_bytes, module_class, manifest, status,
                 installed_by, installed_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, NOW(), NOW())
            """,
            id, data.tenantId(), data.moduleId(), data.name(), data.version(),
            data.description(), data.sourceUrl(), data.jarChecksum(), data.jarSizeBytes(),
            data.moduleClass(), data.manifest(), data.status(), data.installedBy());
        return id;
    }

    @Override
    public void createActions(List<TenantModuleData.TenantModuleActionData> actions) {
        for (var action : actions) {
            String id = action.id() != null ? action.id() : UUID.randomUUID().toString();
            jdbcTemplate.update("""
                INSERT INTO tenant_module_action
                    (id, tenant_module_id, action_key, name, category, description,
                     config_schema, input_schema, output_schema, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, NOW())
                """,
                id, action.tenantModuleId(), action.actionKey(), action.name(),
                action.category(), action.description(), action.configSchema(),
                action.inputSchema(), action.outputSchema());
        }
    }

    @Override
    public Optional<TenantModuleData> findByTenantAndModuleId(String tenantId, String moduleId) {
        List<TenantModuleData> results = jdbcTemplate.query(
            "SELECT * FROM tenant_module WHERE tenant_id = ? AND module_id = ?",
            this::mapModule, tenantId, moduleId);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        TenantModuleData module = results.get(0);
        return Optional.of(withActions(module));
    }

    @Override
    public Optional<TenantModuleData> findById(String id) {
        List<TenantModuleData> results = jdbcTemplate.query(
            "SELECT * FROM tenant_module WHERE id = ?",
            this::mapModule, id);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(withActions(results.get(0)));
    }

    @Override
    public List<TenantModuleData> findByTenant(String tenantId) {
        List<TenantModuleData> modules = jdbcTemplate.query(
            "SELECT * FROM tenant_module WHERE tenant_id = ? ORDER BY installed_at DESC",
            this::mapModule, tenantId);
        return modules.stream().map(this::withActions).toList();
    }

    @Override
    public List<TenantModuleData> findByTenantAndStatus(String tenantId, String status) {
        List<TenantModuleData> modules = jdbcTemplate.query(
            "SELECT * FROM tenant_module WHERE tenant_id = ? AND status = ? ORDER BY installed_at DESC",
            this::mapModule, tenantId, status);
        return modules.stream().map(this::withActions).toList();
    }

    @Override
    public void updateStatus(String id, String status) {
        jdbcTemplate.update(
            "UPDATE tenant_module SET status = ?, updated_at = NOW() WHERE id = ?",
            status, id);
    }

    @Override
    public void deleteModule(String id) {
        // Actions are cascade-deleted via FK
        jdbcTemplate.update("DELETE FROM tenant_module WHERE id = ?", id);
    }

    @Override
    public List<TenantModuleData> findAllActive() {
        List<TenantModuleData> modules = jdbcTemplate.query(
            "SELECT * FROM tenant_module WHERE status = 'ACTIVE' ORDER BY tenant_id, module_id",
            this::mapModule);
        return modules.stream().map(this::withActions).toList();
    }

    private TenantModuleData withActions(TenantModuleData module) {
        List<TenantModuleData.TenantModuleActionData> actions = jdbcTemplate.query(
            "SELECT * FROM tenant_module_action WHERE tenant_module_id = ? ORDER BY action_key",
            this::mapAction, module.id());
        return new TenantModuleData(
            module.id(), module.tenantId(), module.moduleId(), module.name(),
            module.version(), module.description(), module.sourceUrl(),
            module.jarChecksum(), module.jarSizeBytes(), module.moduleClass(),
            module.manifest(), module.status(), module.installedBy(),
            module.installedAt(), module.updatedAt(), actions);
    }

    private TenantModuleData mapModule(ResultSet rs, int rowNum) throws SQLException {
        return new TenantModuleData(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("module_id"),
            rs.getString("name"),
            rs.getString("version"),
            rs.getString("description"),
            rs.getString("source_url"),
            rs.getString("jar_checksum"),
            rs.getObject("jar_size_bytes") != null ? rs.getLong("jar_size_bytes") : null,
            rs.getString("module_class"),
            rs.getString("manifest"),
            rs.getString("status"),
            rs.getString("installed_by"),
            toInstant(rs.getTimestamp("installed_at")),
            toInstant(rs.getTimestamp("updated_at")),
            List.of() // Actions loaded separately
        );
    }

    private TenantModuleData.TenantModuleActionData mapAction(ResultSet rs, int rowNum)
            throws SQLException {
        return new TenantModuleData.TenantModuleActionData(
            rs.getString("id"),
            rs.getString("tenant_module_id"),
            rs.getString("action_key"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getString("config_schema"),
            rs.getString("input_schema"),
            rs.getString("output_schema")
        );
    }

    private Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
