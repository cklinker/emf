package io.kelta.worker.repository;

import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for connected app data access and audit trail persistence.
 *
 * @since 1.0.0
 */
@Repository
public class ConnectedAppRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConnectedAppRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -----------------------------------------------------------------------
    // Connected App Queries
    // -----------------------------------------------------------------------

    public Optional<Map<String, Object>> findActiveByClientId(String clientId) {
        var results = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, name, client_id, scopes, rate_limit_per_hour, ip_restrictions, active " +
                        "FROM connected_app WHERE client_id = ? AND active = true",
                clientId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<Map<String, Object>> findByIdAndTenant(String appId, String tenantId) {
        var results = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, name, client_id, scopes, rate_limit_per_hour, ip_restrictions, active " +
                        "FROM connected_app WHERE id = ? AND tenant_id = ?",
                appId, tenantId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void updateLastUsedAt(String appId) {
        jdbcTemplate.update(
                "UPDATE connected_app SET last_used_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), appId
        );
    }

    // -----------------------------------------------------------------------
    // Token Queries
    // -----------------------------------------------------------------------

    public List<Map<String, Object>> findTokensByAppId(String appId) {
        return jdbcTemplate.queryForList(
                "SELECT id, connected_app_id, scopes, issued_at, expires_at, revoked " +
                        "FROM connected_app_token WHERE connected_app_id = ? ORDER BY issued_at DESC",
                appId
        );
    }

    public String createToken(String appId, String scopes, Instant expiresAt) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO connected_app_token (id, connected_app_id, scopes, issued_at, expires_at, revoked) " +
                        "VALUES (?, ?, ?, ?, ?, false)",
                id, appId, scopes, Timestamp.from(Instant.now()),
                expiresAt != null ? Timestamp.from(expiresAt) : null
        );
        return id;
    }

    public int revokeToken(String tokenId, String appId) {
        return jdbcTemplate.update(
                "UPDATE connected_app_token SET revoked = true WHERE id = ? AND connected_app_id = ?",
                tokenId, appId
        );
    }

    // -----------------------------------------------------------------------
    // Audit Trail
    // -----------------------------------------------------------------------

    public void insertAuditRecord(String tenantId, String appId, String action,
                                   String details, String performedBy) {
        jdbcTemplate.update(
                "INSERT INTO connected_app_audit (id, tenant_id, connected_app_id, action, details, performed_by, created_at) " +
                        "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)",
                UUID.randomUUID().toString(), tenantId, appId, action,
                details, performedBy, Timestamp.from(Instant.now())
        );
    }

    public List<Map<String, Object>> findAuditByAppId(String appId, int limit) {
        return jdbcTemplate.queryForList(
                "SELECT id, action, details, performed_by, created_at " +
                        "FROM connected_app_audit WHERE connected_app_id = ? ORDER BY created_at DESC LIMIT ?",
                appId, limit
        );
    }
}
