package com.emf.runtime.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * JDBC-backed implementation of {@link WorkflowStore}.
 * <p>
 * Queries the workflow system tables directly using {@link JdbcTemplate}.
 * This avoids the overhead of the {@link com.emf.runtime.query.QueryEngine}
 * (validation, events, before-save hooks) which is unnecessary for internal
 * workflow engine operations.
 * <p>
 * Table mappings:
 * <ul>
 *   <li>{@code workflow_rule} - workflow rules</li>
 *   <li>{@code workflow_action} - actions associated with rules</li>
 *   <li>{@code workflow_execution_log} - execution logs</li>
 *   <li>{@code workflow_action_log} - per-action execution logs</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class JdbcWorkflowStore implements WorkflowStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowStore.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkflowStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public List<WorkflowRuleData> findActiveRules(String tenantId, String collectionName, String triggerType) {
        String sql = """
            SELECT wr.id, wr.tenant_id, wr.collection_id, c.name AS collection_name,
                   wr.name, wr.description, wr.active, wr.trigger_type, wr.filter_formula,
                   wr.re_evaluate_on_update, wr.execution_order, wr.error_handling,
                   wr.trigger_fields, wr.cron_expression, wr.timezone,
                   wr.last_scheduled_run, wr.execution_mode
            FROM workflow_rule wr
            JOIN collection c ON wr.collection_id = c.id
            WHERE wr.tenant_id = ? AND c.name = ? AND wr.trigger_type = ? AND wr.active = true
            ORDER BY wr.execution_order ASC
            """;

        List<WorkflowRuleData> rules = new ArrayList<>(jdbcTemplate.query(sql,
            (rs, rowNum) -> mapRule(rs), tenantId, collectionName, triggerType));

        // Load actions for each rule
        for (int i = 0; i < rules.size(); i++) {
            WorkflowRuleData rule = rules.get(i);
            List<WorkflowActionData> actions = loadActionsForRule(rule.id());
            rules.set(i, withActions(rule, actions));
        }

        return rules;
    }

    @Override
    public Optional<WorkflowRuleData> findRuleById(String ruleId) {
        String sql = """
            SELECT wr.id, wr.tenant_id, wr.collection_id, c.name AS collection_name,
                   wr.name, wr.description, wr.active, wr.trigger_type, wr.filter_formula,
                   wr.re_evaluate_on_update, wr.execution_order, wr.error_handling,
                   wr.trigger_fields, wr.cron_expression, wr.timezone,
                   wr.last_scheduled_run, wr.execution_mode
            FROM workflow_rule wr
            JOIN collection c ON wr.collection_id = c.id
            WHERE wr.id = ? AND wr.active = true
            """;

        List<WorkflowRuleData> rules = jdbcTemplate.query(sql,
            (rs, rowNum) -> mapRule(rs), ruleId);

        if (rules.isEmpty()) {
            return Optional.empty();
        }

        WorkflowRuleData rule = rules.get(0);
        List<WorkflowActionData> actions = loadActionsForRule(rule.id());
        return Optional.of(withActions(rule, actions));
    }

    @Override
    public List<WorkflowRuleData> findScheduledRules() {
        String sql = """
            SELECT wr.id, wr.tenant_id, wr.collection_id, c.name AS collection_name,
                   wr.name, wr.description, wr.active, wr.trigger_type, wr.filter_formula,
                   wr.re_evaluate_on_update, wr.execution_order, wr.error_handling,
                   wr.trigger_fields, wr.cron_expression, wr.timezone,
                   wr.last_scheduled_run, wr.execution_mode
            FROM workflow_rule wr
            JOIN collection c ON wr.collection_id = c.id
            WHERE wr.trigger_type = 'SCHEDULED' AND wr.active = true
            ORDER BY wr.execution_order ASC
            """;

        List<WorkflowRuleData> rules = new ArrayList<>(jdbcTemplate.query(sql,
            (rs, rowNum) -> mapRule(rs)));

        for (int i = 0; i < rules.size(); i++) {
            WorkflowRuleData rule = rules.get(i);
            List<WorkflowActionData> actions = loadActionsForRule(rule.id());
            rules.set(i, withActions(rule, actions));
        }

        return rules;
    }

    @Override
    public String createExecutionLog(String tenantId, String workflowRuleId,
                                       String recordId, String triggerType) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();

        jdbcTemplate.update("""
            INSERT INTO workflow_execution_log
                (id, tenant_id, workflow_rule_id, record_id, trigger_type, status,
                 actions_executed, executed_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'EXECUTING', 0, ?, ?, ?)
            """,
            id, tenantId, workflowRuleId, recordId, triggerType,
            Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

        return id;
    }

    @Override
    public void updateExecutionLog(String executionLogId, String status,
                                     int actionsExecuted, String errorMessage, int durationMs) {
        jdbcTemplate.update("""
            UPDATE workflow_execution_log
            SET status = ?, actions_executed = ?, error_message = ?,
                duration_ms = ?, updated_at = ?
            WHERE id = ?
            """,
            status, actionsExecuted, errorMessage, durationMs,
            Timestamp.from(Instant.now()), executionLogId);
    }

    @Override
    public void createActionLog(String executionLogId, String actionId, String actionType,
                                  String status, String errorMessage,
                                  String inputSnapshot, String outputSnapshot,
                                  int durationMs, int attemptNumber) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();

        jdbcTemplate.update("""
            INSERT INTO workflow_action_log
                (id, execution_log_id, action_id, action_type, status, error_message,
                 input_snapshot, output_snapshot, duration_ms, attempt_number,
                 executed_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
            """,
            id, executionLogId, actionId, actionType, status, errorMessage,
            inputSnapshot, outputSnapshot, durationMs, attemptNumber,
            Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public void updateLastScheduledRun(String ruleId, Instant timestamp) {
        jdbcTemplate.update("""
            UPDATE workflow_rule SET last_scheduled_run = ?, updated_at = ? WHERE id = ?
            """,
            Timestamp.from(timestamp), Timestamp.from(Instant.now()), ruleId);
    }

    @Override
    public boolean claimScheduledRule(String ruleId, Instant expectedLastRun, Instant newTimestamp) {
        int rowsUpdated;
        if (expectedLastRun == null) {
            rowsUpdated = jdbcTemplate.update("""
                UPDATE workflow_rule
                SET last_scheduled_run = ?, updated_at = ?
                WHERE id = ? AND last_scheduled_run IS NULL
                """,
                Timestamp.from(newTimestamp), Timestamp.from(Instant.now()), ruleId);
        } else {
            rowsUpdated = jdbcTemplate.update("""
                UPDATE workflow_rule
                SET last_scheduled_run = ?, updated_at = ?
                WHERE id = ? AND last_scheduled_run = ?
                """,
                Timestamp.from(newTimestamp), Timestamp.from(Instant.now()),
                ruleId, Timestamp.from(expectedLastRun));
        }
        return rowsUpdated > 0;
    }

    // ---- Internal helpers ----

    private List<WorkflowActionData> loadActionsForRule(String ruleId) {
        return jdbcTemplate.query("""
            SELECT id, action_type, execution_order, config, active,
                   retry_count, retry_delay_seconds, retry_backoff
            FROM workflow_action
            WHERE workflow_rule_id = ?
            ORDER BY execution_order ASC
            """,
            (rs, rowNum) -> new WorkflowActionData(
                rs.getString("id"),
                rs.getString("action_type"),
                rs.getInt("execution_order"),
                rs.getString("config"),
                rs.getBoolean("active"),
                rs.getInt("retry_count"),
                rs.getInt("retry_delay_seconds"),
                rs.getString("retry_backoff")
            ),
            ruleId);
    }

    private WorkflowRuleData mapRule(ResultSet rs) throws SQLException {
        return new WorkflowRuleData(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("collection_id"),
            rs.getString("collection_name"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBoolean("active"),
            rs.getString("trigger_type"),
            rs.getString("filter_formula"),
            rs.getBoolean("re_evaluate_on_update"),
            rs.getInt("execution_order"),
            rs.getString("error_handling"),
            parseTriggerFields(rs.getString("trigger_fields")),
            rs.getString("cron_expression"),
            rs.getString("timezone"),
            rs.getTimestamp("last_scheduled_run") != null
                ? rs.getTimestamp("last_scheduled_run").toInstant() : null,
            rs.getString("execution_mode"),
            List.of() // Actions are loaded separately
        );
    }

    private List<String> parseTriggerFields(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse trigger fields JSON: {}", e.getMessage());
            return null;
        }
    }

    private WorkflowRuleData withActions(WorkflowRuleData rule, List<WorkflowActionData> actions) {
        return new WorkflowRuleData(
            rule.id(), rule.tenantId(), rule.collectionId(), rule.collectionName(),
            rule.name(), rule.description(), rule.active(), rule.triggerType(),
            rule.filterFormula(), rule.reEvaluateOnUpdate(), rule.executionOrder(),
            rule.errorHandling(), rule.triggerFields(), rule.cronExpression(),
            rule.timezone(), rule.lastScheduledRun(), rule.executionMode(), actions);
    }
}
