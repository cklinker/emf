package com.emf.runtime.flow;

import com.emf.runtime.workflow.WorkflowActionData;
import com.emf.runtime.workflow.WorkflowRuleData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a {@link WorkflowRuleData} and its actions into a Flow definition.
 * <p>
 * Each workflow rule becomes a Flow with:
 * <ul>
 *   <li>A flow_type derived from the rule's trigger type</li>
 *   <li>A trigger_config derived from the rule's trigger configuration</li>
 *   <li>A definition JSON with sequential Task states for each action</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class WorkflowRuleToFlowMigrator {

    /**
     * Result of migrating a single workflow rule to a flow definition.
     *
     * @param flowType      the flow type (RECORD_TRIGGERED, SCHEDULED, AUTOLAUNCHED)
     * @param triggerConfig the trigger configuration JSON map
     * @param definition    the flow definition JSON map
     * @param warnings      any warnings generated during migration
     */
    public record MigrationResult(
        String flowType,
        Map<String, Object> triggerConfig,
        Map<String, Object> definition,
        List<String> warnings
    ) {}

    /**
     * Converts a workflow rule to a flow definition.
     *
     * @param rule the workflow rule with its actions
     * @return the migration result containing flow type, trigger config, and definition
     */
    public MigrationResult migrate(WorkflowRuleData rule) {
        List<String> warnings = new ArrayList<>();

        String flowType = mapFlowType(rule.triggerType());
        Map<String, Object> triggerConfig = buildTriggerConfig(rule, warnings);
        Map<String, Object> definition = buildDefinition(rule, warnings);

        return new MigrationResult(flowType, triggerConfig, definition, warnings);
    }

    /**
     * Maps a workflow rule trigger type to a flow type.
     */
    String mapFlowType(String triggerType) {
        if (triggerType == null) {
            return "AUTOLAUNCHED";
        }
        return switch (triggerType) {
            case "ON_CREATE", "ON_UPDATE", "ON_DELETE", "ON_CREATE_OR_UPDATE",
                 "BEFORE_CREATE", "BEFORE_UPDATE" -> "RECORD_TRIGGERED";
            case "SCHEDULED" -> "SCHEDULED";
            case "MANUAL" -> "AUTOLAUNCHED";
            default -> "AUTOLAUNCHED";
        };
    }

    /**
     * Builds the trigger_config JSON map from the rule's trigger settings.
     */
    Map<String, Object> buildTriggerConfig(WorkflowRuleData rule, List<String> warnings) {
        Map<String, Object> config = new LinkedHashMap<>();

        switch (rule.triggerType() != null ? rule.triggerType() : "") {
            case "ON_CREATE" -> {
                config.put("collection", rule.collectionName());
                config.put("events", List.of("CREATED"));
            }
            case "ON_UPDATE" -> {
                config.put("collection", rule.collectionName());
                config.put("events", List.of("UPDATED"));
                if (rule.triggerFields() != null && !rule.triggerFields().isEmpty()) {
                    config.put("triggerFields", rule.triggerFields());
                }
            }
            case "ON_DELETE" -> {
                config.put("collection", rule.collectionName());
                config.put("events", List.of("DELETED"));
            }
            case "ON_CREATE_OR_UPDATE" -> {
                config.put("collection", rule.collectionName());
                config.put("events", List.of("CREATED", "UPDATED"));
                if (rule.triggerFields() != null && !rule.triggerFields().isEmpty()) {
                    config.put("triggerFields", rule.triggerFields());
                }
            }
            case "BEFORE_CREATE" -> {
                config.put("collection", rule.collectionName());
                config.put("events", List.of("CREATED"));
                config.put("synchronous", true);
            }
            case "BEFORE_UPDATE" -> {
                config.put("collection", rule.collectionName());
                config.put("events", List.of("UPDATED"));
                config.put("synchronous", true);
                if (rule.triggerFields() != null && !rule.triggerFields().isEmpty()) {
                    config.put("triggerFields", rule.triggerFields());
                }
            }
            case "SCHEDULED" -> {
                if (rule.cronExpression() != null && !rule.cronExpression().isBlank()) {
                    config.put("cronExpression", rule.cronExpression());
                }
                if (rule.timezone() != null && !rule.timezone().isBlank()) {
                    config.put("timezone", rule.timezone());
                }
            }
            case "MANUAL" -> {
                // No specific trigger config needed for manual/API-invoked flows
            }
            default -> warnings.add("Unknown trigger type: " + rule.triggerType());
        }

        if (rule.filterFormula() != null && !rule.filterFormula().isBlank()) {
            config.put("filterFormula", rule.filterFormula());
        }

        return config;
    }

    /**
     * Builds the flow definition JSON from the rule's actions.
     * <p>
     * Actions are chained sequentially: Action1 → Action2 → ... → Succeed.
     * If error handling is STOP_ON_ERROR, each Task gets a Catch rule
     * that transitions to a FlowFailed state. If CONTINUE_ON_ERROR,
     * errors are captured in the state data and execution continues.
     */
    Map<String, Object> buildDefinition(WorkflowRuleData rule, List<String> warnings) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("Comment", "Migrated from workflow rule: " + rule.name());

        List<WorkflowActionData> activeActions = rule.activeActions();

        if (activeActions.isEmpty()) {
            warnings.add("Rule has no active actions; flow will contain only a Succeed state");
            definition.put("StartAt", "FlowSucceeded");
            Map<String, Object> states = new LinkedHashMap<>();
            states.put("FlowSucceeded", Map.of("Type", "Succeed"));
            definition.put("States", states);
            return definition;
        }

        Map<String, Object> states = new LinkedHashMap<>();
        boolean stopOnError = rule.stopOnError();

        for (int i = 0; i < activeActions.size(); i++) {
            WorkflowActionData action = activeActions.get(i);
            String stateId = buildStateId(action, i);
            boolean isLast = i == activeActions.size() - 1;
            String nextState = isLast ? "FlowSucceeded" : buildStateId(activeActions.get(i + 1), i + 1);

            Map<String, Object> state = buildTaskState(action, nextState, stopOnError, isLast, warnings);
            states.put(stateId, state);
        }

        // Add terminal states
        states.put("FlowSucceeded", Map.of("Type", "Succeed"));
        if (stopOnError) {
            Map<String, Object> failState = new LinkedHashMap<>();
            failState.put("Type", "Fail");
            failState.put("Error", "ActionFailed");
            failState.put("Cause", "A task action failed and error handling is STOP_ON_ERROR");
            states.put("FlowFailed", failState);
        }

        definition.put("StartAt", buildStateId(activeActions.get(0), 0));
        definition.put("States", states);

        return definition;
    }

    /**
     * Builds a Task state from a workflow action.
     */
    private Map<String, Object> buildTaskState(WorkflowActionData action, String nextState,
                                                boolean stopOnError, boolean isLast,
                                                List<String> warnings) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("Type", "Task");
        state.put("Resource", action.actionType());

        if (isLast && !stopOnError) {
            state.put("Next", "FlowSucceeded");
        } else {
            state.put("Next", nextState);
        }

        // Add retry policy if configured
        if (action.retryCount() > 0) {
            double backoffRate = "EXPONENTIAL".equals(action.retryBackoff()) ? 2.0 : 1.0;
            List<Map<String, Object>> retry = List.of(Map.of(
                "ErrorEquals", List.of("States.ALL"),
                "IntervalSeconds", action.retryDelaySeconds(),
                "MaxAttempts", action.retryCount(),
                "BackoffRate", backoffRate
            ));
            state.put("Retry", retry);
        }

        // Add error handling
        if (stopOnError) {
            List<Map<String, Object>> catchRules = List.of(Map.of(
                "ErrorEquals", List.of("States.ALL"),
                "Next", "FlowFailed",
                "ResultPath", "$.error"
            ));
            state.put("Catch", catchRules);
        } else {
            // Continue on error: capture error and move to next state
            List<Map<String, Object>> catchRules = List.of(Map.of(
                "ErrorEquals", List.of("States.ALL"),
                "Next", isLast ? "FlowSucceeded" : nextState,
                "ResultPath", "$.errors." + sanitizeId(action.actionType() + "_" + action.id())
            ));
            state.put("Catch", catchRules);
        }

        return state;
    }

    /**
     * Builds a state ID from an action (e.g., "Step1_FIELD_UPDATE").
     */
    String buildStateId(WorkflowActionData action, int index) {
        String sanitized = sanitizeId(action.actionType());
        return "Step" + (index + 1) + "_" + sanitized;
    }

    /**
     * Sanitizes a string for use as a state ID.
     */
    private String sanitizeId(String input) {
        if (input == null) return "Unknown";
        return input.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
