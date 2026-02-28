package com.emf.runtime.flow;

/**
 * Executes a single state within a flow and returns the result.
 * <p>
 * Each state type (Task, Choice, Pass, etc.) has its own executor implementation.
 * The executor processes the state, updates the execution context's state data,
 * and returns the next state ID to transition to (or null for terminal states).
 *
 * @since 1.0.0
 */
public interface StateExecutor {

    /**
     * Returns the state type this executor handles (e.g., "Task", "Choice").
     */
    String stateType();

    /**
     * Executes the given state and returns the result.
     *
     * @param state   the state definition
     * @param context the mutable execution context
     * @return the execution result containing the next state ID (or null if terminal)
     */
    StateExecutionResult execute(StateDefinition state, FlowExecutionContext context);
}
