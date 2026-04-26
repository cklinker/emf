package io.kelta.worker.service.credential;

/**
 * Audit context for a single credential resolution. Captures who is asking,
 * which flow run is running, and a free-form purpose tag (e.g.
 * {@code "CALL_API:salesforce-prod"}). The values flow into the
 * {@code setup_audit_trail} row so security reviewers can trace every
 * decryption back to a source.
 *
 * <p>Pass {@code null} for any value that is genuinely not available.
 */
public record ResolutionContext(
    String workflowRuleId,
    String executionLogId,
    String userId,
    String purpose
) {

    /** Convenience for cases that don't have flow context (e.g., admin tests). */
    public static ResolutionContext forUser(String userId, String purpose) {
        return new ResolutionContext(null, null, userId, purpose);
    }

    /** Convenience for flow-driven resolution. */
    public static ResolutionContext forFlow(String workflowRuleId, String executionLogId, String purpose) {
        return new ResolutionContext(workflowRuleId, executionLogId, null, purpose);
    }
}
