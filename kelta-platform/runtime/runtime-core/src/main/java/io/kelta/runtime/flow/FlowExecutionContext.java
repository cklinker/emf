package io.kelta.runtime.flow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable execution context that tracks state as the engine transitions
 * between states in a flow definition.
 *
 * @since 1.0.0
 */
public class FlowExecutionContext {

    private final String executionId;
    private final String tenantId;
    private final String flowId;
    private final String userId;
    private final FlowDefinition definition;
    private final Instant startedAt;
    private final List<CaughtError> caughtErrors = new ArrayList<>();

    private Map<String, Object> stateData;
    private String currentStateId;
    private int stepCount;
    private int failedCount;
    private boolean cancelled;
    private boolean completed;
    private String finalStatus;

    public FlowExecutionContext(String executionId, String tenantId, String flowId,
                                String userId, FlowDefinition definition,
                                Map<String, Object> initialState) {
        this.executionId = executionId;
        this.tenantId = tenantId;
        this.flowId = flowId;
        this.userId = userId;
        this.definition = definition;
        this.stateData = initialState != null ? new LinkedHashMap<>(initialState) : new LinkedHashMap<>();
        this.currentStateId = definition.startAt();
        this.stepCount = 0;
        this.startedAt = Instant.now();
        this.cancelled = false;
        this.completed = false;
        this.finalStatus = null;
    }

    public String executionId() { return executionId; }
    public String tenantId() { return tenantId; }
    public String flowId() { return flowId; }
    public String userId() { return userId; }
    public FlowDefinition definition() { return definition; }
    public Instant startedAt() { return startedAt; }
    public String currentStateId() { return currentStateId; }
    public int stepCount() { return stepCount; }
    public boolean isCancelled() { return cancelled; }

    public Map<String, Object> stateData() { return stateData; }

    public void setStateData(Map<String, Object> stateData) {
        this.stateData = stateData != null ? new LinkedHashMap<>(stateData) : new LinkedHashMap<>();
    }

    public void setCurrentStateId(String stateId) {
        this.currentStateId = stateId;
    }

    public void incrementStepCount() {
        this.stepCount++;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCompleted() { return completed; }
    public String finalStatus() { return finalStatus; }

    public void markCompleted(String status) {
        this.completed = true;
        this.finalStatus = status;
    }

    /**
     * Returns the current state definition.
     */
    public StateDefinition currentState() {
        return definition.getState(currentStateId);
    }

    /**
     * Returns elapsed time in milliseconds since execution started.
     */
    public int elapsedMs() {
        return (int) (System.currentTimeMillis() - startedAt.toEpochMilli());
    }

    /**
     * Records an error that was caught by a Catch policy. The execution still
     * proceeds to the catch's next state, but the swallowed error is preserved
     * so callers (e.g. Map state) can detect that an iteration had a failure
     * even though it was recovered from.
     */
    public void recordCaughtError(String stateId, String errorCode, String errorMessage) {
        caughtErrors.add(new CaughtError(stateId, errorCode, errorMessage));
    }

    /**
     * Returns the list of errors caught during this execution context's lifetime.
     */
    public List<CaughtError> caughtErrors() {
        return caughtErrors;
    }

    /**
     * Returns the running count of failed iterations attributed to this context
     * (incremented by states such as Map when their sub-flows record caught errors).
     */
    public int failedCount() {
        return failedCount;
    }

    public void addFailedCount(int n) {
        this.failedCount += n;
    }

    /**
     * Describes an error that a Catch policy intercepted.
     */
    public record CaughtError(String stateId, String errorCode, String errorMessage) {}
}
