package com.emf.controlplane.service.workflow;

import com.emf.controlplane.dto.WorkflowRuleDto;
import com.emf.controlplane.entity.WorkflowAction;
import com.emf.controlplane.entity.WorkflowActionLog;
import com.emf.controlplane.entity.WorkflowExecutionLog;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.repository.WorkflowActionLogRepository;
import com.emf.controlplane.repository.WorkflowExecutionLogRepository;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import com.emf.controlplane.service.CollectionService;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.formula.FormulaEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core workflow execution engine.
 * <p>
 * Evaluates workflow rules against record change events and executes matching
 * actions via the {@link ActionHandlerRegistry}.
 * <p>
 * Flow:
 * <ol>
 *   <li>Receive {@link RecordChangeEvent} from Kafka via {@link WorkflowEventListener}</li>
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
 */
@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowRuleRepository ruleRepository;
    private final WorkflowExecutionLogRepository executionLogRepository;
    private final WorkflowActionLogRepository actionLogRepository;
    private final ActionHandlerRegistry handlerRegistry;
    private final FormulaEvaluator formulaEvaluator;
    private final CollectionService collectionService;
    private final ObjectMapper objectMapper;

    public WorkflowEngine(WorkflowRuleRepository ruleRepository,
                           WorkflowExecutionLogRepository executionLogRepository,
                           WorkflowActionLogRepository actionLogRepository,
                           ActionHandlerRegistry handlerRegistry,
                           FormulaEvaluator formulaEvaluator,
                           CollectionService collectionService,
                           ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.executionLogRepository = executionLogRepository;
        this.actionLogRepository = actionLogRepository;
        this.handlerRegistry = handlerRegistry;
        this.formulaEvaluator = formulaEvaluator;
        this.collectionService = collectionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates all matching workflow rules for a record change event.
     */
    public void evaluate(RecordChangeEvent event) {
        String triggerType = mapChangeTypeToTrigger(event.getChangeType());
        String tenantId = event.getTenantId();

        // Find the collection entity to get its ID
        String collectionId;
        try {
            var collection = collectionService.getCollectionByIdOrName(event.getCollectionName());
            collectionId = collection.getId();
        } catch (Exception e) {
            log.warn("Collection '{}' not found for workflow evaluation, skipping", event.getCollectionName());
            return;
        }

        // Find matching rules: active rules for this tenant + collection + trigger type
        List<WorkflowRule> matchingRules = findMatchingRules(tenantId, collectionId, triggerType);
        if (matchingRules.isEmpty()) {
            log.debug("No matching workflow rules for tenant={}, collection={}, trigger={}",
                tenantId, event.getCollectionName(), triggerType);
            return;
        }

        log.info("Found {} matching workflow rules for collection={}, trigger={}, recordId={}",
            matchingRules.size(), event.getCollectionName(), triggerType, event.getRecordId());

        // Evaluate each rule
        for (WorkflowRule rule : matchingRules) {
            evaluateRule(rule, event);
        }
    }

    /**
     * Evaluates a single workflow rule against a record change event.
     */
    @Transactional
    public void evaluateRule(WorkflowRule rule, RecordChangeEvent event) {
        long startTime = System.currentTimeMillis();

        // Check trigger fields — if specified, only fire when at least one
        // of the listed fields appears in the event's changedFields list
        if (!matchesTriggerFields(rule, event)) {
            log.debug("Trigger fields check rejected record {} for rule '{}': " +
                    "none of {} were in changed fields {}",
                event.getRecordId(), rule.getName(),
                rule.getTriggerFields(), event.getChangedFields());
            return;
        }

        // Check filter formula
        if (rule.getFilterFormula() != null && !rule.getFilterFormula().isBlank()) {
            try {
                boolean filterPasses = formulaEvaluator.evaluateBoolean(
                    rule.getFilterFormula(), event.getData());
                if (!filterPasses) {
                    log.debug("Filter formula rejected record {} for rule '{}': {}",
                        event.getRecordId(), rule.getName(), rule.getFilterFormula());
                    return;
                }
            } catch (Exception e) {
                log.warn("Error evaluating filter formula for rule '{}': {}",
                    rule.getName(), e.getMessage());
                logExecution(rule, event, "FAILURE", 0,
                    "Filter formula error: " + e.getMessage(), startTime);
                return;
            }
        }

        // Execute actions
        List<WorkflowAction> activeActions = rule.getActions().stream()
            .filter(WorkflowAction::isActive)
            .sorted((a, b) -> Integer.compare(a.getExecutionOrder(), b.getExecutionOrder()))
            .toList();

        if (activeActions.isEmpty()) {
            log.debug("Rule '{}' has no active actions, skipping", rule.getName());
            return;
        }

        // Create execution log
        WorkflowExecutionLog executionLog = createExecutionLog(rule, event);
        boolean stopOnError = "STOP_ON_ERROR".equals(rule.getErrorHandling());
        int actionsExecuted = 0;
        String overallStatus = "SUCCESS";
        String overallError = null;

        for (WorkflowAction action : activeActions) {
            long actionStartTime = System.currentTimeMillis();
            ActionResult result = executeActionWithRetry(action, rule, event, executionLog.getId());
            int actionDurationMs = (int) (System.currentTimeMillis() - actionStartTime);
            actionsExecuted++;

            if (!result.successful()) {
                if (stopOnError) {
                    overallStatus = "FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.getActionType(), result.errorMessage());
                    log.error("Workflow rule '{}' stopped on error at action {}: {}",
                        rule.getName(), action.getActionType(), result.errorMessage());
                    break;
                } else {
                    overallStatus = "PARTIAL_FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.getActionType(), result.errorMessage());
                    log.warn("Workflow rule '{}' continuing despite error at action {}: {}",
                        rule.getName(), action.getActionType(), result.errorMessage());
                }
            }
        }

        // Update execution log
        long durationMs = System.currentTimeMillis() - startTime;
        executionLog.setStatus(overallStatus);
        executionLog.setActionsExecuted(actionsExecuted);
        executionLog.setErrorMessage(overallError);
        executionLog.setDurationMs((int) durationMs);
        executionLogRepository.save(executionLog);

        log.info("Workflow rule '{}' completed: status={}, actions={}, duration={}ms",
            rule.getName(), overallStatus, actionsExecuted, durationMs);
    }

    /**
     * Executes a scheduled workflow rule without a specific record context.
     * <p>
     * Unlike {@link #evaluateRule(WorkflowRule, RecordChangeEvent)}, this method
     * does not check trigger fields or evaluate filter formulas. It directly
     * executes the rule's actions for scheduled/periodic execution.
     *
     * @param rule the scheduled workflow rule to execute
     */
    @Transactional
    public void executeScheduledRule(WorkflowRule rule) {
        long startTime = System.currentTimeMillis();
        String tenantId = rule.getTenantId();
        String collectionId = rule.getCollection().getId();
        String collectionName = rule.getCollection().getName();

        log.info("Executing scheduled workflow rule '{}' for tenant={}, collection={}",
            rule.getName(), tenantId, collectionName);

        List<WorkflowAction> activeActions = rule.getActions().stream()
            .filter(WorkflowAction::isActive)
            .sorted((a, b) -> Integer.compare(a.getExecutionOrder(), b.getExecutionOrder()))
            .toList();

        if (activeActions.isEmpty()) {
            log.debug("Scheduled rule '{}' has no active actions, skipping", rule.getName());
            return;
        }

        // Create a synthetic event for logging and action context
        RecordChangeEvent syntheticEvent = new RecordChangeEvent(
            java.util.UUID.randomUUID().toString(),
            tenantId, collectionName, null, ChangeType.CREATED,
            Map.of(), null, List.of(), "system",
            java.time.Instant.now());

        // Create execution log
        WorkflowExecutionLog executionLog = createScheduledExecutionLog(rule);
        boolean stopOnError = "STOP_ON_ERROR".equals(rule.getErrorHandling());
        int actionsExecuted = 0;
        String overallStatus = "SUCCESS";
        String overallError = null;

        for (WorkflowAction action : activeActions) {
            long actionStartTime = System.currentTimeMillis();
            ActionResult result = executeScheduledAction(action, rule, executionLog.getId());
            int actionDurationMs = (int) (System.currentTimeMillis() - actionStartTime);
            actionsExecuted++;

            logActionExecution(executionLog.getId(), action, result, syntheticEvent, actionDurationMs, 1);

            if (!result.successful()) {
                if (stopOnError) {
                    overallStatus = "FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.getActionType(), result.errorMessage());
                    log.error("Scheduled rule '{}' stopped on error at action {}: {}",
                        rule.getName(), action.getActionType(), result.errorMessage());
                    break;
                } else {
                    overallStatus = "PARTIAL_FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.getActionType(), result.errorMessage());
                    log.warn("Scheduled rule '{}' continuing despite error at action {}: {}",
                        rule.getName(), action.getActionType(), result.errorMessage());
                }
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        executionLog.setStatus(overallStatus);
        executionLog.setActionsExecuted(actionsExecuted);
        executionLog.setErrorMessage(overallError);
        executionLog.setDurationMs((int) durationMs);
        executionLogRepository.save(executionLog);

        log.info("Scheduled rule '{}' completed: status={}, actions={}, duration={}ms",
            rule.getName(), overallStatus, actionsExecuted, durationMs);
    }

    /**
     * Executes a workflow rule manually for a specific record ID.
     * <p>
     * Creates a synthetic event and executes the rule's actions. Does not check
     * trigger fields or filter formulas — the caller explicitly chose to run this rule.
     *
     * @param rule     the workflow rule to execute
     * @param recordId the record ID to execute against (may be null)
     * @param userId   the user who triggered the manual execution
     * @return the execution log ID, or null if no active actions
     */
    @Transactional
    public String executeManualRule(WorkflowRule rule, String recordId, String userId) {
        long startTime = System.currentTimeMillis();
        String tenantId = rule.getTenantId();
        String collectionName = rule.getCollection().getName();

        log.info("Manual execution of workflow rule '{}' for record={}, user={}",
            rule.getName(), recordId, userId);

        List<WorkflowAction> activeActions = rule.getActions().stream()
            .filter(WorkflowAction::isActive)
            .sorted((a, b) -> Integer.compare(a.getExecutionOrder(), b.getExecutionOrder()))
            .toList();

        if (activeActions.isEmpty()) {
            log.debug("Rule '{}' has no active actions, skipping manual execution", rule.getName());
            return null;
        }

        // Create a synthetic event for the action context
        RecordChangeEvent syntheticEvent = new RecordChangeEvent(
            java.util.UUID.randomUUID().toString(),
            tenantId, collectionName, recordId, ChangeType.CREATED,
            Map.of(), null, List.of(), userId != null ? userId : "system",
            Instant.now());

        // Create execution log with MANUAL trigger type
        WorkflowExecutionLog executionLog = new WorkflowExecutionLog();
        executionLog.setTenantId(tenantId);
        executionLog.setWorkflowRule(rule);
        executionLog.setRecordId(recordId);
        executionLog.setTriggerType("MANUAL");
        executionLog.setStatus("EXECUTING");
        executionLog.setExecutedAt(Instant.now());
        executionLog = executionLogRepository.save(executionLog);

        boolean stopOnError = "STOP_ON_ERROR".equals(rule.getErrorHandling());
        int actionsExecuted = 0;
        String overallStatus = "SUCCESS";
        String overallError = null;

        for (WorkflowAction action : activeActions) {
            long actionStartTime = System.currentTimeMillis();
            ActionResult result = executeAction(action, rule, syntheticEvent, executionLog.getId());
            int actionDurationMs = (int) (System.currentTimeMillis() - actionStartTime);
            actionsExecuted++;

            logActionExecution(executionLog.getId(), action, result, syntheticEvent, actionDurationMs, 1);

            if (!result.successful()) {
                if (stopOnError) {
                    overallStatus = "FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.getActionType(), result.errorMessage());
                    break;
                } else {
                    overallStatus = "PARTIAL_FAILURE";
                    overallError = String.format("Action '%s' failed: %s",
                        action.getActionType(), result.errorMessage());
                }
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        executionLog.setStatus(overallStatus);
        executionLog.setActionsExecuted(actionsExecuted);
        executionLog.setErrorMessage(overallError);
        executionLog.setDurationMs((int) durationMs);
        executionLogRepository.save(executionLog);

        log.info("Manual execution of rule '{}' completed: status={}, actions={}, duration={}ms",
            rule.getName(), overallStatus, actionsExecuted, durationMs);

        return executionLog.getId();
    }

    /**
     * Evaluates before-save workflow rules synchronously during record create/update.
     * <p>
     * Only FIELD_UPDATE actions are supported for before-save triggers (safe for
     * synchronous context). Returns accumulated field updates to apply before persist.
     * <p>
     * Called by the worker via {@code POST /internal/workflow/before-save}.
     *
     * @param tenantId       the tenant ID
     * @param collectionId   the collection ID
     * @param collectionName the collection name
     * @param recordId       the record ID (null for creates)
     * @param data           the current record data
     * @param previousData   the previous record data (null for creates)
     * @param changedFields  the list of changed fields (empty for creates)
     * @param userId         the user performing the save
     * @param changeType     "CREATE" or "UPDATE"
     * @return a map with keys: "fieldUpdates" (Map), "rulesEvaluated" (int), "actionsExecuted" (int)
     */
    @Transactional
    public Map<String, Object> evaluateBeforeSave(String tenantId, String collectionId,
                                                    String collectionName, String recordId,
                                                    Map<String, Object> data,
                                                    Map<String, Object> previousData,
                                                    List<String> changedFields,
                                                    String userId, String changeType) {
        // Determine the trigger type to match
        String triggerType = "CREATE".equals(changeType) ? "BEFORE_CREATE" : "BEFORE_UPDATE";

        log.info("Evaluating before-save workflows: tenant={}, collection={}, trigger={}, record={}",
            tenantId, collectionName, triggerType, recordId);

        List<WorkflowRule> rules = ruleRepository
            .findByTenantIdAndCollectionIdAndTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(
                tenantId, collectionId, triggerType);

        Map<String, Object> accumulatedUpdates = new HashMap<>();
        int rulesEvaluated = 0;
        int actionsExecuted = 0;

        for (WorkflowRule rule : rules) {
            rulesEvaluated++;

            // Check trigger fields for BEFORE_UPDATE
            if ("BEFORE_UPDATE".equals(triggerType)) {
                List<String> triggerFields = WorkflowRuleDto.parseTriggerFields(rule.getTriggerFields());
                if (triggerFields != null && !triggerFields.isEmpty()) {
                    if (changedFields == null || changedFields.isEmpty()
                            || Collections.disjoint(triggerFields, changedFields)) {
                        log.debug("Before-save trigger fields check rejected record {} for rule '{}'",
                            recordId, rule.getName());
                        continue;
                    }
                }
            }

            // Check filter formula
            if (rule.getFilterFormula() != null && !rule.getFilterFormula().isBlank()) {
                try {
                    boolean filterPasses = formulaEvaluator.evaluateBoolean(
                        rule.getFilterFormula(), data);
                    if (!filterPasses) {
                        log.debug("Before-save filter rejected record {} for rule '{}': {}",
                            recordId, rule.getName(), rule.getFilterFormula());
                        continue;
                    }
                } catch (Exception e) {
                    log.warn("Error evaluating before-save filter for rule '{}': {}",
                        rule.getName(), e.getMessage());
                    continue;
                }
            }

            // Execute only FIELD_UPDATE actions (safe for synchronous context)
            List<WorkflowAction> fieldUpdateActions = rule.getActions().stream()
                .filter(WorkflowAction::isActive)
                .filter(a -> "FIELD_UPDATE".equals(a.getActionType()))
                .sorted((a, b) -> Integer.compare(a.getExecutionOrder(), b.getExecutionOrder()))
                .toList();

            boolean stopOnError = "STOP_ON_ERROR".equals(rule.getErrorHandling());

            for (WorkflowAction action : fieldUpdateActions) {
                ActionContext context = ActionContext.builder()
                    .tenantId(tenantId)
                    .collectionId(collectionId)
                    .collectionName(collectionName)
                    .recordId(recordId)
                    .data(data)
                    .previousData(previousData)
                    .changedFields(changedFields != null ? changedFields : List.of())
                    .userId(userId != null ? userId : "system")
                    .actionConfigJson(action.getConfig())
                    .workflowRuleId(rule.getId())
                    .executionLogId(null)
                    .resolvedData(Map.of())
                    .build();

                Optional<ActionHandler> handlerOpt = handlerRegistry.getHandler("FIELD_UPDATE");
                if (handlerOpt.isEmpty()) {
                    log.error("No FIELD_UPDATE handler registered for before-save rule '{}'", rule.getName());
                    continue;
                }

                try {
                    ActionResult result = handlerOpt.get().execute(context);
                    actionsExecuted++;

                    if (result.successful() && result.outputData() != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> updatedFields = (Map<String, Object>) result.outputData().get("updatedFields");
                        if (updatedFields != null) {
                            accumulatedUpdates.putAll(updatedFields);
                        }
                    } else if (!result.successful()) {
                        log.warn("Before-save FIELD_UPDATE failed for rule '{}': {}",
                            rule.getName(), result.errorMessage());
                        if (stopOnError) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("Exception in before-save FIELD_UPDATE for rule '{}': {}",
                        rule.getName(), e.getMessage(), e);
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

    /**
     * Executes a single action for a scheduled workflow rule.
     */
    private ActionResult executeScheduledAction(WorkflowAction action, WorkflowRule rule,
                                                  String executionLogId) {
        String actionType = action.getActionType();
        Optional<ActionHandler> handlerOpt = handlerRegistry.getHandler(actionType);

        if (handlerOpt.isEmpty()) {
            log.error("No handler registered for action type '{}' in rule '{}'",
                actionType, rule.getName());
            return ActionResult.failure("No handler registered for action type: " + actionType);
        }

        ActionHandler handler = handlerOpt.get();
        ActionContext context = ActionContext.builder()
            .tenantId(rule.getTenantId())
            .collectionId(rule.getCollection().getId())
            .collectionName(rule.getCollection().getName())
            .recordId(null)
            .data(Map.of())
            .previousData(null)
            .changedFields(List.of())
            .userId("system")
            .actionConfigJson(action.getConfig())
            .workflowRuleId(rule.getId())
            .executionLogId(executionLogId)
            .resolvedData(Map.of())
            .build();

        try {
            return handler.execute(context);
        } catch (Exception e) {
            log.error("Exception executing scheduled action '{}' for rule '{}': {}",
                actionType, rule.getName(), e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    /**
     * Creates an execution log entry for a scheduled rule execution.
     */
    private WorkflowExecutionLog createScheduledExecutionLog(WorkflowRule rule) {
        WorkflowExecutionLog executionLog = new WorkflowExecutionLog();
        executionLog.setTenantId(rule.getTenantId());
        executionLog.setWorkflowRule(rule);
        executionLog.setRecordId(null);
        executionLog.setTriggerType("SCHEDULED");
        executionLog.setStatus("EXECUTING");
        executionLog.setExecutedAt(Instant.now());
        return executionLogRepository.save(executionLog);
    }

    /**
     * Executes an action with retry logic.
     * <p>
     * If the action has retry configured (retryCount > 0), failed executions are retried
     * with the configured delay and backoff strategy. Each attempt is logged individually.
     */
    ActionResult executeActionWithRetry(WorkflowAction action, WorkflowRule rule,
                                               RecordChangeEvent event, String executionLogId) {
        int maxAttempts = 1 + Math.max(0, action.getRetryCount());
        int delaySeconds = Math.max(1, action.getRetryDelaySeconds());
        boolean exponentialBackoff = "EXPONENTIAL".equals(action.getRetryBackoff());

        ActionResult lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long attemptStartTime = System.currentTimeMillis();
            lastResult = executeAction(action, rule, event, executionLogId);
            int attemptDurationMs = (int) (System.currentTimeMillis() - attemptStartTime);

            // Log this attempt
            logActionExecution(executionLogId, action, lastResult, event, attemptDurationMs, attempt);

            if (lastResult.successful()) {
                if (attempt > 1) {
                    log.info("Action '{}' in rule '{}' succeeded on attempt {}",
                        action.getActionType(), rule.getName(), attempt);
                }
                return lastResult;
            }

            // If there are more retries, wait before the next attempt
            if (attempt < maxAttempts) {
                int waitSeconds = exponentialBackoff
                    ? delaySeconds * (int) Math.pow(2, attempt - 1)
                    : delaySeconds;
                log.info("Action '{}' in rule '{}' failed on attempt {}/{}, retrying in {}s: {}",
                    action.getActionType(), rule.getName(), attempt, maxAttempts,
                    waitSeconds, lastResult.errorMessage());
                try {
                    Thread.sleep(waitSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Retry wait interrupted for action '{}' in rule '{}'",
                        action.getActionType(), rule.getName());
                    return lastResult;
                }
            }
        }

        if (maxAttempts > 1) {
            log.error("Action '{}' in rule '{}' failed after {} attempts: {}",
                action.getActionType(), rule.getName(), maxAttempts,
                lastResult != null ? lastResult.errorMessage() : "unknown");
        }

        return lastResult;
    }

    /**
     * Executes a single action within a workflow rule.
     */
    private ActionResult executeAction(WorkflowAction action, WorkflowRule rule,
                                        RecordChangeEvent event, String executionLogId) {
        String actionType = action.getActionType();
        Optional<ActionHandler> handlerOpt = handlerRegistry.getHandler(actionType);

        if (handlerOpt.isEmpty()) {
            log.error("No handler registered for action type '{}' in rule '{}'",
                actionType, rule.getName());
            return ActionResult.failure("No handler registered for action type: " + actionType);
        }

        ActionHandler handler = handlerOpt.get();

        ActionContext context = ActionContext.builder()
            .tenantId(event.getTenantId())
            .collectionId(rule.getCollection().getId())
            .collectionName(event.getCollectionName())
            .recordId(event.getRecordId())
            .data(event.getData())
            .previousData(event.getPreviousData())
            .changedFields(event.getChangedFields())
            .userId(event.getUserId())
            .actionConfigJson(action.getConfig())
            .workflowRuleId(rule.getId())
            .executionLogId(executionLogId)
            .resolvedData(Map.of()) // DataPayload resolution will be added in A5
            .build();

        try {
            return handler.execute(context);
        } catch (Exception e) {
            log.error("Exception executing action '{}' for rule '{}': {}",
                actionType, rule.getName(), e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    /**
     * Maps a RecordChangeEvent.ChangeType to a workflow trigger type.
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
    private List<WorkflowRule> findMatchingRules(String tenantId, String collectionId, String triggerType) {
        // Get rules that match the specific trigger
        List<WorkflowRule> specificRules = ruleRepository
            .findByTenantIdAndCollectionIdAndTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(
                tenantId, collectionId, triggerType);

        // Also get ON_CREATE_OR_UPDATE rules for create/update events
        if ("ON_CREATE".equals(triggerType) || "ON_UPDATE".equals(triggerType)) {
            List<WorkflowRule> combinedRules = ruleRepository
                .findByTenantIdAndCollectionIdAndTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(
                    tenantId, collectionId, "ON_CREATE_OR_UPDATE");

            if (!combinedRules.isEmpty()) {
                // Merge and sort by execution order
                return java.util.stream.Stream.concat(specificRules.stream(), combinedRules.stream())
                    .sorted((a, b) -> Integer.compare(a.getExecutionOrder(), b.getExecutionOrder()))
                    .toList();
            }
        }

        return specificRules;
    }

    /**
     * Creates an initial execution log entry.
     */
    private WorkflowExecutionLog createExecutionLog(WorkflowRule rule, RecordChangeEvent event) {
        WorkflowExecutionLog executionLog = new WorkflowExecutionLog();
        executionLog.setTenantId(event.getTenantId());
        executionLog.setWorkflowRule(rule);
        executionLog.setRecordId(event.getRecordId());
        executionLog.setTriggerType(mapChangeTypeToTrigger(event.getChangeType()));
        executionLog.setStatus("EXECUTING");
        executionLog.setExecutedAt(Instant.now());
        return executionLogRepository.save(executionLog);
    }

    /**
     * Logs individual action execution result with input snapshot, duration, and attempt number.
     */
    private void logActionExecution(String executionLogId, WorkflowAction action,
                                      ActionResult result, RecordChangeEvent event,
                                      int durationMs, int attemptNumber) {
        WorkflowActionLog actionLog = new WorkflowActionLog();
        actionLog.setExecutionLogId(executionLogId);
        actionLog.setActionId(action.getId());
        actionLog.setActionType(action.getActionType());
        actionLog.setStatus(result.successful() ? "SUCCESS" : "FAILURE");
        actionLog.setErrorMessage(result.errorMessage());
        actionLog.setDurationMs(durationMs);
        actionLog.setAttemptNumber(attemptNumber);
        actionLog.setExecutedAt(Instant.now());

        // Capture input snapshot (action config + record data summary)
        try {
            Map<String, Object> inputSnapshot = Map.of(
                "actionConfig", action.getConfig() != null ? action.getConfig() : "{}",
                "recordId", event.getRecordId() != null ? event.getRecordId() : "",
                "collectionName", event.getCollectionName() != null ? event.getCollectionName() : ""
            );
            actionLog.setInputSnapshot(objectMapper.writeValueAsString(inputSnapshot));
        } catch (Exception e) {
            log.warn("Failed to serialize action input snapshot: {}", e.getMessage());
        }

        // Capture output snapshot
        try {
            if (result.outputData() != null && !result.outputData().isEmpty()) {
                actionLog.setOutputSnapshot(objectMapper.writeValueAsString(result.outputData()));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize action output: {}", e.getMessage());
        }

        actionLogRepository.save(actionLog);
    }

    /**
     * Creates a failed execution log entry.
     */
    private void logExecution(WorkflowRule rule, RecordChangeEvent event,
                               String status, int actionsExecuted,
                               String errorMessage, long startTime) {
        WorkflowExecutionLog executionLog = new WorkflowExecutionLog();
        executionLog.setTenantId(event.getTenantId());
        executionLog.setWorkflowRule(rule);
        executionLog.setRecordId(event.getRecordId());
        executionLog.setTriggerType(mapChangeTypeToTrigger(event.getChangeType()));
        executionLog.setStatus(status);
        executionLog.setActionsExecuted(actionsExecuted);
        executionLog.setErrorMessage(errorMessage);
        executionLog.setDurationMs((int) (System.currentTimeMillis() - startTime));
        executionLog.setExecutedAt(Instant.now());
        executionLogRepository.save(executionLog);
    }

    /**
     * Checks whether the event's changed fields match the rule's trigger fields.
     * <p>
     * If the rule has no trigger fields configured (null or empty), the rule matches
     * any change (backward compatible). If trigger fields are configured, at least one
     * of the listed fields must appear in the event's changedFields list.
     * <p>
     * For CREATE and DELETE events (which have no changedFields), trigger fields are
     * not applicable, so the rule always matches.
     *
     * @param rule  the workflow rule to check
     * @param event the record change event
     * @return true if the rule should fire, false if trigger fields filter it out
     */
    boolean matchesTriggerFields(WorkflowRule rule, RecordChangeEvent event) {
        // Parse trigger fields from JSONB string
        List<String> triggerFields = WorkflowRuleDto.parseTriggerFields(rule.getTriggerFields());

        // No trigger fields configured — match any change
        if (triggerFields == null || triggerFields.isEmpty()) {
            return true;
        }

        // For non-update events (CREATE, DELETE), changedFields is empty/irrelevant
        // Trigger fields filtering only applies to UPDATE events
        if (event.getChangeType() != ChangeType.UPDATED) {
            return true;
        }

        // Check if any of the trigger fields were changed
        List<String> changedFields = event.getChangedFields();
        if (changedFields == null || changedFields.isEmpty()) {
            return false;
        }

        return !Collections.disjoint(triggerFields, changedFields);
    }
}
