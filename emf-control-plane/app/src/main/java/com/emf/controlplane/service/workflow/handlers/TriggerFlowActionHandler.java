package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionHandler;
import com.emf.controlplane.service.workflow.ActionResult;
import com.emf.controlplane.service.workflow.WorkflowEngine;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Action handler that invokes another workflow rule as a subflow.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "workflowRuleId": "uuid-of-target-rule"
 * }
 * </pre>
 * <p>
 * The target workflow rule is executed with the same record context as the current
 * workflow. A max depth counter prevents infinite recursion (max depth: 5).
 * <p>
 * The depth is tracked via a thread-local counter that increments on each nested
 * invocation and decrements on return.
 */
@Component
public class TriggerFlowActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(TriggerFlowActionHandler.class);
    private static final int MAX_DEPTH = 5;

    private static final ThreadLocal<Integer> depthCounter = ThreadLocal.withInitial(() -> 0);

    private final ObjectMapper objectMapper;
    private final WorkflowRuleRepository ruleRepository;
    private final WorkflowEngine workflowEngine;

    public TriggerFlowActionHandler(ObjectMapper objectMapper,
                                     WorkflowRuleRepository ruleRepository,
                                     @Lazy WorkflowEngine workflowEngine) {
        this.objectMapper = objectMapper;
        this.ruleRepository = ruleRepository;
        this.workflowEngine = workflowEngine;
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
                return ActionResult.failure("Target workflow rule ID is required");
            }

            // Prevent infinite recursion
            int currentDepth = depthCounter.get();
            if (currentDepth >= MAX_DEPTH) {
                log.warn("Trigger flow max depth ({}) exceeded, stopping recursion. " +
                    "Rule: {}, target: {}", MAX_DEPTH, context.workflowRuleId(), targetRuleId);
                return ActionResult.failure("Max subflow depth (" + MAX_DEPTH + ") exceeded");
            }

            // Prevent self-referencing loops
            if (targetRuleId.equals(context.workflowRuleId())) {
                return ActionResult.failure("Cannot trigger a workflow rule from itself");
            }

            // Find the target rule
            Optional<WorkflowRule> targetRuleOpt = ruleRepository.findById(targetRuleId);
            if (targetRuleOpt.isEmpty()) {
                return ActionResult.failure("Target workflow rule not found: " + targetRuleId);
            }

            WorkflowRule targetRule = targetRuleOpt.get();

            if (!targetRule.isActive()) {
                log.info("Target workflow rule '{}' is inactive, skipping", targetRule.getName());
                return ActionResult.success(Map.of(
                    "targetRuleId", targetRuleId,
                    "targetRuleName", targetRule.getName(),
                    "status", "SKIPPED",
                    "reason", "Target rule is inactive"
                ));
            }

            log.info("Trigger flow action: invoking rule '{}' (depth={}), source rule={}",
                targetRule.getName(), currentDepth + 1, context.workflowRuleId());

            // Create a synthetic event for the target rule execution
            RecordChangeEvent syntheticEvent = new RecordChangeEvent(
                java.util.UUID.randomUUID().toString(),
                context.tenantId(),
                context.collectionName(),
                context.recordId(),
                ChangeType.CREATED,
                context.data() != null ? context.data() : Map.of(),
                context.previousData(),
                context.changedFields() != null ? context.changedFields() : List.of(),
                context.userId() != null ? context.userId() : "system",
                Instant.now()
            );

            // Increment depth and execute
            depthCounter.set(currentDepth + 1);
            try {
                workflowEngine.evaluateRule(targetRule, syntheticEvent);
            } finally {
                depthCounter.set(currentDepth);
            }

            return ActionResult.success(Map.of(
                "targetRuleId", targetRuleId,
                "targetRuleName", targetRule.getName(),
                "depth", currentDepth + 1,
                "status", "EXECUTED"
            ));
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

    /**
     * Returns the current subflow depth (for testing).
     */
    static int getCurrentDepth() {
        return depthCounter.get();
    }

    /**
     * Resets the depth counter (for testing).
     */
    static void resetDepth() {
        depthCounter.remove();
    }
}
