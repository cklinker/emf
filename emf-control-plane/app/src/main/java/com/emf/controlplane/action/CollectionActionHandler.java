package com.emf.controlplane.action;

import java.util.Map;

/**
 * Interface for collection-scoped action handlers.
 *
 * <p>An action handler implements a specific operation on a system collection,
 * such as activating a validation rule, rotating a connected app secret,
 * or manually executing a workflow rule.
 *
 * <p>Each handler is auto-discovered by Spring and registered in the
 * {@link CollectionActionRegistry}.
 *
 * @since 1.0.0
 */
public interface CollectionActionHandler {

    /**
     * Returns the collection name this handler applies to.
     *
     * @return the collection name (e.g., "validation-rules")
     */
    String getCollectionName();

    /**
     * Returns the action name.
     *
     * @return the action name (e.g., "activate")
     */
    String getActionName();

    /**
     * Returns whether this action requires a record ID (instance action)
     * or operates on the collection as a whole (collection action).
     *
     * @return true if this action requires a record ID
     */
    boolean isInstanceAction();

    /**
     * Executes the action.
     *
     * @param id the record ID (null for collection-level actions)
     * @param body the request body (may be null or empty)
     * @param tenantId the tenant ID from the request
     * @param userId the user ID from the request
     * @return the result of the action (will be serialized as JSON response body)
     */
    Object execute(String id, Map<String, Object> body, String tenantId, String userId);
}
