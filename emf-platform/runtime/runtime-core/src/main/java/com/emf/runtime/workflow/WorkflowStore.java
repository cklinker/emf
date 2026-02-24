package com.emf.runtime.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data access interface for the workflow engine.
 * <p>
 * Abstracts the underlying storage mechanism (JPA, QueryEngine, in-memory, etc.)
 * so that the {@link WorkflowEngine} can run in any environment without direct
 * database dependencies.
 * <p>
 * Implementations:
 * <ul>
 *   <li>Worker: backed by {@code QueryEngine} querying system collections
 *       ({@code workflow-rules}, {@code workflow-actions}, etc.)</li>
 *   <li>Control-plane: backed by JPA repositories (backward compatibility)</li>
 *   <li>Tests: in-memory implementation</li>
 * </ul>
 *
 * @since 1.0.0
 */
public interface WorkflowStore {

    // ---- Rule queries ----

    /**
     * Finds active workflow rules matching the given tenant, collection, and trigger type.
     * <p>
     * Rules are returned sorted by {@code executionOrder} ascending.
     * Each rule includes its actions (both active and inactive).
     *
     * @param tenantId       the tenant ID
     * @param collectionName the collection name
     * @param triggerType    the trigger type (ON_CREATE, ON_UPDATE, ON_DELETE, etc.)
     * @return the matching rules with their actions, sorted by executionOrder
     */
    List<WorkflowRuleData> findActiveRules(String tenantId, String collectionName, String triggerType);

    /**
     * Finds all active scheduled workflow rules across all tenants.
     * <p>
     * Used by the scheduled workflow executor to poll for due rules.
     *
     * @return all active SCHEDULED rules with their actions
     */
    List<WorkflowRuleData> findScheduledRules();

    // ---- Execution logging ----

    /**
     * Creates an execution log entry and returns its ID.
     * <p>
     * Called at the start of rule evaluation; the log is updated upon completion.
     *
     * @param tenantId      the tenant ID
     * @param workflowRuleId the workflow rule ID
     * @param recordId      the record ID (null for scheduled rules)
     * @param triggerType   the trigger type that fired this rule
     * @return the execution log ID
     */
    String createExecutionLog(String tenantId, String workflowRuleId,
                               String recordId, String triggerType);

    /**
     * Updates an execution log entry upon completion.
     *
     * @param executionLogId  the execution log ID
     * @param status          the final status (SUCCESS, FAILURE, PARTIAL_FAILURE)
     * @param actionsExecuted the number of actions executed
     * @param errorMessage    the error message (null if successful)
     * @param durationMs      the total execution duration in milliseconds
     */
    void updateExecutionLog(String executionLogId, String status,
                             int actionsExecuted, String errorMessage, int durationMs);

    /**
     * Creates an action log entry for an individual action execution.
     *
     * @param executionLogId the parent execution log ID
     * @param actionId       the action ID
     * @param actionType     the action type key
     * @param status         the action status (SUCCESS or FAILURE)
     * @param errorMessage   the error message (null if successful)
     * @param inputSnapshot  JSON string of the action input
     * @param outputSnapshot JSON string of the action output
     * @param durationMs     the action execution duration in milliseconds
     * @param attemptNumber  the attempt number (1 for first try)
     */
    void createActionLog(String executionLogId, String actionId, String actionType,
                          String status, String errorMessage,
                          String inputSnapshot, String outputSnapshot,
                          int durationMs, int attemptNumber);

    // ---- Scheduled rule management ----

    /**
     * Updates the last scheduled run timestamp for a rule.
     * <p>
     * Called after a scheduled rule executes to track when it last ran.
     *
     * @param ruleId the workflow rule ID
     * @param timestamp the execution timestamp
     */
    void updateLastScheduledRun(String ruleId, Instant timestamp);
}
