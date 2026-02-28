package com.emf.runtime.flow.executor;

import com.emf.runtime.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Executes Parallel states by running branches concurrently and merging results.
 * <p>
 * Each branch is a sub-state-machine executed via the provided engine callback.
 * The results of all branches are collected into a list at the ResultPath.
 *
 * @since 1.0.0
 */
public class ParallelStateExecutor implements StateExecutor {

    private static final Logger log = LoggerFactory.getLogger(ParallelStateExecutor.class);

    private final StateDataResolver dataResolver;
    private final ExecutorService executorService;
    private final FlowEngine engine;

    public ParallelStateExecutor(StateDataResolver dataResolver,
                                 ExecutorService executorService,
                                 FlowEngine engine) {
        this.dataResolver = dataResolver;
        this.executorService = executorService;
        this.engine = engine;
    }

    @Override
    public String stateType() {
        return "Parallel";
    }

    @Override
    public StateExecutionResult execute(StateDefinition state, FlowExecutionContext context) {
        StateDefinition.ParallelState parallel = (StateDefinition.ParallelState) state;

        Map<String, Object> input = dataResolver.applyInputPath(context.stateData(), parallel.inputPath());

        List<FlowDefinition> branches = parallel.branches();
        if (branches == null || branches.isEmpty()) {
            return handleNoResult(parallel, context);
        }

        // Submit each branch for concurrent execution
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (FlowDefinition branch : branches) {
            futures.add(executorService.submit(() ->
                engine.executeSubFlow(branch, input, context)));
        }

        // Collect results
        List<Map<String, Object>> branchResults = new ArrayList<>();
        String errorCode = null;
        String errorMessage = null;

        for (int i = 0; i < futures.size(); i++) {
            try {
                branchResults.add(futures.get(i).get());
            } catch (ExecutionException e) {
                errorCode = "ParallelBranchFailed";
                errorMessage = "Branch " + i + " failed: " + e.getCause().getMessage();
                log.error("Parallel branch {} failed", i, e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errorCode = "ParallelInterrupted";
                errorMessage = "Parallel execution interrupted";
                break;
            }
        }

        if (errorCode != null) {
            return handleError(parallel, context, errorCode, errorMessage);
        }

        // Merge results via ResultPath
        Map<String, Object> afterResult = dataResolver.applyResultPath(
            context.stateData(), branchResults, parallel.resultPath());
        Map<String, Object> output = dataResolver.applyOutputPath(afterResult, parallel.outputPath());

        if (parallel.end()) {
            return StateExecutionResult.terminalSuccess(output);
        }
        return StateExecutionResult.success(parallel.next(), output);
    }

    private StateExecutionResult handleError(StateDefinition.ParallelState parallel,
                                             FlowExecutionContext context,
                                             String errorCode, String errorMessage) {
        for (CatchPolicy catchPolicy : parallel.catchPolicies()) {
            if (catchPolicy.matches(errorCode)) {
                Map<String, Object> errorData = new LinkedHashMap<>();
                errorData.put("Error", errorCode);
                errorData.put("Cause", errorMessage);
                Map<String, Object> stateAfterCatch = dataResolver.applyResultPath(
                    context.stateData(), errorData, catchPolicy.resultPath());
                return StateExecutionResult.success(catchPolicy.next(), stateAfterCatch);
            }
        }
        return StateExecutionResult.failure(errorCode, errorMessage, context.stateData());
    }

    private StateExecutionResult handleNoResult(StateDefinition.ParallelState parallel,
                                                FlowExecutionContext context) {
        Map<String, Object> output = dataResolver.applyOutputPath(context.stateData(), parallel.outputPath());
        if (parallel.end()) {
            return StateExecutionResult.terminalSuccess(output);
        }
        return StateExecutionResult.success(parallel.next(), output);
    }
}
