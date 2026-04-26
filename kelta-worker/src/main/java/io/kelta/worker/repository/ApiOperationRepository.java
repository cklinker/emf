package io.kelta.worker.repository;

import io.kelta.runtime.module.integration.api.ApiOperation;
import io.kelta.runtime.module.integration.api.ParsedSpec;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JDBC repository for the {@code api_operation} table. Each {@link ApiOperation}
 * is a row keyed by {@code (spec_id, synthetic_op_id)}. Re-imports use
 * {@link #syncOperations} which adds new operations, updates changed ones,
 * and soft-deletes ones that disappeared (sets {@code deprecated=TRUE}) so
 * existing flows that reference them keep working until the user updates
 * them.
 */
@Repository
public class ApiOperationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ApiOperationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<ApiOperation> listBySpec(String tenantId, String specId) {
        return jdbcTemplate.query(
            """
            SELECT id, tenant_id, spec_id, operation_id, synthetic_op_id, http_method,
                   path_template, summary, description, tags, parameters_schema,
                   request_body_schema, response_schemas, security_required, deprecated
            FROM api_operation
            WHERE tenant_id = ? AND spec_id = ?
            ORDER BY tags NULLS LAST, path_template, http_method
            """,
            operationRowMapper(), tenantId, specId);
    }

    public Optional<ApiOperation> findOperation(String tenantId, String specId,
                                                 String syntheticOpId) {
        try {
            ApiOperation op = jdbcTemplate.queryForObject(
                """
                SELECT id, tenant_id, spec_id, operation_id, synthetic_op_id, http_method,
                       path_template, summary, description, tags, parameters_schema,
                       request_body_schema, response_schemas, security_required, deprecated
                FROM api_operation
                WHERE tenant_id = ? AND spec_id = ? AND synthetic_op_id = ?
                """,
                operationRowMapper(), tenantId, specId, syntheticOpId);
            return Optional.ofNullable(op);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Searches operations for the picker UI. Filters are AND-ed; pass
     * {@code null} or empty string to skip a filter.
     */
    public List<ApiOperation> search(String tenantId, String query, String method,
                                      String specId, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, tenant_id, spec_id, operation_id, synthetic_op_id, http_method,
                   path_template, summary, description, tags, parameters_schema,
                   request_body_schema, response_schemas, security_required, deprecated
            FROM api_operation
            WHERE tenant_id = ?
            """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(tenantId);

        if (query != null && !query.isBlank()) {
            sql.append(" AND search_text ILIKE ? ");
            args.add("%" + query + "%");
        }
        if (method != null && !method.isBlank()) {
            sql.append(" AND http_method = ? ");
            args.add(method.toUpperCase());
        }
        if (specId != null && !specId.isBlank()) {
            sql.append(" AND spec_id = ? ");
            args.add(specId);
        }
        sql.append(" ORDER BY path_template, http_method LIMIT ? ");
        args.add(Math.max(1, Math.min(limit, 200)));

        return jdbcTemplate.query(sql.toString(), operationRowMapper(), args.toArray());
    }

    /**
     * Re-points the operation set for a spec to {@code parsedOps}:
     * <ul>
     *   <li>New synthetic ops → INSERT</li>
     *   <li>Existing synthetic ops → UPDATE in place</li>
     *   <li>Existing synthetic ops not in {@code parsedOps} → mark
     *       {@code deprecated=TRUE} (NOT hard-deleted, since flow definitions
     *       reference them by id)</li>
     * </ul>
     *
     * @return diff summary: counts of added / changed / removed (deprecated)
     */
    public OperationSyncResult syncOperations(String tenantId, String specId,
                                               List<ParsedSpec.ParsedOperation> parsedOps) {
        Map<String, ApiOperation> existing = new LinkedHashMap<>();
        for (ApiOperation op : listBySpec(tenantId, specId)) {
            existing.put(op.syntheticOpId(), op);
        }

        Set<String> seen = new HashSet<>();
        int added = 0;
        int changed = 0;

        for (ParsedSpec.ParsedOperation parsed : parsedOps) {
            seen.add(parsed.syntheticOpId());
            ApiOperation prior = existing.get(parsed.syntheticOpId());
            if (prior == null) {
                jdbcTemplate.update(
                    """
                    INSERT INTO api_operation (
                        id, tenant_id, spec_id, operation_id, synthetic_op_id, http_method,
                        path_template, summary, description, tags, parameters_schema,
                        request_body_schema, response_schemas, security_required,
                        deprecated, search_text)
                    VALUES (?, ?, ?, ?, ?, ?,
                            ?, ?, ?, ?::jsonb, ?::jsonb,
                            ?::jsonb, ?::jsonb, ?::jsonb,
                            ?, ?)
                    """,
                    UUID.randomUUID().toString(), tenantId, specId,
                    parsed.operationId(), parsed.syntheticOpId(), parsed.httpMethod(),
                    parsed.pathTemplate(), parsed.summary(), parsed.description(),
                    writeJson(parsed.tags()), writeJson(parsed.parametersSchema()),
                    writeJson(parsed.requestBodySchema()), writeJson(parsed.responseSchemas()),
                    writeJson(parsed.securityRequired()),
                    parsed.deprecated(), parsed.searchText());
                added++;
            } else {
                jdbcTemplate.update(
                    """
                    UPDATE api_operation
                    SET    operation_id = ?, http_method = ?, path_template = ?,
                           summary = ?, description = ?, tags = ?::jsonb,
                           parameters_schema = ?::jsonb, request_body_schema = ?::jsonb,
                           response_schemas = ?::jsonb, security_required = ?::jsonb,
                           deprecated = ?, search_text = ?
                    WHERE  id = ?
                    """,
                    parsed.operationId(), parsed.httpMethod(), parsed.pathTemplate(),
                    parsed.summary(), parsed.description(),
                    writeJson(parsed.tags()), writeJson(parsed.parametersSchema()),
                    writeJson(parsed.requestBodySchema()), writeJson(parsed.responseSchemas()),
                    writeJson(parsed.securityRequired()),
                    parsed.deprecated(), parsed.searchText(),
                    prior.id());
                changed++;
            }
        }

        int removed = 0;
        for (Map.Entry<String, ApiOperation> entry : existing.entrySet()) {
            if (!seen.contains(entry.getKey()) && !entry.getValue().deprecated()) {
                jdbcTemplate.update(
                    "UPDATE api_operation SET deprecated = TRUE WHERE id = ?",
                    entry.getValue().id());
                removed++;
            }
        }

        return new OperationSyncResult(added, changed, removed);
    }

    public record OperationSyncResult(int added, int changed, int removed) {
    }

    // -----------------------------------------------------------------------

    private RowMapper<ApiOperation> operationRowMapper() {
        return (rs, rowNum) -> new ApiOperation(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("spec_id"),
            rs.getString("operation_id"),
            rs.getString("synthetic_op_id"),
            rs.getString("http_method"),
            rs.getString("path_template"),
            rs.getString("summary"),
            rs.getString("description"),
            readJson(rs.getString("tags")),
            readJson(rs.getString("parameters_schema")),
            readJson(rs.getString("request_body_schema")),
            readJson(rs.getString("response_schemas")),
            readJson(rs.getString("security_required")),
            rs.getBoolean("deprecated"));
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
