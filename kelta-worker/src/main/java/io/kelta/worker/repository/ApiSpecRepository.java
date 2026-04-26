package io.kelta.worker.repository;

import io.kelta.runtime.module.integration.api.ApiSpec;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC repository for the {@code api_spec} table. Reads route through
 * {@code TenantContext.callWithTenant(...)} to engage the V127 RLS policy.
 */
@Repository
public class ApiSpecRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ApiSpecRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<ApiSpec> listActive(String tenantId) {
        return jdbcTemplate.query(
            """
            SELECT id, tenant_id, name, description, spec_version, api_title, api_version,
                   base_url, servers, security_schemes, source_type, source_url,
                   raw_spec, raw_format, parsed_spec, spec_hash, revision, is_active,
                   last_imported_at
            FROM api_spec
            WHERE tenant_id = ? AND is_active = TRUE
            ORDER BY api_title NULLS LAST, name
            """,
            specRowMapper(), tenantId);
    }

    public Optional<ApiSpec> findById(String id, String tenantId) {
        try {
            ApiSpec spec = jdbcTemplate.queryForObject(
                """
                SELECT id, tenant_id, name, description, spec_version, api_title, api_version,
                       base_url, servers, security_schemes, source_type, source_url,
                       raw_spec, raw_format, parsed_spec, spec_hash, revision, is_active,
                       last_imported_at
                FROM api_spec
                WHERE id = ? AND tenant_id = ?
                """,
                specRowMapper(), id, tenantId);
            return Optional.ofNullable(spec);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ApiSpec> findByName(String name, String tenantId) {
        List<ApiSpec> rows = jdbcTemplate.query(
            """
            SELECT id, tenant_id, name, description, spec_version, api_title, api_version,
                   base_url, servers, security_schemes, source_type, source_url,
                   raw_spec, raw_format, parsed_spec, spec_hash, revision, is_active,
                   last_imported_at
            FROM api_spec
            WHERE name = ? AND tenant_id = ?
            """,
            specRowMapper(), name, tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void insert(ApiSpec spec, String createdBy) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
            """
            INSERT INTO api_spec (
                id, tenant_id, name, description, spec_version, api_title, api_version,
                base_url, servers, security_schemes, source_type, source_url,
                raw_spec, raw_format, parsed_spec, spec_hash, revision, is_active,
                last_imported_at, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?,
                    ?, ?::jsonb, ?::jsonb, ?, ?,
                    ?, ?, ?::jsonb, ?, ?, ?,
                    ?, ?, ?, ?)
            """,
            spec.id(), spec.tenantId(), spec.name(), spec.description(),
            spec.specVersion(), spec.apiTitle(), spec.apiVersion(),
            spec.baseUrl(), writeJson(spec.servers()), writeJson(spec.securitySchemes()),
            spec.sourceType(), spec.sourceUrl(),
            spec.rawSpec(), spec.rawFormat(), writeJson(spec.parsedSpec()),
            spec.specHash(), spec.revision(), spec.active(),
            Timestamp.from(spec.lastImportedAt()), createdBy, now, now);
    }

    public void updateForReimport(ApiSpec spec, String updatedBy) {
        jdbcTemplate.update(
            """
            UPDATE api_spec
            SET    description = ?,
                   spec_version = ?,
                   api_title = ?,
                   api_version = ?,
                   base_url = ?,
                   servers = ?::jsonb,
                   security_schemes = ?::jsonb,
                   source_type = ?,
                   source_url = ?,
                   raw_spec = ?,
                   raw_format = ?,
                   parsed_spec = ?::jsonb,
                   spec_hash = ?,
                   revision = ?,
                   is_active = TRUE,
                   last_imported_at = ?,
                   updated_by = ?,
                   updated_at = ?
            WHERE  id = ? AND tenant_id = ?
            """,
            spec.description(), spec.specVersion(), spec.apiTitle(), spec.apiVersion(),
            spec.baseUrl(), writeJson(spec.servers()), writeJson(spec.securitySchemes()),
            spec.sourceType(), spec.sourceUrl(),
            spec.rawSpec(), spec.rawFormat(), writeJson(spec.parsedSpec()),
            spec.specHash(), spec.revision(),
            Timestamp.from(spec.lastImportedAt()), updatedBy, Timestamp.from(Instant.now()),
            spec.id(), spec.tenantId());
    }

    public void softDelete(String id, String tenantId, String updatedBy) {
        jdbcTemplate.update(
            """
            UPDATE api_spec
            SET    is_active = FALSE, updated_by = ?, updated_at = ?
            WHERE  id = ? AND tenant_id = ?
            """,
            updatedBy, Timestamp.from(Instant.now()), id, tenantId);
    }

    // -----------------------------------------------------------------------

    private RowMapper<ApiSpec> specRowMapper() {
        return (rs, rowNum) -> new ApiSpec(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("spec_version"),
            rs.getString("api_title"),
            rs.getString("api_version"),
            rs.getString("base_url"),
            readJson(rs.getString("servers")),
            readJson(rs.getString("security_schemes")),
            rs.getString("source_type"),
            rs.getString("source_url"),
            rs.getString("raw_spec"),
            rs.getString("raw_format"),
            readJson(rs.getString("parsed_spec")),
            rs.getString("spec_hash"),
            rs.getInt("revision"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("last_imported_at").toInstant());
    }

    private String writeJson(JsonNode node) {
        if (node == null) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
