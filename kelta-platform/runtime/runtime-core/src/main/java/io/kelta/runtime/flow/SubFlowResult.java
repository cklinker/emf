package io.kelta.runtime.flow;

import java.util.List;
import java.util.Map;

/**
 * Result of executing a sub-flow (used by Map and Parallel state executors).
 * <p>
 * Beyond the final state data, this carries the list of errors that the sub-flow
 * caught via Catch policies. Without this, a sub-flow whose Catch redirects to a
 * "success" terminal looks identical to a sub-flow that ran cleanly — and a Map
 * iterator typically uses {@code Catch: [{ ErrorEquals: [States.ALL], Next: Done }]}
 * to prevent one bad item from killing the batch, which causes per-item failures
 * to vanish silently. {@link #caughtErrors()} lets the parent state surface those
 * swallowed errors.
 *
 * @param stateData    the sub-flow's final state data
 * @param caughtErrors errors intercepted by Catch policies inside the sub-flow
 * @since 1.0.0
 */
public record SubFlowResult(
    Map<String, Object> stateData,
    List<FlowExecutionContext.CaughtError> caughtErrors
) {

    public boolean hadCaughtError() {
        return caughtErrors != null && !caughtErrors.isEmpty();
    }
}
