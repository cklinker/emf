package com.emf.runtime.flow;

import java.util.List;

/**
 * Catch policy for Task states. When a task fails with a matching error
 * and all retries are exhausted, the engine transitions to the specified
 * fallback state instead of failing the execution.
 *
 * @param errorEquals  list of error codes that trigger this catch (e.g., "States.ALL")
 * @param resultPath   JSONPath where the error details are placed in state data (default: "$")
 * @param next         the state ID to transition to on catch
 * @since 1.0.0
 */
public record CatchPolicy(
    List<String> errorEquals,
    String resultPath,
    String next
) {

    /**
     * Returns true if this policy matches the given error code.
     * "States.ALL" matches any error.
     */
    public boolean matches(String errorCode) {
        if (errorEquals == null || errorEquals.isEmpty()) {
            return false;
        }
        return errorEquals.contains("States.ALL") || errorEquals.contains(errorCode);
    }
}
