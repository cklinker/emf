package com.emf.runtime.module.core.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionResult;
import com.emf.runtime.workflow.WorkflowEngine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Action handler that triggers another workflow rule as a subflow.
 *
 * <p>When a workflow engine is available, this handler looks up the target rule
 * by ID and executes it with the current record context. If no engine is available
 * (e.g., during module testing), it falls back to returning a failure.
 *
 * <p>Config format:
 * <pre>
 * {
 *   "workflowRuleId": "target-rule-uuid"
 * }
 * </pre>
 *
 * @since 1.0.0
 */
public class TriggerFlowActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(TriggerFlowActionHandler.class);

    private final ObjectMapper objectMapper;
    private final WorkflowEngine workflowEngine;

    /**
     * Creates a TriggerFlowActionHandler with a workflow engine for subflow execution.
     *
     * @param objectMapper   the JSON mapper
     * @param workflowEngine the workflow engine (may be null if not yet available)
     */
    public TriggerFlowActionHandler(ObjectMapper objectMapper, WorkflowEngine workflowEngine) {
        this.objectMapper = objectMapper;
        this.workflowEngine = workflowEngine;
    }

    /**
     * Backward-compatible constructor without workflow engine.
     * Creates a stub that returns failure when executed.
     *
     * @param objectMapper the JSON mapper
     */
    public TriggerFlowActionHandler(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    @Override
    public String getActionTypeKey() {
        return "TRIGGER_FLOW";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String targetRuleId = (String) config.get("workflowRuleId");

            if (targetRuleId == null || targetRuleId.isBlank()) {
                return ActionResult.failure("TRIGGER_FLOW: 'workflowRuleId' is required in config");
            }

            if (workflowEngine == null) {
                log.warn("TRIGGER_FLOW action invoked without WorkflowEngine for target rule '{}' " +
                    "from rule '{}'. WorkflowEngine is not available.",
                    targetRuleId, context.workflowRuleId());
                return ActionResult.failure("TRIGGER_FLOW requires WorkflowEngine to be available");
            }

            log.info("TRIGGER_FLOW: triggering rule '{}' from rule '{}' for record '{}'",
                targetRuleId, context.workflowRuleId(), context.recordId());

            String executionLogId = workflowEngine.executeRuleById(
                targetRuleId,
                context.recordId(),
                context.tenantId(),
                context.collectionName(),
                context.data(),
                context.userId()
            );

            if (executionLogId == null) {
                return ActionResult.failure(
                    "TRIGGER_FLOW: target rule '" + targetRuleId + "' not found, inactive, or has no actions");
            }

            Map<String, Object> output = new HashMap<>();
            output.put("targetRuleId", targetRuleId);
            output.put("executionLogId", executionLogId);

            log.info("TRIGGER_FLOW: successfully executed subflow rule '{}' (execution={})",
                targetRuleId, executionLogId);

            return ActionResult.success(output);
        } catch (Exception e) {
            log.error("Failed to execute trigger flow action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("workflowRuleId") == null) {
                throw new IllegalArgumentException("Config must contain 'workflowRuleId'");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
