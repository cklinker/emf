package com.emf.worker.controller;

import com.emf.runtime.flow.WorkflowRuleToFlowMigrator;
import com.emf.runtime.workflow.WorkflowActionData;
import com.emf.runtime.workflow.WorkflowRuleData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Admin endpoint for migrating workflow rules to flows.
 * <p>
 * This controller reads workflow rules from the database and converts
 * them to flow definitions using {@link WorkflowRuleToFlowMigrator}.
 * Migrated flows are created as inactive drafts.
 */
@RestController
@RequestMapping("/api/collections/admin/migrate-workflow-rules")
@ConditionalOnBean(WorkflowRuleToFlowMigrator.class)
public class WorkflowMigrationController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowMigrationController.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final WorkflowRuleToFlowMigrator migrator;

    public WorkflowMigrationController(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                        WorkflowRuleToFlowMigrator migrator) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.migrator = migrator;
    }

    /**
     * Migrate all workflow rules for a tenant to flows.
     *
     * @param tenantId the tenant whose rules to migrate
     * @param dryRun   if true, validate without persisting
     * @return migration report
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> migrateAll(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        log.info("Starting workflow rule migration for tenant {} (dryRun={})", tenantId, dryRun);

        List<WorkflowRuleData> rules = loadRulesForTenant(tenantId);

        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;

        for (WorkflowRuleData rule : rules) {
            try {
                var migrationResult = migrator.migrate(rule);
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("ruleId", rule.id());
                report.put("ruleName", rule.name());
                report.put("flowType", migrationResult.flowType());
                report.put("status", "SUCCESS");
                report.put("warnings", migrationResult.warnings());

                if (!dryRun) {
                    String flowId = createFlow(tenantId, rule, migrationResult);
                    report.put("flowId", flowId);
                }

                results.add(report);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to migrate rule {}: {}", rule.id(), e.getMessage(), e);
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("ruleId", rule.id());
                report.put("ruleName", rule.name());
                report.put("status", "ERROR");
                report.put("error", e.getMessage());
                results.add(report);
                errorCount++;
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenantId", tenantId);
        response.put("dryRun", dryRun);
        response.put("totalRules", rules.size());
        response.put("successCount", successCount);
        response.put("errorCount", errorCount);
        response.put("results", results);

        log.info("Workflow rule migration complete for tenant {}: {} success, {} errors",
                tenantId, successCount, errorCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Migrate a single workflow rule to a flow.
     */
    @PostMapping("/{ruleId}")
    public ResponseEntity<Map<String, Object>> migrateOne(
            @PathVariable String ruleId,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        WorkflowRuleData rule = loadRuleById(ruleId);
        if (rule == null) {
            return ResponseEntity.notFound().build();
        }

        var migrationResult = migrator.migrate(rule);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ruleId", rule.id());
        report.put("ruleName", rule.name());
        report.put("flowType", migrationResult.flowType());
        report.put("triggerConfig", migrationResult.triggerConfig());
        report.put("definition", migrationResult.definition());
        report.put("warnings", migrationResult.warnings());
        report.put("dryRun", dryRun);

        if (!dryRun) {
            String flowId = createFlow(rule.tenantId(), rule, migrationResult);
            report.put("flowId", flowId);
            report.put("status", "CREATED");
        } else {
            report.put("status", "VALIDATED");
        }

        return ResponseEntity.ok(report);
    }

    private List<WorkflowRuleData> loadRulesForTenant(String tenantId) {
        String sql = """
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

        return jdbc.query(sql, (rs, rowNum) -> {
            String ruleId = rs.getString("id");
            List<WorkflowActionData> actions = loadActionsForRule(ruleId);
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

    private WorkflowRuleData loadRuleById(String ruleId) {
        String sql = """
            SELECT wr.id, wr.tenant_id, wr.collection_id, c.name as collection_name,
                   wr.name, wr.description, wr.active, wr.trigger_type,
                   wr.filter_formula, wr.re_evaluate_on_update, wr.execution_order,
                   wr.error_handling, wr.trigger_fields, wr.cron_expression,
                   wr.timezone, wr.last_scheduled_run, wr.execution_mode
            FROM workflow_rule wr
            LEFT JOIN collection c ON wr.collection_id = c.id
            WHERE wr.id = ?
            """;

        return jdbc.query(sql, (rs, rowNum) -> {
            List<WorkflowActionData> actions = loadActionsForRule(ruleId);
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
        }, ruleId).stream().findFirst().orElse(null);
    }

    private List<WorkflowActionData> loadActionsForRule(String ruleId) {
        return jdbc.query(
                "SELECT * FROM workflow_action WHERE workflow_rule_id = ? ORDER BY execution_order",
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

    private String createFlow(String tenantId, WorkflowRuleData rule,
                               WorkflowRuleToFlowMigrator.MigrationResult migrationResult) {
        String flowId = UUID.randomUUID().toString();
        try {
            String definitionJson = objectMapper.writeValueAsString(migrationResult.definition());
            String triggerConfigJson = objectMapper.writeValueAsString(migrationResult.triggerConfig());

            jdbc.update("""
                INSERT INTO flow (id, tenant_id, name, description, flow_type, active,
                                  definition, trigger_config, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, false, ?::jsonb, ?::jsonb, 'system-migration', NOW(), NOW())
                """,
                    flowId, tenantId,
                    "[Migrated] " + rule.name(),
                    "Migrated from workflow rule: " + rule.id(),
                    migrationResult.flowType(),
                    definitionJson, triggerConfigJson
            );

            log.info("Created flow {} from workflow rule {} for tenant {}",
                    flowId, rule.id(), tenantId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create flow: " + e.getMessage(), e);
        }
        return flowId;
    }

    private List<String> parseTriggerFields(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
