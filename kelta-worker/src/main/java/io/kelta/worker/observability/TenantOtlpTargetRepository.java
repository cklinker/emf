package io.kelta.worker.observability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC access to {@code tenant_otlp_target} (Rec 7). RLS scopes reads/writes to the
 * current tenant, so callers must run under that tenant's {@code TenantContext}.
 */
@Repository
public class TenantOtlpTargetRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TenantOtlpTargetRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** A stored export target: endpoint, headers, and whether export is enabled. */
    public record StoredTarget(String endpoint, Map<String, String> headers, boolean enabled) {}

    public Optional<StoredTarget> find(String tenantId) {
        List<StoredTarget> rows = jdbcTemplate.query(
                "SELECT endpoint, headers, enabled FROM tenant_otlp_target WHERE tenant_id = ?",
                (rs, n) -> new StoredTarget(
                        rs.getString("endpoint"),
                        parseHeaders(rs.getString("headers")),
                        rs.getBoolean("enabled")),
                tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void upsert(String tenantId, String endpoint, Map<String, String> headers, boolean enabled) {
        jdbcTemplate.update("""
                INSERT INTO tenant_otlp_target (tenant_id, endpoint, headers, enabled, created_at, updated_at)
                VALUES (?, ?, ?::jsonb, ?, NOW(), NOW())
                ON CONFLICT (tenant_id) DO UPDATE
                  SET endpoint = EXCLUDED.endpoint, headers = EXCLUDED.headers,
                      enabled = EXCLUDED.enabled, updated_at = NOW()
                """, tenantId, endpoint, writeHeaders(headers), enabled);
    }

    public void delete(String tenantId) {
        jdbcTemplate.update("DELETE FROM tenant_otlp_target WHERE tenant_id = ?", tenantId);
    }

    private Map<String, String> parseHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            Map<String, String> result = new LinkedHashMap<>();
            parsed.forEach((k, v) -> {
                if (v != null) {
                    result.put(k, v.toString());
                }
            });
            return result;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private String writeHeaders(Map<String, String> headers) {
        try {
            return objectMapper.writeValueAsString(headers != null ? headers : Map.of());
        } catch (RuntimeException e) {
            return "{}";
        }
    }
}
