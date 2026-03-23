package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Utility for structured security event logging.
 *
 * <p>All security-sensitive operations should log through this class to ensure
 * consistent formatting and easy aggregation in log management systems.
 *
 * <p>Events are logged as structured key-value pairs for JSON log parsing.
 */
public final class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("security.audit");

    private SecurityAuditLogger() {
    }

    public enum EventType {
        PASSWORD_CHANGED,
        PASSWORD_POLICY_UPDATED,
        LOGIN_FAILED,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        TOKEN_ISSUED,
        TOKEN_REVOKED,
        AUTH_FAILURE,
        MFA_CHALLENGE_SUCCESS,
        MFA_CHALLENGE_FAILED,
        MFA_ENROLLED,
        MFA_DISABLED,
        MFA_RESET,
        RECOVERY_CODE_USED
    }

    /**
     * Log a security event with structured context.
     *
     * @param eventType the type of security event
     * @param actorId   the user or system performing the action (or "system" for automated)
     * @param targetId  the entity being acted upon (userId, appId, etc.)
     * @param tenantId  the tenant context
     * @param result    "success" or "failure"
     * @param detail    additional context (nullable)
     */
    public static void log(EventType eventType, String actorId, String targetId,
                           String tenantId, String result, String detail) {
        try {
            MDC.put("security.event", eventType.name());
            MDC.put("security.actor", actorId != null ? actorId : "unknown");
            MDC.put("security.target", targetId != null ? targetId : "unknown");
            MDC.put("security.tenant", tenantId != null ? tenantId : "unknown");
            MDC.put("security.result", result);

            String message = String.format("security_event=%s actor=%s target=%s tenant=%s result=%s",
                    eventType, actorId, targetId, tenantId, result);
            if (detail != null && !detail.isBlank()) {
                message += " detail=" + detail;
            }

            if ("failure".equals(result)) {
                SecurityAuditLogger.log.warn(message);
            } else {
                SecurityAuditLogger.log.info(message);
            }
        } finally {
            MDC.remove("security.event");
            MDC.remove("security.actor");
            MDC.remove("security.target");
            MDC.remove("security.tenant");
            MDC.remove("security.result");
        }
    }
}
