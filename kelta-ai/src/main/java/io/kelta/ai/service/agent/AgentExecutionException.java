package io.kelta.ai.service.agent;

/**
 * Thrown when a governed agent cannot run: it is disabled, or the tenant's monthly AI token limit is
 * already exhausted. The controller maps it to an appropriate 4xx status.
 */
public class AgentExecutionException extends RuntimeException {

    /** Distinguishes the failure so the controller can choose the right HTTP status. */
    public enum Reason {
        AGENT_DISABLED,
        TOKEN_LIMIT_EXCEEDED
    }

    private final Reason reason;

    public AgentExecutionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
