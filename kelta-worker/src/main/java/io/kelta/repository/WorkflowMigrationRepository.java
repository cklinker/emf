package io.kelta.worker.repository;

import io.kelta.runtime.workflow.WorkflowActionData;
import io.kelta.runtime.workflow.WorkflowRuleData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for workflow rule migration database queries.
 *
 * <p>Encapsulates SQL access for loading workflow rules and actions,
 * and creating migrated flow records.
 */
@Repository
public class WorkflowMigrationRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowMigrationRepository.class);

    private static final String SELECT_RULES_FOR_TENANT = """
            SELECT wr.id, wr.tenant_id, wr.collection_id, c.name as collection_name,
                   wr.name, wr.description, wr.active, wr.trigger_type,
                   wr.filter_formula, wr.re_evaluate_on_update, wr.execution_order,
                   wr.error_handling, wr.trigger_fields, wr.cron_expression,
                   wr.timezone, wr.last_scheduled_run, wr.execution_mode
            FROM workflow_rule wr
            LEFT JOIN collection c ON wr.collection_id = c.id
            WHERE wr.tenant_id = ?
            ORDER BY wr.execution_order
            """;

    private static final String SELECT_RULE_BY_ID = """
            SELECT wr.id, wr.tenant_id, wr.collection_id, c.name as collection_name,
                   wr.name, wr.description, wr.active, wr.trigger_type,
                   wr.filter_formula, wr.re_evaluate_on_update, wr.execution_order,
                   wr.error_handling, wr.trigger_fields, wr.cron_expression,
                   wr.timezone, wr.last_scheduled_run, wr.execution_mode
            FROM workflow_rule wr
            LEFT JOIN collection c ON wr.collection_id = c.id
            WHERE wr.id = ?
            """;

    private static final String SELECT_ACTIONS_FOR_RULE = """
            SELECT * FROM workflow_action WHERE workflow_rule_id = ? ORDER BY execution_order
            """;

    private static final String INSERT_FLOW = """
            INSERT INTO flow (id, tenant_id, name, description, flow_type, active,
                              definition, trigger_config, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, false, ?::jsonb, ?::jsonb, 'system-migration', NOW(), NOW())
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowMigrationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<WorkflowRuleData> findRulesForTenant(String tenantId) {
        return jdbcTemplate.query(SELECT_RULES_FOR_TENANT, (rs, rowNum) -> {
            String ruleId = rs.getString("id");
            List<WorkflowActionData> actions = findActionsForRule(ruleId);
            List<String> triggerFields = parseTriggerFields(rs.getString("trigger_fields"));
            Instant lastRun = rs.getTimestamp("last_scheduled_run") != null
                    ? rs.getTimestamp("last_scheduled_run").toInstant() : null;

            return new WorkflowRuleData(
                    ruleId,
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
                    triggerFields,
                    rs.getString("cron_expression"),
                    rs.getString("timezone"),
                    lastRun,
                    rs.getString("execution_mode"),
                    actions
            );
        }, tenantId);
    }

    public Optional<WorkflowRuleData> findRuleById(String ruleId) {
        return jdbcTemplate.query(SELECT_RULE_BY_ID, (rs, rowNum) -> {
            List<WorkflowActionData> actions = findActionsForRule(ruleId);
            List<String> triggerFields = parseTriggerFields(rs.getString("trigger_fields"));
            Instant lastRun = rs.getTimestamp("last_scheduled_run") != null
                    ? rs.getTimestamp("last_scheduled_run").toInstant() : null;

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
                    triggerFields,
                    rs.getString("cron_expression"),
                    rs.getString("timezone"),
                    lastRun,
                    rs.getString("execution_mode"),
                    actions
            );
        }, ruleId).stream().findFirst();
    }

    public List<WorkflowActionData> findActionsForRule(String ruleId) {
        return jdbcTemplate.query(SELECT_ACTIONS_FOR_RULE,
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
                ruleId
        );
    }

    private List<String> parseTriggerFields(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse trigger_fields JSON: {}", e.getMessage());
            return List.of();
        }
    }

    public void createFlow(String flowId, String tenantId, String name, String description,
                            String flowType, String definitionJson, String triggerConfigJson) {
        jdbcTemplate.update(INSERT_FLOW,
                flowId, tenantId, name, description, flowType,
                definitionJson, triggerConfigJson);
    }
}
