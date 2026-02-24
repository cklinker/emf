package com.emf.runtime.workflow;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.formula.FormulaEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Core workflow execution engine.
 * <p>
 * Evaluates workflow rules against record change events and executes matching
 * actions via the {@link ActionHandlerRegistry}. This engine is storage-agnostic:
 * all data access goes through the {@link WorkflowStore} interface.
 * <p>
 * Flow:
 * <ol>
 *   <li>Receive {@link RecordChangeEvent} from Kafka (via worker listener)</li>
 *   <li>Query active rules for the tenant + collection + matching trigger type</li>
 *   <li>For each matching rule (ordered by executionOrder):
 *     <ul>
 *       <li>Evaluate filterFormula against record data</li>
 *       <li>If filter passes, execute actions in order via handlers</li>
 *       <li>Log execution results</li>
 *     </ul>
 *   </li>
 *   <li>Handle errors per rule's errorHandling mode (STOP_ON_ERROR / CONTINUE_ON_ERROR)</li>
 * </ol>
 *
 * @since 1.0.0
 */
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowStore store;
    private final ActionHandlerRegistry handlerRegistry;
    private final FormulaEvaluator formulaEvaluator;
    private final ObjectMapper objectMapper;

    public WorkflowEngine(WorkflowStore store,
                           ActionHandlerRegistry handlerRegistry,
                           FormulaEvaluator formulaEvaluator,
                           ObjectMapper objectMapper) {
        this.store = Objects.requireNonNull(store, "WorkflowStore must not be null");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "ActionHandlerRegistry must not be null");
        this.formulaEvaluator = Objects.requireNonNull(formulaEvaluator, "FormulaEvaluator must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    }

    /**
     * Evaluates all matching workflow rules for a record change event.
     *
     * @param event the record change event
     */
    public void evaluate(RecordChangeEvent event) {
        String triggerType = mapChangeTypeToTrigger(event.getChangeType());
        String tenantId = event.getTenantId();
        String collectionName = event.getCollectionName();

        List<WorkflowRuleData> matchingRules = findMatchingRules(tenantId, collectionName, triggerType);
        if (matchingRules.isEmpty()) {
            log.debug("No matching workflow rules for tenant={}, collection={}, trigger={}",
                tenantId, collectionName, triggerType);
            return;
        }

        log.info("Found {} matching workflow rules for collection={}, trigger={}, recordId={}",
            matchingRules.size(), collectionName, triggerType, event.getRecordId());

        for (WorkflowRuleData rule : matchingRules) {
            evaluateRule(rule, event);
        }
    }

    /**
     * Evaluates a single workflow rule against a record change event.
     *
     * @param rule  the workflow rule to evaluate
     * @param event the record change event
     */
    public void evaluateRule(WorkflowRuleData rule, RecordChangeEvent event) {
        long startTime = System.currentTimeMillis();

        // Check trigger fields
        if (!matchesTriggerFields(rule, event)) {
            log.debug("Trigger fields check rejected record {} for rule '{}': " +
                    "none of {} were in changed fields {}",
                event.getRecordId(), rule.name(),
                rule.triggerFields(), event.getChangedFields());
            return;
        }

        // Check filter formula
        if (rule.filterFormula() != null && !rule.filterFormula().isBlank()) {
            try {
                boolean filterPasses = formulaEvaluator.evaluateBoolean(
                    rule.filterFormula(), event.getData());
                if (!filterPasses) {
                    log.debug("Filter formula rejected record {} for rule '{}': {}",
                        event.getRecordId(), rule.name(), rule.filterFormula());
                    return;
                }
            } catch (Exception e) {
                log.warn("Error evaluating filter formula for rule '{}': {}",
                    rule.name(), e.getMessage());
                logFailedExecution(rule, event, "Filter formula error: " + e.getMessage(), startTime);
                return;
            }
        }

        // Execute actions
        List<WorkflowActionData> activeActions = rule.activeActions();
        if (activeActions.isEmpty()) {
            log.debug("Rule '{}' has no active actions, skipping", rule.name());
            return;
        }

        // Create execution log
        String executionLogId = store.createExecutionLog(
            event.getTenantId(), rule.id(), event.getRecordId(),
            mapChangeTypeToTrigger(event.getChangeType()));

        boolean stopOnError = rule.stopOnError();
        int actionsExecuted = 0;
        String overallStatus = "SUCCESS";
        String overallError = null;

        for (WorkflowActionData action : activeActions) {
            long actionStartTime = System.currentTimeMillis();
            ActionResult result = executeActionWithRetry(action, rule, event, executionLogId);
            actionsExecuted++;

            if (!result.successful()) {
                if (stopOnError) {
                    overallStatus = "FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.actionType(), result.errorMessage());
                    log.error("Workflow rule '{}' stopped on error at action {}: {}",
                        rule.name(), action.actionType(), result.errorMessage());
                    break;
                } else {
                    overallStatus = "PARTIAL_FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.actionType(), result.errorMessage());
                    log.warn("Workflow rule '{}' continuing despite error at action {}: {}",
                        rule.name(), action.actionType(), result.errorMessage());
                }
            }
        }

        int durationMs = (int) (System.currentTimeMillis() - startTime);
        store.updateExecutionLog(executionLogId, overallStatus, actionsExecuted, overallError, durationMs);

        log.info("Workflow rule '{}' completed: status={}, actions={}, duration={}ms",
            rule.name(), overallStatus, actionsExecuted, durationMs);
    }

    /**
     * Executes a scheduled workflow rule without a specific record context.
     *
     * @param rule the scheduled workflow rule to execute
     */
    public void executeScheduledRule(WorkflowRuleData rule) {
        long startTime = System.currentTimeMillis();

        log.info("Executing scheduled workflow rule '{}' for tenant={}, collection={}",
            rule.name(), rule.tenantId(), rule.collectionName());

        List<WorkflowActionData> activeActions = rule.activeActions();
        if (activeActions.isEmpty()) {
            log.debug("Scheduled rule '{}' has no active actions, skipping", rule.name());
            return;
        }

        // Create a synthetic event for action context
        RecordChangeEvent syntheticEvent = new RecordChangeEvent(
            UUID.randomUUID().toString(),
            rule.tenantId(), rule.collectionName(), null, ChangeType.CREATED,
            Map.of(), null, List.of(), "system", Instant.now());

        String executionLogId = store.createExecutionLog(
            rule.tenantId(), rule.id(), null, "SCHEDULED");

        boolean stopOnError = rule.stopOnError();
        int actionsExecuted = 0;
        String overallStatus = "SUCCESS";
        String overallError = null;

        for (WorkflowActionData action : activeActions) {
            long actionStartTime = System.currentTimeMillis();
            ActionResult result = executeAction(action, rule, syntheticEvent, executionLogId);
            int actionDurationMs = (int) (System.currentTimeMillis() - actionStartTime);
            actionsExecuted++;

            logActionExecution(executionLogId, action, result, syntheticEvent, actionDurationMs, 1);

            if (!result.successful()) {
                if (stopOnError) {
                    overallStatus = "FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.actionType(), result.errorMessage());
                    log.error("Scheduled rule '{}' stopped on error at action {}: {}",
                        rule.name(), action.actionType(), result.errorMessage());
                    break;
                } else {
                    overallStatus = "PARTIAL_FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.actionType(), result.errorMessage());
                    log.warn("Scheduled rule '{}' continuing despite error at action {}: {}",
                        rule.name(), action.actionType(), result.errorMessage());
                }
            }
        }

        int durationMs = (int) (System.currentTimeMillis() - startTime);
        store.updateExecutionLog(executionLogId, overallStatus, actionsExecuted, overallError, durationMs);

        log.info("Scheduled rule '{}' completed: status={}, actions={}, duration={}ms",
            rule.name(), overallStatus, actionsExecuted, durationMs);
    }

    /**
     * Executes a workflow rule manually for a specific record.
     *
     * @param rule     the workflow rule to execute
     * @param recordId the record ID (may be null)
     * @param userId   the user who triggered the execution
     * @return the execution log ID, or null if no active actions
     */
    public String executeManualRule(WorkflowRuleData rule, String recordId, String userId) {
        long startTime = System.currentTimeMillis();

        log.info("Manual execution of workflow rule '{}' for record={}, user={}",
            rule.name(), recordId, userId);

        List<WorkflowActionData> activeActions = rule.activeActions();
        if (activeActions.isEmpty()) {
            log.debug("Rule '{}' has no active actions, skipping manual execution", rule.name());
            return null;
        }

        RecordChangeEvent syntheticEvent = new RecordChangeEvent(
            UUID.randomUUID().toString(),
            rule.tenantId(), rule.collectionName(), recordId, ChangeType.CREATED,
            Map.of(), null, List.of(), userId != null ? userId : "system", Instant.now());

        String executionLogId = store.createExecutionLog(
            rule.tenantId(), rule.id(), recordId, "MANUAL");

        boolean stopOnError = rule.stopOnError();
        int actionsExecuted = 0;
        String overallStatus = "SUCCESS";
        String overallError = null;

        for (WorkflowActionData action : activeActions) {
            long actionStartTime = System.currentTimeMillis();
            ActionResult result = executeAction(action, rule, syntheticEvent, executionLogId);
            int actionDurationMs = (int) (System.currentTimeMillis() - actionStartTime);
            actionsExecuted++;

            logActionExecution(executionLogId, action, result, syntheticEvent, actionDurationMs, 1);

            if (!result.successful()) {
                if (stopOnError) {
                    overallStatus = "FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.actionType(), result.errorMessage());
                    break;
                } else {
                    overallStatus = "PARTIAL_FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.actionType(), result.errorMessage());
                }
            }
        }

        int durationMs = (int) (System.currentTimeMillis() - startTime);
        store.updateExecutionLog(executionLogId, overallStatus, actionsExecuted, overallError, durationMs);

        log.info("Manual execution of rule '{}' completed: status={}, actions={}, duration={}ms",
            rule.name(), overallStatus, actionsExecuted, durationMs);

        return executionLogId;
    }

    /**
     * Evaluates before-save workflow rules synchronously during record create/update.
     * <p>
     * Only FIELD_UPDATE actions are supported for before-save triggers. Returns accumulated
     * field updates to apply before persist.
     * <p>
     * <strong>Note:</strong> This method does NOT evaluate lifecycle hooks (BeforeSaveHook).
     * Those should be called separately by the caller (e.g., DefaultQueryEngine).
     *
     * @param tenantId       the tenant ID
     * @param collectionName the collection name
     * @param recordId       the record ID (null for creates)
     * @param data           the current record data
     * @param previousData   the previous record data (null for creates)
     * @param changedFields  the list of changed field names (empty for creates)
     * @param userId         the user performing the save
     * @param changeType     "CREATE" or "UPDATE"
     * @return result map with keys: "fieldUpdates" (Map), "rulesEvaluated" (int), "actionsExecuted" (int)
     */
    public Map<String, Object> evaluateBeforeSave(String tenantId, String collectionName,
                                                    String recordId,
                                                    Map<String, Object> data,
                                                    Map<String, Object> previousData,
                                                    List<String> changedFields,
                                                    String userId, String changeType) {
        String triggerType = "CREATE".equals(changeType) ? "BEFORE_CREATE" : "BEFORE_UPDATE";

        log.info("Evaluating before-save workflows: tenant={}, collection={}, trigger={}, record={}",
            tenantId, collectionName, triggerType, recordId);

        Map<String, Object> accumulatedUpdates = new HashMap<>();
        int rulesEvaluated = 0;
        int actionsExecuted = 0;

        List<WorkflowRuleData> rules = store.findActiveRules(tenantId, collectionName, triggerType);

        for (WorkflowRuleData rule : rules) {
            rulesEvaluated++;

            // Check trigger fields for BEFORE_UPDATE
            if ("BEFORE_UPDATE".equals(triggerType)) {
                List<String> triggerFields = rule.triggerFields();
                if (triggerFields != null && !triggerFields.isEmpty()) {
                    if (changedFields == null || changedFields.isEmpty()
                            || Collections.disjoint(triggerFields, changedFields)) {
                        log.debug("Before-save trigger fields check rejected record {} for rule '{}'",
                            recordId, rule.name());
                        continue;
                    }
                }
            }

            // Check filter formula
            if (rule.filterFormula() != null && !rule.filterFormula().isBlank()) {
                try {
                    boolean filterPasses = formulaEvaluator.evaluateBoolean(
                        rule.filterFormula(), data);
                    if (!filterPasses) {
                        log.debug("Before-save filter rejected record {} for rule '{}': {}",
                            recordId, rule.name(), rule.filterFormula());
                        continue;
                    }
                } catch (Exception e) {
                    log.warn("Error evaluating before-save filter for rule '{}': {}",
                        rule.name(), e.getMessage());
                    continue;
                }
            }

            // Execute only FIELD_UPDATE actions
            List<WorkflowActionData> fieldUpdateActions = rule.activeActions().stream()
                .filter(a -> "FIELD_UPDATE".equals(a.actionType()))
                .toList();

            boolean stopOnError = rule.stopOnError();

            for (WorkflowActionData action : fieldUpdateActions) {
                ActionContext context = ActionContext.builder()
                    .tenantId(tenantId)
                    .collectionId(rule.collectionId())
                    .collectionName(collectionName)
                    .recordId(recordId)
                    .data(data)
                    .previousData(previousData)
                    .changedFields(changedFields != null ? changedFields : List.of())
                    .userId(userId != null ? userId : "system")
                    .actionConfigJson(action.config())
                    .workflowRuleId(rule.id())
                    .executionLogId(null)
                    .resolvedData(Map.of())
                    .build();

                Optional<ActionHandler> handlerOpt = handlerRegistry.getHandler("FIELD_UPDATE");
                if (handlerOpt.isEmpty()) {
                    log.error("No FIELD_UPDATE handler registered for before-save rule '{}'", rule.name());
                    continue;
                }

                try {
                    ActionResult result = handlerOpt.get().execute(context);
                    actionsExecuted++;

                    if (result.successful() && result.outputData() != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> updatedFields =
                            (Map<String, Object>) result.outputData().get("updatedFields");
                        if (updatedFields != null) {
                            accumulatedUpdates.putAll(updatedFields);
                        }
                    } else if (!result.successful()) {
                        log.warn("Before-save FIELD_UPDATE failed for rule '{}': {}",
                            rule.name(), result.errorMessage());
                        if (stopOnError) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("Exception in before-save FIELD_UPDATE for rule '{}': {}",
                        rule.name(), e.getMessage(), e);
                    if (stopOnError) {
                        break;
                    }
                }
            }
        }

        log.info("Before-save evaluation complete: {} rules evaluated, {} actions executed, {} field updates",
            rulesEvaluated, actionsExecuted, accumulatedUpdates.size());

        return Map.of(
            "fieldUpdates", accumulatedUpdates,
            "rulesEvaluated", rulesEvaluated,
            "actionsExecuted", actionsExecuted
        );
    }

    // ---- Internal methods ----

    /**
     * Executes an action with retry logic.
     */
    ActionResult executeActionWithRetry(WorkflowActionData action, WorkflowRuleData rule,
                                               RecordChangeEvent event, String executionLogId) {
        int maxAttempts = 1 + Math.max(0, action.retryCount());
        int delaySeconds = Math.max(1, action.retryDelaySeconds());
        boolean exponentialBackoff = "EXPONENTIAL".equals(action.retryBackoff());

        ActionResult lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long attemptStartTime = System.currentTimeMillis();
            lastResult = executeAction(action, rule, event, executionLogId);
            int attemptDurationMs = (int) (System.currentTimeMillis() - attemptStartTime);

            logActionExecution(executionLogId, action, lastResult, event, attemptDurationMs, attempt);

            if (lastResult.successful()) {
                if (attempt > 1) {
                    log.info("Action '{}' in rule '{}' succeeded on attempt {}",
                        action.actionType(), rule.name(), attempt);
                }
                return lastResult;
            }

            if (attempt < maxAttempts) {
                int waitSeconds = exponentialBackoff
                    ? delaySeconds * (int) Math.pow(2, attempt - 1)
                    : delaySeconds;
                log.info("Action '{}' in rule '{}' failed on attempt {}/{}, retrying in {}s: {}",
                    action.actionType(), rule.name(), attempt, maxAttempts,
                    waitSeconds, lastResult.errorMessage());
                try {
                    Thread.sleep(waitSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Retry wait interrupted for action '{}' in rule '{}'",
                        action.actionType(), rule.name());
                    return lastResult;
                }
            }
        }

        if (maxAttempts > 1) {
            log.error("Action '{}' in rule '{}' failed after {} attempts: {}",
                action.actionType(), rule.name(), maxAttempts,
                lastResult != null ? lastResult.errorMessage() : "unknown");
        }

        return lastResult;
    }

    /**
     * Executes a single action within a workflow rule.
     */
    ActionResult executeAction(WorkflowActionData action, WorkflowRuleData rule,
                                      RecordChangeEvent event, String executionLogId) {
        String actionType = action.actionType();
        Optional<ActionHandler> handlerOpt = handlerRegistry.getHandler(actionType);

        if (handlerOpt.isEmpty()) {
            log.error("No handler registered for action type '{}' in rule '{}'",
                actionType, rule.name());
            return ActionResult.failure("No handler registered for action type: " + actionType);
        }

        ActionHandler handler = handlerOpt.get();

        ActionContext context = ActionContext.builder()
            .tenantId(event.getTenantId())
            .collectionId(rule.collectionId())
            .collectionName(event.getCollectionName())
            .recordId(event.getRecordId())
            .data(event.getData())
            .previousData(event.getPreviousData())
            .changedFields(event.getChangedFields())
            .userId(event.getUserId())
            .actionConfigJson(action.config())
            .workflowRuleId(rule.id())
            .executionLogId(executionLogId)
            .resolvedData(Map.of())
            .build();

        try {
            return handler.execute(context);
        } catch (Exception e) {
            log.error("Exception executing action '{}' for rule '{}': {}",
                actionType, rule.name(), e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    /**
     * Maps a ChangeType to a workflow trigger type string.
     */
    String mapChangeTypeToTrigger(ChangeType changeType) {
        return switch (changeType) {
            case CREATED -> "ON_CREATE";
            case UPDATED -> "ON_UPDATE";
            case DELETED -> "ON_DELETE";
        };
    }

    /**
     * Finds matching workflow rules for a tenant, collection, and trigger type.
     * Includes rules that match the specific trigger AND rules with ON_CREATE_OR_UPDATE.
     */
    List<WorkflowRuleData> findMatchingRules(String tenantId, String collectionName, String triggerType) {
        List<WorkflowRuleData> specificRules = store.findActiveRules(tenantId, collectionName, triggerType);

        if ("ON_CREATE".equals(triggerType) || "ON_UPDATE".equals(triggerType)) {
            List<WorkflowRuleData> combinedRules =
                store.findActiveRules(tenantId, collectionName, "ON_CREATE_OR_UPDATE");

            if (!combinedRules.isEmpty()) {
                return Stream.concat(specificRules.stream(), combinedRules.stream())
                    .sorted((a, b) -> Integer.compare(a.executionOrder(), b.executionOrder()))
                    .toList();
            }
        }

        return specificRules;
    }

    /**
     * Checks whether the event's changed fields match the rule's trigger fields.
     */
    boolean matchesTriggerFields(WorkflowRuleData rule, RecordChangeEvent event) {
        List<String> triggerFields = rule.triggerFields();

        if (triggerFields == null || triggerFields.isEmpty()) {
            return true;
        }

        if (event.getChangeType() != ChangeType.UPDATED) {
            return true;
        }

        List<String> changedFields = event.getChangedFields();
        if (changedFields == null || changedFields.isEmpty()) {
            return false;
        }

        return !Collections.disjoint(triggerFields, changedFields);
    }

    /**
     * Logs a failed execution (e.g., filter formula error).
     */
    private void logFailedExecution(WorkflowRuleData rule, RecordChangeEvent event,
                                      String errorMessage, long startTime) {
        String executionLogId = store.createExecutionLog(
            event.getTenantId(), rule.id(), event.getRecordId(),
            mapChangeTypeToTrigger(event.getChangeType()));
        int durationMs = (int) (System.currentTimeMillis() - startTime);
        store.updateExecutionLog(executionLogId, "FAILURE", 0, errorMessage, durationMs);
    }

    /**
     * Logs individual action execution with input/output snapshots.
     */
    private void logActionExecution(String executionLogId, WorkflowActionData action,
                                      ActionResult result, RecordChangeEvent event,
                                      int durationMs, int attemptNumber) {
        String inputSnapshot = null;
        try {
            Map<String, Object> input = Map.of(
                "actionConfig", action.config() != null ? action.config() : "{}",
                "recordId", event.getRecordId() != null ? event.getRecordId() : "",
                "collectionName", event.getCollectionName() != null ? event.getCollectionName() : ""
            );
            inputSnapshot = objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            log.warn("Failed to serialize action input snapshot: {}", e.getMessage());
        }

        String outputSnapshot = null;
        try {
            if (result.outputData() != null && !result.outputData().isEmpty()) {
                outputSnapshot = objectMapper.writeValueAsString(result.outputData());
            }
        } catch (Exception e) {
            log.warn("Failed to serialize action output: {}", e.getMessage());
        }

        store.createActionLog(
            executionLogId,
            action.id(),
            action.actionType(),
            result.successful() ? "SUCCESS" : "FAILURE",
            result.errorMessage(),
            inputSnapshot,
            outputSnapshot,
            durationMs,
            attemptNumber
        );
    }
}
