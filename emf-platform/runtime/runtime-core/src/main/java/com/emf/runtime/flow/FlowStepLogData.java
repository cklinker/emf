package com.emf.runtime.flow;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable data record representing a flow step execution log entry.
 *
 * @param id             unique step log ID
 * @param executionId    the parent flow execution ID
 * @param stateId        the state ID within the flow definition
 * @param stateName      human-readable state name
 * @param stateType      state type (Task, Choice, etc.)
 * @param status         step status (RUNNING, SUCCEEDED, FAILED, SKIPPED)
 * @param inputSnapshot  state data at step entry (JSON snapshot)
 * @param outputSnapshot state data at step exit (JSON snapshot)
 * @param errorMessage   error message if step failed
 * @param errorCode      error code if step failed
 * @param attemptNumber  retry attempt number (1-based)
 * @param durationMs     step execution duration in milliseconds
 * @param startedAt      when the step started
 * @param completedAt    when the step completed
 * @since 1.0.0
 */
public record FlowStepLogData(
    String id,
    String executionId,
    String stateId,
    String stateName,
    String stateType,
    String status,
    Map<String, Object> inputSnapshot,
    Map<String, Object> outputSnapshot,
    String errorMessage,
    String errorCode,
    int attemptNumber,
    Integer durationMs,
    Instant startedAt,
    Instant completedAt
) {

    /** Step is currently executing. */
    public static final String STATUS_RUNNING = "RUNNING";

    /** Step completed successfully. */
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";

    /** Step failed with an error. */
    public static final String STATUS_FAILED = "FAILED";

    /** Step was skipped (e.g., untaken branch). */
    public static final String STATUS_SKIPPED = "SKIPPED";
}
