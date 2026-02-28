package com.emf.runtime.flow;

/**
 * Thrown when a flow definition is malformed, missing required fields,
 * or otherwise cannot be parsed into a valid {@link FlowDefinition}.
 *
 * @since 1.0.0
 */
public class FlowDefinitionException extends RuntimeException {

    public FlowDefinitionException(String message) {
        super(message);
    }

    public FlowDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
