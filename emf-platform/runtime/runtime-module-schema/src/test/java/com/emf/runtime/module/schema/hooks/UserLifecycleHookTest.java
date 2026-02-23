package com.emf.runtime.module.schema.hooks;

import com.emf.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserLifecycleHook")
class UserLifecycleHookTest {

    private final UserLifecycleHook hook = new UserLifecycleHook();

    @Test
    @DisplayName("Should target 'users' collection")
    void shouldTargetUsers() {
        assertEquals("users", hook.getCollectionName());
    }

    @Nested
    @DisplayName("beforeCreate")
    class BeforeCreate {

        @Test
        @DisplayName("Should reject missing email")
        void shouldRejectMissingEmail() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(), "t1");
            assertFalse(result.isSuccess());
            assertEquals("email", result.getErrors().get(0).field());
        }

        @Test
        @DisplayName("Should reject blank email")
        void shouldRejectBlankEmail() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("email", "  ")), "t1");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject invalid email format")
        void shouldRejectInvalidEmail() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("email", "not-an-email")), "t1");
            assertFalse(result.isSuccess());
            assertEquals("email", result.getErrors().get(0).field());
            assertTrue(result.getErrors().get(0).message().contains("email format"));
        }

        @Test
        @DisplayName("Should accept valid email")
        void shouldAcceptValidEmail() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("email", "user@example.com")), "t1");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should normalize email to lowercase")
        void shouldNormalizeEmail() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("email", "User@Example.COM")), "t1");
            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals("user@example.com", result.getFieldUpdates().get("email"));
        }

        @Test
        @DisplayName("Should set default locale to en_US")
        void shouldSetDefaultLocale() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("email", "user@test.com")), "t1");
            assertTrue(result.isSuccess());
            assertEquals("en_US", result.getFieldUpdates().get("locale"));
        }

        @Test
        @DisplayName("Should set default timezone to UTC")
        void shouldSetDefaultTimezone() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("email", "user@test.com")), "t1");
            assertTrue(result.isSuccess());
            assertEquals("UTC", result.getFieldUpdates().get("timezone"));
        }

        @Test
        @DisplayName("Should set default status to ACTIVE")
        void shouldSetDefaultStatus() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("email", "user@test.com")), "t1");
            assertTrue(result.isSuccess());
            assertEquals("ACTIVE", result.getFieldUpdates().get("status"));
        }

        @Test
        @DisplayName("Should not override existing locale")
        void shouldNotOverrideExistingLocale() {
            Map<String, Object> record = new HashMap<>(Map.of("email", "user@test.com", "locale", "fr_FR"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
            assertNull(result.getFieldUpdates().get("locale"));
        }

        @Test
        @DisplayName("Should not override existing timezone")
        void shouldNotOverrideExistingTimezone() {
            Map<String, Object> record = new HashMap<>(Map.of("email", "user@test.com", "timezone", "US/Pacific"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
            assertNull(result.getFieldUpdates().get("timezone"));
        }

        @Test
        @DisplayName("Should not override existing status")
        void shouldNotOverrideExistingStatus() {
            Map<String, Object> record = new HashMap<>(Map.of("email", "user@test.com", "status", "PENDING"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
            assertNull(result.getFieldUpdates().get("status"));
        }
    }

    @Nested
    @DisplayName("beforeUpdate")
    class BeforeUpdate {

        @Test
        @DisplayName("Should accept update without email change")
        void shouldAcceptUpdateWithoutEmail() {
            Map<String, Object> record = new HashMap<>(Map.of("name", "Updated Name"));
            Map<String, Object> previous = Map.of("email", "user@test.com", "name", "Old Name");
            BeforeSaveResult result = hook.beforeUpdate("id1", record, previous, "t1");
            assertTrue(result.isSuccess());
            assertFalse(result.hasFieldUpdates());
        }

        @Test
        @DisplayName("Should reject invalid email on update")
        void shouldRejectInvalidEmailOnUpdate() {
            Map<String, Object> record = new HashMap<>(Map.of("email", "bad-email"));
            Map<String, Object> previous = Map.of("email", "user@test.com");
            BeforeSaveResult result = hook.beforeUpdate("id1", record, previous, "t1");
            assertFalse(result.isSuccess());
            assertEquals("email", result.getErrors().get(0).field());
        }

        @Test
        @DisplayName("Should normalize email on update")
        void shouldNormalizeEmailOnUpdate() {
            Map<String, Object> record = new HashMap<>(Map.of("email", "User@Example.COM"));
            Map<String, Object> previous = Map.of("email", "old@test.com");
            BeforeSaveResult result = hook.beforeUpdate("id1", record, previous, "t1");
            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals("user@example.com", result.getFieldUpdates().get("email"));
        }

        @Test
        @DisplayName("Should accept already lowercase email on update")
        void shouldAcceptLowercaseEmailOnUpdate() {
            Map<String, Object> record = new HashMap<>(Map.of("email", "user@example.com"));
            Map<String, Object> previous = Map.of("email", "old@test.com");
            BeforeSaveResult result = hook.beforeUpdate("id1", record, previous, "t1");
            assertTrue(result.isSuccess());
            assertFalse(result.hasFieldUpdates());
        }
    }
}
