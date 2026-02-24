package com.emf.runtime.workflow;

import java.time.Instant;
import java.util.List;

/**
 * Immutable data representation of a workflow rule with its actions.
 * <p>
 * This record replaces the JPA-based {@code WorkflowRule} entity from the control plane,
 * allowing the workflow engine to operate without JPA dependencies. It carries all
 * information needed to evaluate and execute a workflow rule.
 *
 * @param id                  the unique rule ID
 * @param tenantId            the tenant that owns this rule
 * @param collectionId        the collection ID this rule applies to
 * @param collectionName      the collection name this rule applies to
 * @param name                the human-readable rule name
 * @param description         optional description
 * @param active              whether the rule is active
 * @param triggerType         the trigger type (ON_CREATE, ON_UPDATE, ON_DELETE, ON_CREATE_OR_UPDATE, BEFORE_CREATE, BEFORE_UPDATE, SCHEDULED)
 * @param filterFormula       optional formula that must evaluate to true for the rule to fire
 * @param reEvaluateOnUpdate  whether to re-evaluate on updates (reserved for future use)
 * @param executionOrder      the order in which this rule executes relative to other rules
 * @param errorHandling       error handling mode ("STOP_ON_ERROR" or "CONTINUE_ON_ERROR")
 * @param triggerFields       optional list of field names; for UPDATE triggers, at least one must be in changedFields
 * @param cronExpression      optional cron expression for SCHEDULED rules
 * @param timezone            optional timezone for scheduled rules (defaults to UTC)
 * @param lastScheduledRun    timestamp of the last scheduled execution (null if never)
 * @param executionMode       execution mode ("SEQUENTIAL")
 * @param actions             the ordered list of actions for this rule
 *
 * @since 1.0.0
 */
public record WorkflowRuleData(
    String id,
    String tenantId,
    String collectionId,
    String collectionName,
    String name,
    String description,
    boolean active,
    String triggerType,
    String filterFormula,
    boolean reEvaluateOnUpdate,
    int executionOrder,
    String errorHandling,
    List<String> triggerFields,
    String cronExpression,
    String timezone,
    Instant lastScheduledRun,
    String executionMode,
    List<WorkflowActionData> actions
) {

    /**
     * Returns the active actions sorted by execution order.
     */
    public List<WorkflowActionData> activeActions() {
        if (actions == null) {
            return List.of();
        }
        return actions.stream()
            .filter(WorkflowActionData::active)
            .sorted((a, b) -> Integer.compare(a.executionOrder(), b.executionOrder()))
            .toList();
    }

    /**
     * Returns true if error handling is STOP_ON_ERROR.
     */
    public boolean stopOnError() {
        return "STOP_ON_ERROR".equals(errorHandling);
    }
}
