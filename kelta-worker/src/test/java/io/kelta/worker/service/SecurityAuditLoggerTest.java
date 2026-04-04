package io.kelta.worker.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityAuditLogger Tests")
class SecurityAuditLoggerTest {

    @Nested
    @DisplayName("EventType Enum")
    class EventTypeTests {

        @Test
        void shouldHaveAllExpectedEventTypes() {
            assertEquals(17, SecurityAuditLogger.EventType.values().length);
            assertNotNull(SecurityAuditLogger.EventType.valueOf("PASSWORD_CHANGED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("PASSWORD_POLICY_UPDATED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("LOGIN_FAILED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("ACCOUNT_LOCKED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("ACCOUNT_UNLOCKED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("TOKEN_ISSUED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("TOKEN_REVOKED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("AUTH_FAILURE"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("MFA_CHALLENGE_SUCCESS"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("MFA_CHALLENGE_FAILED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("MFA_ENROLLED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("MFA_DISABLED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("MFA_RESET"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("RECOVERY_CODE_USED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("PASSWORD_RESET_ADMIN"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("PAT_CREATED"));
            assertNotNull(SecurityAuditLogger.EventType.valueOf("PAT_REVOKED"));
        }
    }

    @Nested
    @DisplayName("MDC Cleanup")
    class MdcCleanup {

        @Test
        void shouldCleanMdcAfterSuccessLog() {
            SecurityAuditLogger.log(
                    SecurityAuditLogger.EventType.TOKEN_ISSUED,
                    "actor-1", "target-1", "tenant-1", "success", null);

            assertNull(MDC.get("security.event"));
            assertNull(MDC.get("security.actor"));
            assertNull(MDC.get("security.target"));
            assertNull(MDC.get("security.tenant"));
            assertNull(MDC.get("security.result"));
        }

        @Test
        void shouldCleanMdcAfterFailureLog() {
            SecurityAuditLogger.log(
                    SecurityAuditLogger.EventType.LOGIN_FAILED,
                    "actor-1", "target-1", "tenant-1", "failure", "Bad password");

            assertNull(MDC.get("security.event"));
            assertNull(MDC.get("security.actor"));
            assertNull(MDC.get("security.target"));
            assertNull(MDC.get("security.tenant"));
            assertNull(MDC.get("security.result"));
        }
    }

    @Nested
    @DisplayName("Null Handling")
    class NullHandling {

        @Test
        void shouldHandleNullActorId() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.log(SecurityAuditLogger.EventType.AUTH_FAILURE,
                            null, "target", "tenant", "failure", null));
        }

        @Test
        void shouldHandleNullTargetId() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.log(SecurityAuditLogger.EventType.TOKEN_ISSUED,
                            "actor", null, "tenant", "success", null));
        }

        @Test
        void shouldHandleNullTenantId() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.log(SecurityAuditLogger.EventType.PASSWORD_CHANGED,
                            "actor", "target", null, "success", null));
        }

        @Test
        void shouldHandleNullDetail() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.log(SecurityAuditLogger.EventType.MFA_ENROLLED,
                            "actor", "target", "tenant", "success", null));
        }

        @Test
        void shouldHandleBlankDetail() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.log(SecurityAuditLogger.EventType.MFA_ENROLLED,
                            "actor", "target", "tenant", "success", "  "));
        }
    }
}
