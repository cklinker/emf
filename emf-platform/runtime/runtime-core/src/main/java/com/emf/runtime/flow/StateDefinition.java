package com.emf.runtime.flow;

import java.util.List;
import java.util.Map;

/**
 * Sealed interface representing a state in a flow definition.
 * <p>
 * Each state type maps to an AWS Step Functions concept adapted for EMF's
 * data platform context. States are connected via {@code next} transitions
 * and terminated by {@code end = true} or terminal state types (Succeed, Fail).
 *
 * @since 1.0.0
 */
public sealed interface StateDefinition {

    /**
     * Returns the human-readable name of this state (optional, for display).
     */
    String name();

    /**
     * Returns an optional comment/description for this state.
     */
    String comment();

    /**
     * Returns the state type as a string (e.g., "Task", "Choice").
     */
    String type();

    // -------------------------------------------------------------------------
    // State Type Implementations
    // -------------------------------------------------------------------------

    /**
     * Executes an {@link com.emf.runtime.workflow.ActionHandler} by resource key.
     * Supports InputPath/OutputPath/ResultPath data flow, retry, and catch.
     */
    record TaskState(
        String name,
        String comment,
        String resource,
        String inputPath,
        String outputPath,
        String resultPath,
        Integer timeoutSeconds,
        String next,
        boolean end,
        List<RetryPolicy> retry,
        List<CatchPolicy> catchPolicies
    ) implements StateDefinition {
        @Override
        public String type() { return "Task"; }
    }

    /**
     * Conditional branching based on comparison rules evaluated against state data.
     */
    record ChoiceState(
        String name,
        String comment,
        List<ChoiceRule> choices,
        String defaultState
    ) implements StateDefinition {
        @Override
        public String type() { return "Choice"; }
    }

    /**
     * Executes multiple branches concurrently and merges their results.
     */
    record ParallelState(
        String name,
        String comment,
        List<FlowDefinition> branches,
        String inputPath,
        String outputPath,
        String resultPath,
        String next,
        boolean end,
        List<RetryPolicy> retry,
        List<CatchPolicy> catchPolicies
    ) implements StateDefinition {
        @Override
        public String type() { return "Parallel"; }
    }

    /**
     * Iterates over an array in the state data and executes a sub-flow for each item.
     */
    record MapState(
        String name,
        String comment,
        String itemsPath,
        FlowDefinition iterator,
        Integer maxConcurrency,
        String inputPath,
        String outputPath,
        String resultPath,
        String next,
        boolean end
    ) implements StateDefinition {
        @Override
        public String type() { return "Map"; }
    }

    /**
     * Pauses execution for a fixed duration, until a timestamp, or until an external event.
     */
    record WaitState(
        String name,
        String comment,
        Integer seconds,
        String timestamp,
        String timestampPath,
        String eventName,
        String next,
        boolean end
    ) implements StateDefinition {
        @Override
        public String type() { return "Wait"; }
    }

    /**
     * Passes input to output, optionally injecting a literal result or transforming data.
     */
    record PassState(
        String name,
        String comment,
        Map<String, Object> result,
        String inputPath,
        String outputPath,
        String resultPath,
        String next,
        boolean end
    ) implements StateDefinition {
        @Override
        public String type() { return "Pass"; }
    }

    /**
     * Terminal state indicating successful completion.
     */
    record SucceedState(
        String name,
        String comment
    ) implements StateDefinition {
        @Override
        public String type() { return "Succeed"; }
    }

    /**
     * Terminal state indicating failure with an error code and cause message.
     */
    record FailState(
        String name,
        String comment,
        String error,
        String cause
    ) implements StateDefinition {
        @Override
        public String type() { return "Fail"; }
    }
}
