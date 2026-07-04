package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JDBC access to the {@code script} table for standalone (HTTP-invoked) script execution.
 *
 * <p>Reads are tenant-scoped by Postgres RLS via the request-bound {@code TenantContext};
 * this repository never adds an explicit {@code tenant_id} filter for that reason (RLS is
 * the single source of truth, consistent with the rest of the worker).
 *
 * @since 1.0.0
 */
@Repository
public class ScriptRepository {

    private final JdbcTemplate jdbcTemplate;

    public ScriptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Loads a script by id (RLS scopes it to the current tenant).
     *
     * @param id the script id
     * @return the script, or empty if not found for this tenant
     */
    public Optional<Script> findById(String id) {
        return jdbcTemplate.query(
                "SELECT id, name, script_type, language, source_code, active, required_permission "
                        + "FROM script WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return Optional.<Script>empty();
                    }
                    return Optional.of(new Script(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("script_type"),
                            rs.getString("language"),
                            rs.getString("source_code"),
                            rs.getBoolean("active"),
                            rs.getString("required_permission")));
                },
                id);
    }

    /**
     * Records a single execution in {@code script_execution_log} for auditability.
     *
     * @param tenantId    the tenant id
     * @param scriptId    the executed script id
     * @param status      one of SUCCESS / FAILURE / TIMEOUT / GOVERNOR_LIMIT
     * @param durationMs  wall-clock execution time
     * @param recordId    the record context id, if any
     * @param errorMessage the failure message, if any
     */
    public void insertExecutionLog(String tenantId, String scriptId, String status,
                                   long durationMs, String recordId, String errorMessage) {
        jdbcTemplate.update(
                "INSERT INTO script_execution_log "
                        + "(id, tenant_id, script_id, status, trigger_type, record_id, duration_ms, error_message) "
                        + "VALUES (?, ?, ?, ?, 'API_ENDPOINT', ?, ?, ?)",
                UUID.randomUUID().toString(), tenantId, scriptId, status,
                recordId, (int) Math.min(durationMs, Integer.MAX_VALUE), errorMessage);
    }

    /**
     * A script row relevant to standalone execution.
     *
     * @param id         the script id
     * @param name       the human-readable name
     * @param scriptType one of the {@code chk_script_type} enum values
     * @param language   the script language (e.g. {@code javascript})
     * @param source     the source code
     * @param active     whether the script is active
     */
    public record Script(String id, String name, String scriptType, String language,
                         String source, boolean active, String requiredPermission) {
        public Script(String id, String name, String scriptType, String language,
                      String source, boolean active) {
            this(id, name, scriptType, language, source, active, null);
        }
    }
}
