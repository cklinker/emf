package com.emf.runtime.module.core.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Stub action handler for triggering another workflow rule as a subflow.
 *
 * <p>This is a placeholder implementation that will be replaced in Phase 3 when
 * the WorkflowEngine is moved to runtime-core. The stub logs the attempted trigger
 * and returns a failure indicating the feature is not yet available.
 *
 * @since 1.0.0
 */
public class TriggerFlowActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(TriggerFlowActionHandler.class);

    private final ObjectMapper objectMapper;

    public TriggerFlowActionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

            log.warn("TRIGGER_FLOW action invoked (stub) for target rule '{}' from rule '{}'. " +
                "Full implementation requires WorkflowEngine (Phase 3).",
                targetRuleId, context.workflowRuleId());

            return ActionResult.failure("TRIGGER_FLOW requires WorkflowEngine (Phase 3)");
        } catch (Exception e) {
            log.error("Failed to parse trigger flow config: {}", e.getMessage(), e);
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
