package com.emf.controlplane.service.workflow;

import java.util.Map;

/**
 * Result returned by every {@link ActionHandler} after execution.
 * Contains success/failure status, optional error message, and output data.
 */
public record ActionResult(
    boolean successful,
    String errorMessage,
    Map<String, Object> outputData
) {

    /**
     * Creates a successful result with no output data.
     */
    public static ActionResult success() {
        return new ActionResult(true, null, Map.of());
    }

    /**
     * Creates a successful result with output data.
     */
    public static ActionResult success(Map<String, Object> outputData) {
        return new ActionResult(true, null, outputData);
    }

    /**
     * Creates a failure result with an error message.
     */
    public static ActionResult failure(String errorMessage) {
        return new ActionResult(false, errorMessage, Map.of());
    }

    /**
     * Creates a failure result from an exception.
     */
    public static ActionResult failure(Exception ex) {
        return new ActionResult(false, ex.getMessage(), Map.of());
    }
}
