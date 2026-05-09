package io.kelta.runtime.module.integration.handlers;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionHandler;
import io.kelta.runtime.workflow.ActionResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Action handler that executes a raw SQL statement against the tenant's
 * PostgreSQL schema and returns either a result set or an affected-row count.
 *
 * <p>Config format:
 * <pre>
 * {
 *   "sql": "SELECT id, name FROM orders WHERE customer_id = '${$.customerId}'",
 *   "maxRows": 1000
 * }
 * </pre>
 *
 * <p>Variable substitution into {@code sql} is performed upstream by
 * {@code StateDataResolver.resolveDeep} before this handler runs — the SQL
 * arrives with all {@code ${...}} placeholders already replaced.
 *
 * <p>Tenant scope is enforced by setting {@code search_path} to the current
 * tenant's schema (plus {@code public}) inside a transaction, so unqualified
 * table names cannot reach another tenant's data.
 *
 * <p>Output for SELECT-like statements:
 * <pre>
 * { "records": [...], "rowCount": N, "columns": [...] }
 * </pre>
 *
 * <p>Output for DML / DDL:
 * <pre>
 * { "rowsAffected": N, "success": true }
 * </pre>
 */
public class SqlQueryActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryActionHandler.class);

    private static final int DEFAULT_MAX_ROWS = 1000;
    private static final int HARD_MAX_ROWS = 10_000;

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public SqlQueryActionHandler(ObjectMapper objectMapper,
                                 JdbcTemplate jdbcTemplate,
                                 TransactionTemplate transactionTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public String getActionTypeKey() {
        return "SQL_QUERY";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                    context.actionConfigJson(), new TypeReference<>() {});

            String sql = config.get("sql") instanceof String s ? s.trim() : null;
            if (sql == null || sql.isEmpty()) {
                return ActionResult.failure("sql is required");
            }

            int maxRows = clampMaxRows(config.get("maxRows"));

            String tenantSlug = TenantContext.getSlug();
            if (tenantSlug == null || tenantSlug.isBlank()) {
                return ActionResult.failure(
                        "Tenant slug is not bound — SQL_QUERY cannot run outside a tenant scope");
            }

            boolean returnsResultSet = returnsResultSet(sql);

            Map<String, Object> output = transactionTemplate.execute(status -> {
                jdbcTemplate.execute("SET LOCAL search_path = " + quoteIdent(tenantSlug) + ", public");

                if (returnsResultSet) {
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
                    if (rows.size() > maxRows) {
                        rows = new ArrayList<>(rows.subList(0, maxRows));
                    }

                    List<String> columns = rows.isEmpty()
                            ? List.of()
                            : new ArrayList<>(rows.get(0).keySet());

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("records", rows);
                    result.put("rowCount", rows.size());
                    result.put("columns", columns);
                    return result;
                } else {
                    int rowsAffected = jdbcTemplate.update(sql);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("rowsAffected", rowsAffected);
                    result.put("success", true);
                    return result;
                }
            });

            log.info("SQL_QUERY executed: tenantSlug={}, returnsResultSet={}, output={}",
                    tenantSlug, returnsResultSet, summarize(output));

            return ActionResult.success(output);
        } catch (DataAccessException dae) {
            String message = dae.getMostSpecificCause() != null
                    ? dae.getMostSpecificCause().getMessage()
                    : dae.getMessage();
            log.warn("SQL_QUERY failed: {}", message);
            return ActionResult.failure("SQL error: " + message);
        } catch (Exception e) {
            log.error("SQL_QUERY action failed: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                    configJson, new TypeReference<>() {});

            Object sql = config.get("sql");
            if (!(sql instanceof String s) || s.isBlank()) {
                throw new IllegalArgumentException("Config must contain non-blank 'sql'");
            }

            Object maxRows = config.get("maxRows");
            if (maxRows instanceof Number n) {
                int v = n.intValue();
                if (v < 1 || v > HARD_MAX_ROWS) {
                    throw new IllegalArgumentException(
                            "maxRows must be between 1 and " + HARD_MAX_ROWS);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }

    /**
     * True if executing this SQL is expected to produce a result set.
     * Recognises SELECT, WITH, SHOW, EXPLAIN, VALUES, TABLE, and any DML
     * that has a {@code RETURNING} clause.
     */
    static boolean returnsResultSet(String sql) {
        String stripped = stripLeadingCommentsAndWhitespace(sql).toUpperCase();
        if (stripped.startsWith("SELECT")
                || stripped.startsWith("WITH")
                || stripped.startsWith("SHOW")
                || stripped.startsWith("EXPLAIN")
                || stripped.startsWith("VALUES")
                || stripped.startsWith("TABLE ")) {
            return true;
        }
        // INSERT/UPDATE/DELETE ... RETURNING
        return stripped.contains(" RETURNING ") || stripped.contains("\nRETURNING ");
    }

    private static String stripLeadingCommentsAndWhitespace(String sql) {
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i + 2);
                if (end < 0) return "";
                i = end + 1;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) return "";
                i = end + 2;
            } else {
                break;
            }
        }
        return sql.substring(i);
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    private static int clampMaxRows(Object value) {
        if (value instanceof Number n) {
            int v = n.intValue();
            if (v < 1) return 1;
            if (v > HARD_MAX_ROWS) return HARD_MAX_ROWS;
            return v;
        }
        return DEFAULT_MAX_ROWS;
    }

    private static String summarize(Map<String, Object> output) {
        if (output == null) return "null";
        if (output.containsKey("rowCount")) {
            return "rowCount=" + output.get("rowCount");
        }
        if (output.containsKey("rowsAffected")) {
            return "rowsAffected=" + output.get("rowsAffected");
        }
        return output.toString();
    }
}
