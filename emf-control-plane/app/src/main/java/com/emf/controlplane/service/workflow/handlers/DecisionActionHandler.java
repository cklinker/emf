package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionHandler;
import com.emf.controlplane.service.workflow.ActionHandlerRegistry;
import com.emf.controlplane.service.workflow.ActionResult;
import com.emf.runtime.formula.FormulaEvaluator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Action handler that implements conditional branching (if/else) in workflows.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "condition": "status == 'High'",
 *   "trueActions": [
 *     {"actionType": "FIELD_UPDATE", "config": {"updates": [{"field": "priority", "value": "Urgent"}]}}
 *   ],
 *   "falseActions": [
 *     {"actionType": "LOG_MESSAGE", "config": {"message": "Not high priority", "level": "INFO"}}
 *   ]
 * }
 * </pre>
 * <p>
 * The condition is evaluated via {@link FormulaEvaluator} against the record data.
 * Based on the result, either {@code trueActions} or {@code falseActions} are executed
 * in sequence. Each nested action receives the same {@link ActionContext} as the parent.
 */
@Component
public class DecisionActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(DecisionActionHandler.class);

    private final ObjectMapper objectMapper;
    private final FormulaEvaluator formulaEvaluator;
    private final ActionHandlerRegistry handlerRegistry;

    public DecisionActionHandler(ObjectMapper objectMapper,
                                  FormulaEvaluator formulaEvaluator,
                                  @Lazy ActionHandlerRegistry handlerRegistry) {
        this.objectMapper = objectMapper;
        this.formulaEvaluator = formulaEvaluator;
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public String getActionTypeKey() {
        return "DECISION";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String condition = (String) config.get("condition");
            if (condition == null || condition.isBlank()) {
                return ActionResult.failure("Decision condition is required");
            }

            // Evaluate condition against record data
            boolean conditionResult;
            try {
                conditionResult = formulaEvaluator.evaluateBoolean(condition, context.data());
            } catch (Exception e) {
                log.warn("Decision condition evaluation failed: {}", e.getMessage());
                return ActionResult.failure("Condition evaluation error: " + e.getMessage());
            }

            log.info("Decision action: condition='{}' evaluated to {} for record={}",
                condition, conditionResult, context.recordId());

            // Get the appropriate action branch
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> branchActions = conditionResult
                ? (List<Map<String, Object>>) config.get("trueActions")
                : (List<Map<String, Object>>) config.get("falseActions");

            if (branchActions == null || branchActions.isEmpty()) {
                log.debug("No actions defined for {} branch of decision", conditionResult ? "true" : "false");
                return ActionResult.success(Map.of(
                    "conditionResult", conditionResult,
                    "branch", conditionResult ? "true" : "false",
                    "actionsExecuted", 0
                ));
            }

            // Execute branch actions in sequence
            int actionsExecuted = 0;
            List<Map<String, Object>> actionResults = new ArrayList<>();

            for (Map<String, Object> actionDef : branchActions) {
                String actionType = (String) actionDef.get("actionType");
                if (actionType == null || actionType.isBlank()) {
                    continue;
                }

                Optional<ActionHandler> handlerOpt = handlerRegistry.getHandler(actionType);
                if (handlerOpt.isEmpty()) {
                    log.warn("No handler for nested action type '{}' in decision", actionType);
                    actionResults.add(Map.of("actionType", actionType, "status", "SKIPPED",
                        "error", "No handler registered"));
                    continue;
                }

                // Build nested action config JSON
                Object nestedConfig = actionDef.get("config");
                String nestedConfigJson = nestedConfig != null
                    ? objectMapper.writeValueAsString(nestedConfig) : "{}";

                // Create child context with the nested action config
                ActionContext childContext = ActionContext.builder()
                    .tenantId(context.tenantId())
                    .collectionId(context.collectionId())
                    .collectionName(context.collectionName())
                    .recordId(context.recordId())
                    .data(context.data())
                    .previousData(context.previousData())
                    .changedFields(context.changedFields())
                    .userId(context.userId())
                    .actionConfigJson(nestedConfigJson)
                    .workflowRuleId(context.workflowRuleId())
                    .executionLogId(context.executionLogId())
                    .resolvedData(context.resolvedData())
                    .build();

                ActionResult nestedResult = handlerOpt.get().execute(childContext);
                actionsExecuted++;

                Map<String, Object> resultEntry = new HashMap<>();
                resultEntry.put("actionType", actionType);
                resultEntry.put("status", nestedResult.successful() ? "SUCCESS" : "FAILURE");
                if (nestedResult.errorMessage() != null) {
                    resultEntry.put("error", nestedResult.errorMessage());
                }
                actionResults.add(resultEntry);

                if (!nestedResult.successful()) {
                    log.warn("Nested action '{}' failed in decision branch: {}",
                        actionType, nestedResult.errorMessage());
                    // Stop on first nested failure
                    return ActionResult.success(Map.of(
                        "conditionResult", conditionResult,
                        "branch", conditionResult ? "true" : "false",
                        "actionsExecuted", actionsExecuted,
                        "actionResults", actionResults,
                        "nestedFailure", true
                    ));
                }
            }

            return ActionResult.success(Map.of(
                "conditionResult", conditionResult,
                "branch", conditionResult ? "true" : "false",
                "actionsExecuted", actionsExecuted,
                "actionResults", actionResults
            ));
        } catch (Exception e) {
            log.error("Failed to execute decision action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("condition") == null) {
                throw new IllegalArgumentException("Config must contain 'condition' expression");
            }

            // At least one branch should have actions
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trueActions = (List<Map<String, Object>>) config.get("trueActions");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> falseActions = (List<Map<String, Object>>) config.get("falseActions");

            if ((trueActions == null || trueActions.isEmpty())
                && (falseActions == null || falseActions.isEmpty())) {
                throw new IllegalArgumentException(
                    "At least one of 'trueActions' or 'falseActions' must have actions defined");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
