package io.kelta.worker.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Queries audit data from PostgreSQL tables (setup_audit_trail, security_audit_log).
 * Replaces OpenSearch-based audit queries.
 */
@Service
public class AuditQueryService {

    private final JdbcTemplate jdbcTemplate;

    public AuditQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Search audit events across setup_audit_trail and security_audit_log tables.
     */
    public ObservabilityQueryService.SearchResult searchAudit(String tenantId, String auditType,
                                                              String action, String userId,
                                                              Instant start, Instant end,
                                                              int page, int size) {
        if ("setup".equals(auditType)) {
            return searchSetupAudit(tenantId, action, userId, start, end, page, size);
        } else if ("security".equals(auditType) || "login".equals(auditType)) {
            return searchSecurityAudit(tenantId, auditType, userId, start, end, page, size);
        } else {
            // Search both and merge results
            var setupResult = searchSetupAudit(tenantId, action, userId, start, end, page, size);
            var securityResult = searchSecurityAudit(tenantId, null, userId, start, end, page, size);

            List<Map<String, Object>> combined = new ArrayList<>(setupResult.hits());
            combined.addAll(securityResult.hits());

            // Sort by timestamp descending
            combined.sort((a, b) -> {
                Instant tsA = extractTimestamp(a);
                Instant tsB = extractTimestamp(b);
                return tsB.compareTo(tsA);
            });

            // Apply pagination to combined results
            long totalHits = setupResult.totalHits() + securityResult.totalHits();
            int fromIndex = Math.min(page * size, combined.size());
            int toIndex = Math.min(fromIndex + size, combined.size());
            List<Map<String, Object>> paged = combined.subList(fromIndex, toIndex);

            return new ObservabilityQueryService.SearchResult(paged, totalHits);
        }
    }

    private ObservabilityQueryService.SearchResult searchSetupAudit(
            String tenantId, String action, String userId,
            Instant start, Instant end, int page, int size) {

        StringBuilder sql = new StringBuilder(
                "SELECT id, tenant_id, user_id, action, section, entity_type, entity_id, entity_name, " +
                        "old_value, new_value, timestamp AS \"@timestamp\", 'setup' AS audit_type " +
                        "FROM setup_audit_trail WHERE 1=1");
        StringBuilder countSql = new StringBuilder(
                "SELECT COUNT(*) FROM setup_audit_trail WHERE 1=1");

        List<Object> params = new ArrayList<>();
        List<Object> countParams = new ArrayList<>();

        appendFilter(sql, countSql, params, countParams, " AND tenant_id = ?", tenantId);
        appendFilter(sql, countSql, params, countParams, " AND action = ?", action);
        appendFilter(sql, countSql, params, countParams, " AND user_id = ?", userId);

        if (start != null) {
            String clause = " AND timestamp >= ?";
            sql.append(clause);
            countSql.append(clause);
            Timestamp ts = Timestamp.from(start);
            params.add(ts);
            countParams.add(ts);
        }
        if (end != null) {
            String clause = " AND timestamp <= ?";
            sql.append(clause);
            countSql.append(clause);
            Timestamp ts = Timestamp.from(end);
            params.add(ts);
            countParams.add(ts);
        }

        sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        Long totalHits = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());
        List<Map<String, Object>> hits = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        return new ObservabilityQueryService.SearchResult(hits, totalHits != null ? totalHits : 0);
    }

    private ObservabilityQueryService.SearchResult searchSecurityAudit(
            String tenantId, String auditType, String userId,
            Instant start, Instant end, int page, int size) {

        StringBuilder sql = new StringBuilder(
                "SELECT id, tenant_id, event_type, event_category, actor_user_id AS user_id, " +
                        "actor_email, target_type, target_id, target_name, details, " +
                        "ip_address, user_agent, created_at AS \"@timestamp\", " +
                        "CASE WHEN event_type = 'LOGIN_SUCCESS' THEN 'login' ELSE 'security' END AS audit_type " +
                        "FROM security_audit_log WHERE 1=1");
        StringBuilder countSql = new StringBuilder(
                "SELECT COUNT(*) FROM security_audit_log WHERE 1=1");

        List<Object> params = new ArrayList<>();
        List<Object> countParams = new ArrayList<>();

        appendFilter(sql, countSql, params, countParams, " AND tenant_id = ?", tenantId);
        appendFilter(sql, countSql, params, countParams, " AND actor_user_id = ?", userId);

        if ("login".equals(auditType)) {
            String clause = " AND event_type = 'LOGIN_SUCCESS'";
            sql.append(clause);
            countSql.append(clause);
        }

        if (start != null) {
            String clause = " AND created_at >= ?";
            sql.append(clause);
            countSql.append(clause);
            Timestamp ts = Timestamp.from(start);
            params.add(ts);
            countParams.add(ts);
        }
        if (end != null) {
            String clause = " AND created_at <= ?";
            sql.append(clause);
            countSql.append(clause);
            Timestamp ts = Timestamp.from(end);
            params.add(ts);
            countParams.add(ts);
        }

        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        Long totalHits = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());
        List<Map<String, Object>> hits = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        return new ObservabilityQueryService.SearchResult(hits, totalHits != null ? totalHits : 0);
    }

    private void appendFilter(StringBuilder sql, StringBuilder countSql,
                              List<Object> params, List<Object> countParams,
                              String clause, String value) {
        if (value != null && !value.isEmpty()) {
            sql.append(clause);
            countSql.append(clause);
            params.add(value);
            countParams.add(value);
        }
    }

    private Instant extractTimestamp(Map<String, Object> row) {
        Object ts = row.get("@timestamp");
        if (ts instanceof Timestamp t) return t.toInstant();
        if (ts instanceof Instant i) return i;
        return Instant.EPOCH;
    }
}
