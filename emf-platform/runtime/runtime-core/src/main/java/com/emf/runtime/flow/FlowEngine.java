package com.emf.runtime.flow;

import com.emf.runtime.flow.executor.*;
import com.emf.runtime.workflow.ActionHandlerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Core flow execution engine that interprets {@link FlowDefinition} state machines.
 * <p>
 * The engine traverses states from {@code startAt} until it reaches a terminal state
 * (Succeed or Fail), the execution is cancelled, or the step limit is reached.
 * <p>
 * Each state type is handled by a registered {@link StateExecutor}. The engine
 * manages the dispatch loop, state data flow, and execution persistence.
 * <p>
 * Execution is asynchronous by default — {@link #startExecution} dispatches to a
 * managed thread pool and returns the execution ID immediately. Use
 * {@link #executeSynchronous} for blocking execution.
 *
 * @since 1.0.0
 */
public class FlowEngine {

    private static final Logger log = LoggerFactory.getLogger(FlowEngine.class);

    /** Maximum steps per execution to prevent infinite loops. */
    private static final int MAX_STEPS = 500;

    private final FlowStore flowStore;
    private final FlowDefinitionParser parser;
    private final StateDataResolver dataResolver;
    private final Map<String, StateExecutor> executors;
    private final ExecutorService threadPool;

    public FlowEngine(FlowStore flowStore,
                      ActionHandlerRegistry handlerRegistry,
                      ObjectMapper objectMapper,
                      int threadPoolSize) {
        this.flowStore = flowStore;
        this.parser = new FlowDefinitionParser(objectMapper);
        this.dataResolver = new StateDataResolver(objectMapper);
        this.threadPool = Executors.newFixedThreadPool(threadPoolSize);

        // Register state executors
        ChoiceRuleEvaluator ruleEvaluator = new ChoiceRuleEvaluator(dataResolver);
        this.executors = new LinkedHashMap<>();
        registerExecutor(new TaskStateExecutor(handlerRegistry, dataResolver, objectMapper));
        registerExecutor(new ChoiceStateExecutor(ruleEvaluator));
        registerExecutor(new PassStateExecutor(dataResolver));
        registerExecutor(new WaitStateExecutor());
        registerExecutor(new SucceedStateExecutor());
        registerExecutor(new FailStateExecutor());
        registerExecutor(new ParallelStateExecutor(dataResolver, threadPool, this));
        registerExecutor(new MapStateExecutor(dataResolver, threadPool, this));
    }

    private void registerExecutor(StateExecutor executor) {
        executors.put(executor.stateType(), executor);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Starts a flow execution asynchronously. Returns the execution ID immediately.
     * The execution runs on the engine's thread pool.
     *
     * @param tenantId        tenant ID
     * @param flowId          flow ID
     * @param definitionJson  the flow definition JSON
     * @param initialInput    the initial state data
     * @param userId          user who initiated the execution (null for triggers)
     * @param isTest          whether this is a test execution
     * @return the execution ID
     */
    public String startExecution(String tenantId, String flowId, String definitionJson,
                                 Map<String, Object> initialInput, String userId, boolean isTest) {
        FlowDefinition definition = parser.parse(definitionJson);
        String executionId = flowStore.createExecution(tenantId, flowId, userId, null, initialInput, isTest);

        threadPool.submit(() -> {
            try {
                executeFlow(executionId, tenantId, flowId, userId, definition, initialInput);
            } catch (Exception e) {
                log.error("Flow execution {} failed unexpectedly", executionId, e);
                flowStore.completeExecution(executionId, FlowExecutionData.STATUS_FAILED,
                    initialInput, e.getMessage(), 0, 0);
            }
        });

        return executionId;
    }

    /**
     * Executes a flow synchronously, blocking until completion.
     * Returns the final state data.
     *
     * @param tenantId        tenant ID
     * @param flowId          flow ID
     * @param definitionJson  the flow definition JSON
     * @param initialInput    the initial state data
     * @param userId          user who initiated the execution
     * @return the final state data after execution completes
     */
    public Map<String, Object> executeSynchronous(String tenantId, String flowId,
                                                   String definitionJson,
                                                   Map<String, Object> initialInput,
                                                   String userId) {
        FlowDefinition definition = parser.parse(definitionJson);
        String executionId = flowStore.createExecution(tenantId, flowId, userId, null, initialInput, false);
        return executeFlow(executionId, tenantId, flowId, userId, definition, initialInput);
    }

    /**
     * Resumes a waiting execution.
     *
     * @param executionId the execution ID to resume
     */
    public void resumeExecution(String executionId) {
        Optional<FlowExecutionData> executionOpt = flowStore.loadExecution(executionId);
        if (executionOpt.isEmpty()) {
            log.warn("Cannot resume execution {} — not found", executionId);
            return;
        }

        FlowExecutionData execution = executionOpt.get();
        if (!FlowExecutionData.STATUS_WAITING.equals(execution.status())) {
            log.warn("Cannot resume execution {} — status is {}", executionId, execution.status());
            return;
        }

        threadPool.submit(() -> {
            try {
                // Re-load to get the current node
                FlowExecutionData fresh = flowStore.loadExecution(executionId).orElseThrow();
                // We need the definition JSON — for now, mark as resumed and advance
                // The caller must provide the definition or it must be re-loaded from the flow table
                log.info("Resuming execution {} from state '{}'",
                    executionId, fresh.currentNodeId());
                // TODO: Resume needs the flow definition, which requires FlowStore to load the flow definition
                // This will be completed in Phase 1C when the full worker integration provides the definition
            } catch (Exception e) {
                log.error("Failed to resume execution {}", executionId, e);
            }
        });
    }

    /**
     * Cancels a running execution.
     *
     * @param executionId the execution ID to cancel
     */
    public void cancelExecution(String executionId) {
        flowStore.cancelExecution(executionId);
        log.info("Cancelled execution {}", executionId);
    }

    /**
     * Executes a sub-flow (used by Parallel and Map state executors).
     * Runs the sub-flow from startAt to terminal state and returns the final state data.
     *
     * @param subFlow   the sub-flow definition
     * @param input     the input state data for the sub-flow
     * @param parent    the parent execution context (for tenant/user info)
     * @return the final state data
     */
    public Map<String, Object> executeSubFlow(FlowDefinition subFlow,
                                               Map<String, Object> input,
                                               FlowExecutionContext parent) {
        FlowExecutionContext subContext = new FlowExecutionContext(
            parent.executionId() + "-sub-" + UUID.randomUUID().toString().substring(0, 8),
            parent.tenantId(), parent.flowId(), parent.userId(),
            subFlow, input);

        return runStateLoop(subContext);
    }

    /**
     * Shuts down the engine's thread pool gracefully.
     */
    public void shutdown() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Internal Execution
    // -------------------------------------------------------------------------

    private Map<String, Object> executeFlow(String executionId, String tenantId, String flowId,
                                            String userId, FlowDefinition definition,
                                            Map<String, Object> initialInput) {
        FlowExecutionContext context = new FlowExecutionContext(
            executionId, tenantId, flowId, userId, definition, initialInput);

        log.info("Starting flow execution {} (flow={}, startAt={})",
            executionId, flowId, definition.startAt());

        Map<String, Object> finalData = runStateLoop(context);

        // Only complete if runStateLoop didn't already mark it as complete
        if (!context.isCompleted()) {
            String status = context.isCancelled()
                ? FlowExecutionData.STATUS_CANCELLED
                : FlowExecutionData.STATUS_COMPLETED;

            flowStore.completeExecution(executionId, status, finalData, null,
                context.elapsedMs(), context.stepCount());

            log.info("Flow execution {} completed with status {} ({} steps, {}ms)",
                executionId, status, context.stepCount(), context.elapsedMs());
        } else {
            log.info("Flow execution {} finished with status {} ({} steps, {}ms)",
                executionId, context.finalStatus(), context.stepCount(), context.elapsedMs());
        }

        return finalData;
    }

    private Map<String, Object> runStateLoop(FlowExecutionContext context) {
        while (!context.isCancelled() && context.stepCount() < MAX_STEPS) {
            StateDefinition currentState = context.currentState();
            if (currentState == null) {
                log.error("State '{}' not found in flow definition", context.currentStateId());
                flowStore.completeExecution(context.executionId(),
                    FlowExecutionData.STATUS_FAILED, context.stateData(),
                    "State '" + context.currentStateId() + "' not found",
                    context.elapsedMs(), context.stepCount());
                context.markCompleted(FlowExecutionData.STATUS_FAILED);
                return context.stateData();
            }

            String stateType = currentState.type();
            StateExecutor executor = executors.get(stateType);
            if (executor == null) {
                log.error("No executor for state type '{}'", stateType);
                flowStore.completeExecution(context.executionId(),
                    FlowExecutionData.STATUS_FAILED, context.stateData(),
                    "No executor for state type: " + stateType,
                    context.elapsedMs(), context.stepCount());
                context.markCompleted(FlowExecutionData.STATUS_FAILED);
                return context.stateData();
            }

            // Log step start
            long stepStart = System.currentTimeMillis();
            String stepLogId = flowStore.logStepExecution(
                context.executionId(), context.currentStateId(), currentState.name(),
                stateType, context.stateData(), null,
                FlowStepLogData.STATUS_RUNNING, null, null, null, 1);

            context.incrementStepCount();

            // Execute the state
            StateExecutionResult result = executor.execute(currentState, context);
            int stepDuration = (int) (System.currentTimeMillis() - stepStart);

            // Update step log
            flowStore.updateStepLog(stepLogId, result.updatedData(),
                result.status(), result.errorMessage(), result.errorCode(), stepDuration);

            // Handle result
            if ("WAITING".equals(result.status())) {
                // Persist execution as WAITING
                context.setStateData(result.updatedData());
                flowStore.updateExecutionState(context.executionId(), context.currentStateId(),
                    result.updatedData(), FlowExecutionData.STATUS_WAITING, context.stepCount());
                return result.updatedData();
            }

            if (result.terminal()) {
                context.setStateData(result.updatedData());
                if ("FAILED".equals(result.status())) {
                    flowStore.completeExecution(context.executionId(),
                        FlowExecutionData.STATUS_FAILED, result.updatedData(),
                        result.errorMessage(), context.elapsedMs(), context.stepCount());
                    context.markCompleted(FlowExecutionData.STATUS_FAILED);
                }
                return result.updatedData();
            }

            if ("FAILED".equals(result.status())) {
                // Uncaught failure
                flowStore.completeExecution(context.executionId(),
                    FlowExecutionData.STATUS_FAILED, result.updatedData(),
                    result.errorMessage(), context.elapsedMs(), context.stepCount());
                context.markCompleted(FlowExecutionData.STATUS_FAILED);
                return result.updatedData();
            }

            // Transition to next state
            context.setStateData(result.updatedData());
            context.setCurrentStateId(result.nextStateId());

            // Persist checkpoint
            flowStore.updateExecutionState(context.executionId(), result.nextStateId(),
                result.updatedData(), FlowExecutionData.STATUS_RUNNING, context.stepCount());
        }

        // Step limit exceeded
        if (context.stepCount() >= MAX_STEPS) {
            log.error("Execution {} exceeded max step limit ({})", context.executionId(), MAX_STEPS);
            flowStore.completeExecution(context.executionId(),
                FlowExecutionData.STATUS_FAILED, context.stateData(),
                "Execution exceeded maximum step limit (" + MAX_STEPS + ")",
                context.elapsedMs(), context.stepCount());
            context.markCompleted(FlowExecutionData.STATUS_FAILED);
        }

        return context.stateData();
    }
}
