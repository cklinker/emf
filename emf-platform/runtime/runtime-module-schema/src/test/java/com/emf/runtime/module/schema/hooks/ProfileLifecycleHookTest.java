package com.emf.runtime.module.schema.hooks;

import com.emf.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProfileLifecycleHook")
class ProfileLifecycleHookTest {

    private final ProfileLifecycleHook hook = new ProfileLifecycleHook();

    @Test
    @DisplayName("Should target 'profiles' collection")
    void shouldTargetProfiles() {
        assertEquals("profiles", hook.getCollectionName());
    }

    @Nested
    @DisplayName("beforeCreate")
    class BeforeCreate {

        @Test
        @DisplayName("Should reject missing name")
        void shouldRejectMissingName() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(), "t1");
            assertFalse(result.isSuccess());
            assertEquals("name", result.getErrors().get(0).field());
        }

        @Test
        @DisplayName("Should reject blank name")
        void shouldRejectBlankName() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "  ")), "t1");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept valid profile name")
        void shouldAcceptValidName() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "Admin")), "t1");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should set default system to false")
        void shouldSetDefaultSystemFalse() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "Standard User")), "t1");
            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals(false, result.getFieldUpdates().get("system"));
        }

        @Test
        @DisplayName("Should not override existing system flag")
        void shouldNotOverrideExistingSystem() {
            Map<String, Object> record = new HashMap<>(Map.of("name", "Admin", "system", true));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
            assertFalse(result.hasFieldUpdates());
        }
    }
}
