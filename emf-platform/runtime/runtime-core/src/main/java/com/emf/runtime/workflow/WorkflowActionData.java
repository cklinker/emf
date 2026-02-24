package com.emf.runtime.workflow;

/**
 * Immutable data representation of a workflow action.
 * <p>
 * This record replaces the JPA-based {@code WorkflowAction} entity from the control plane,
 * allowing the workflow engine to operate without JPA dependencies.
 *
 * @param id                the unique action ID
 * @param actionType        the action type key (e.g., "FIELD_UPDATE", "EMAIL_ALERT")
 * @param executionOrder    the order in which this action executes within its rule
 * @param config            the action configuration as a JSON string
 * @param active            whether the action is active
 * @param retryCount        the number of retries on failure (0 = no retry)
 * @param retryDelaySeconds the delay between retries in seconds
 * @param retryBackoff      the backoff strategy ("FIXED" or "EXPONENTIAL")
 *
 * @since 1.0.0
 */
public record WorkflowActionData(
    String id,
    String actionType,
    int executionOrder,
    String config,
    boolean active,
    int retryCount,
    int retryDelaySeconds,
    String retryBackoff
) {

    /**
     * Creates an action with default retry settings.
     */
    public static WorkflowActionData of(String id, String actionType, int executionOrder,
                                          String config, boolean active) {
        return new WorkflowActionData(id, actionType, executionOrder, config, active,
                0, 60, "FIXED");
    }
}
