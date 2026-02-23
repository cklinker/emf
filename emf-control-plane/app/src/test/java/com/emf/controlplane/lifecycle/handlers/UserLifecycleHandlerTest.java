package com.emf.controlplane.lifecycle.handlers;

import com.emf.controlplane.lifecycle.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserLifecycleHandler Tests")
class UserLifecycleHandlerTest {

    private UserLifecycleHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserLifecycleHandler();
    }

    @Test
    @DisplayName("Should return 'users' as collection name")
    void shouldReturnCollectionName() {
        assertEquals("users", handler.getCollectionName());
    }

    @Nested
    @DisplayName("beforeCreate")
    class BeforeCreateTests {

        @Test
        @DisplayName("Should reject null email")
        void shouldRejectNullEmail() {
            Map<String, Object> record = new HashMap<>();

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
            assertEquals("email", result.getErrors().get(0).field());
            assertEquals("Email is required", result.getErrors().get(0).message());
        }

        @Test
        @DisplayName("Should reject blank email")
        void shouldRejectBlankEmail() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "   ");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
            assertEquals("email", result.getErrors().get(0).field());
        }

        @Test
        @DisplayName("Should reject invalid email format - no @")
        void shouldRejectInvalidEmailNoAt() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "notanemail");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
            assertEquals("Invalid email format", result.getErrors().get(0).message());
        }

        @Test
        @DisplayName("Should reject invalid email format - no domain")
        void shouldRejectInvalidEmailNoDomain() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "user@");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept valid email")
        void shouldAcceptValidEmail() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "user@example.com");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should normalize email to lowercase")
        void shouldNormalizeEmail() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "User@Example.COM");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals("user@example.com", result.getFieldUpdates().get("email"));
        }

        @Test
        @DisplayName("Should set default locale when not provided")
        void shouldSetDefaultLocale() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "user@example.com");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            assertEquals("en_US", result.getFieldUpdates().get("locale"));
        }

        @Test
        @DisplayName("Should not override existing locale")
        void shouldNotOverrideLocale() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "user@example.com");
            record.put("locale", "fr_FR");
            record.put("timezone", "UTC");
            record.put("status", "ACTIVE");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            // No field updates — all defaults already set
            assertFalse(result.hasFieldUpdates());
        }

        @Test
        @DisplayName("Should set default timezone when not provided")
        void shouldSetDefaultTimezone() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "user@example.com");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            assertEquals("UTC", result.getFieldUpdates().get("timezone"));
        }

        @Test
        @DisplayName("Should set default status when not provided")
        void shouldSetDefaultStatus() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "user@example.com");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            assertEquals("ACTIVE", result.getFieldUpdates().get("status"));
        }
    }

    @Nested
    @DisplayName("beforeUpdate")
    class BeforeUpdateTests {

        @Test
        @DisplayName("Should accept update without email change")
        void shouldAcceptNoEmailChange() {
            Map<String, Object> record = new HashMap<>();
            record.put("firstName", "Updated");

            BeforeSaveResult result = handler.beforeUpdate("user-1", record, Map.of(), "tenant-1");

            assertTrue(result.isSuccess());
            assertFalse(result.hasFieldUpdates());
        }

        @Test
        @DisplayName("Should validate email format on update")
        void shouldValidateEmailOnUpdate() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "invalid-email");

            BeforeSaveResult result = handler.beforeUpdate("user-1", record, Map.of(), "tenant-1");

            assertFalse(result.isSuccess());
            assertEquals("email", result.getErrors().get(0).field());
        }

        @Test
        @DisplayName("Should normalize email on update")
        void shouldNormalizeEmailOnUpdate() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "New.User@Example.COM");

            BeforeSaveResult result = handler.beforeUpdate("user-1", record,
                    Map.of("email", "old@example.com"), "tenant-1");

            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals("new.user@example.com", result.getFieldUpdates().get("email"));
        }

        @Test
        @DisplayName("Should accept already lowercase email on update")
        void shouldAcceptLowercaseEmailOnUpdate() {
            Map<String, Object> record = new HashMap<>();
            record.put("email", "user@example.com");

            BeforeSaveResult result = handler.beforeUpdate("user-1", record, Map.of(), "tenant-1");

            assertTrue(result.isSuccess());
            assertFalse(result.hasFieldUpdates());
        }
    }

    @Nested
    @DisplayName("afterCreate / afterDelete")
    class AfterHookTests {

        @Test
        @DisplayName("afterCreate should not throw")
        void afterCreateShouldNotThrow() {
            assertDoesNotThrow(() ->
                    handler.afterCreate(Map.of("email", "user@example.com"), "tenant-1"));
        }

        @Test
        @DisplayName("afterDelete should not throw")
        void afterDeleteShouldNotThrow() {
            assertDoesNotThrow(() ->
                    handler.afterDelete("user-1", "tenant-1"));
        }
    }
}
