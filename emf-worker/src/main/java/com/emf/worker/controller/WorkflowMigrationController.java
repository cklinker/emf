package com.emf.worker.controller;

import com.emf.runtime.flow.WorkflowRuleToFlowMigrator;
import com.emf.runtime.workflow.WorkflowRuleData;
import com.emf.worker.repository.WorkflowMigrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin endpoint for migrating workflow rules to flows.
 * <p>
 * This controller reads workflow rules from the database and converts
 * them to flow definitions using {@link WorkflowRuleToFlowMigrator}.
 * Migrated flows are created as inactive drafts.
 */
@RestController
@RequestMapping("/api/admin/migrate-workflow-rules")
@ConditionalOnBean(WorkflowRuleToFlowMigrator.class)
public class WorkflowMigrationController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowMigrationController.class);

    private final WorkflowMigrationRepository repository;
    private final ObjectMapper objectMapper;
    private final WorkflowRuleToFlowMigrator migrator;

    public WorkflowMigrationController(WorkflowMigrationRepository repository,
                                        ObjectMapper objectMapper,
                                        WorkflowRuleToFlowMigrator migrator) {
        this.repository = repository;
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

        List<WorkflowRuleData> rules = repository.findRulesForTenant(tenantId);

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

        Optional<WorkflowRuleData> ruleOpt = repository.findRuleById(ruleId);
        if (ruleOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WorkflowRuleData rule = ruleOpt.get();
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

    private String createFlow(String tenantId, WorkflowRuleData rule,
                               WorkflowRuleToFlowMigrator.MigrationResult migrationResult) {
        String flowId = UUID.randomUUID().toString();
        try {
            String definitionJson = objectMapper.writeValueAsString(migrationResult.definition());
            String triggerConfigJson = objectMapper.writeValueAsString(migrationResult.triggerConfig());

            repository.createFlow(flowId, tenantId,
                    "[Migrated] " + rule.name(),
                    "Migrated from workflow rule: " + rule.id(),
                    migrationResult.flowType(),
                    definitionJson, triggerConfigJson);

            log.info("Created flow {} from workflow rule {} for tenant {}",
                    flowId, rule.id(), tenantId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create flow: " + e.getMessage(), e);
        }
        return flowId;
    }
}
