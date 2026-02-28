package com.emf.runtime.flow;

/**
 * Callback interface for observing flow execution lifecycle events.
 * <p>
 * Implementations can be used to record metrics, publish events, or
 * perform other side effects at key execution points. The engine
 * calls these methods on the execution thread — implementations
 * should be fast and non-blocking.
 *
 * @since 1.0.0
 */
public interface FlowExecutionListener {

    /**
     * Called when a flow execution starts.
     *
     * @param flowId the flow ID
     */
    void onExecutionStarted(String flowId);

    /**
     * Called when a flow execution completes (success, failure, or cancellation).
     *
     * @param flowId     the flow ID
     * @param status     final status (COMPLETED, FAILED, CANCELLED)
     * @param durationMs execution duration in milliseconds
     * @param isTest     whether this was a test execution
     */
    void onExecutionCompleted(String flowId, String status, long durationMs, boolean isTest);

    /**
     * Called when a single step completes within a flow execution.
     *
     * @param flowId     the flow ID
     * @param stateType  state type (Task, Choice, Wait, etc.)
     * @param resource   resource key for Task states (null for non-Task)
     * @param status     step outcome (SUCCEEDED, FAILED)
     * @param durationMs step duration in milliseconds
     */
    void onStepCompleted(String flowId, String stateType, String resource,
                         String status, long durationMs);

    /**
     * Called when a flow execution encounters an error.
     *
     * @param flowId    the flow ID
     * @param errorCode the error code or type
     */
    void onExecutionError(String flowId, String errorCode);

    /**
     * A no-op implementation that ignores all events.
     */
    FlowExecutionListener NOOP = new FlowExecutionListener() {
        @Override public void onExecutionStarted(String flowId) {}
        @Override public void onExecutionCompleted(String flowId, String status, long durationMs, boolean isTest) {}
        @Override public void onStepCompleted(String flowId, String stateType, String resource, String status, long durationMs) {}
        @Override public void onExecutionError(String flowId, String errorCode) {}
    };
}
