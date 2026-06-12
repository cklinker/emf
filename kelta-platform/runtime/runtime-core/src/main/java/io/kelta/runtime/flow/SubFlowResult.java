package io.kelta.runtime.flow;

import java.util.List;
import java.util.Map;

/**
 * Result of executing a sub-flow (used by Map, Parallel, and InvokeFlow state executors).
 * <p>
 * Beyond the final state data, this carries the list of errors that the sub-flow
 * caught via Catch policies. Without this, a sub-flow whose Catch redirects to a
 * "success" terminal looks identical to a sub-flow that ran cleanly — and a Map
 * iterator typically uses {@code Catch: [{ ErrorEquals: [States.ALL], Next: Done }]}
 * to prevent one bad item from killing the batch, which causes per-item failures
 * to vanish silently. {@link #caughtErrors()} lets the parent state surface those
 * swallowed errors.
 * <p>
 * It also carries any <em>uncaught</em> terminal failure from the sub-flow so
 * the parent state can propagate it. {@code InvokeFlow} treats an uncaught
 * sub-flow failure as its own failure (which the parent may then Catch), while
 * Map/Parallel currently still treat each branch's data as success — only the
 * caught-error list is surfaced for those.
 *
 * @param stateData         the sub-flow's final state data
 * @param caughtErrors      errors intercepted by Catch policies inside the sub-flow
 * @param uncaughtErrorCode error code from the sub-flow's terminal failure, if any
 * @param uncaughtErrorMessage cause message from the sub-flow's terminal failure, if any
 * @since 1.0.0
 */
public record SubFlowResult(
    Map<String, Object> stateData,
    List<FlowExecutionContext.CaughtError> caughtErrors,
    String uncaughtErrorCode,
    String uncaughtErrorMessage
) {

    public SubFlowResult(Map<String, Object> stateData,
                         List<FlowExecutionContext.CaughtError> caughtErrors) {
        this(stateData, caughtErrors, null, null);
    }

    public boolean hadCaughtError() {
        return caughtErrors != null && !caughtErrors.isEmpty();
    }

    public boolean hadUncaughtError() {
        return uncaughtErrorCode != null;
    }
}
