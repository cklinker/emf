package com.emf.runtime.flow;

import java.util.Map;

/**
 * Parsed representation of a flow definition JSON.
 * <p>
 * A flow definition describes a state machine: a set of named states,
 * a start state, and transitions between them. The engine traverses
 * states from {@code startAt} until it reaches a terminal state
 * (Succeed or Fail) or the execution is cancelled.
 *
 * @param comment   optional human-readable description of the flow
 * @param startAt   the ID of the first state to execute
 * @param states    map of state ID to state definition
 * @param metadata  optional metadata (e.g., canvas node positions for the UI)
 * @since 1.0.0
 */
public record FlowDefinition(
    String comment,
    String startAt,
    Map<String, StateDefinition> states,
    Map<String, Object> metadata
) {

    /**
     * Returns the state definition for the given state ID.
     *
     * @param stateId the state identifier
     * @return the state definition, or null if not found
     */
    public StateDefinition getState(String stateId) {
        return states != null ? states.get(stateId) : null;
    }

    /**
     * Returns true if the definition contains the given state ID.
     */
    public boolean hasState(String stateId) {
        return states != null && states.containsKey(stateId);
    }
}
