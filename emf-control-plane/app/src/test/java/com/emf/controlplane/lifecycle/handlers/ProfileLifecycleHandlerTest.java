package com.emf.controlplane.lifecycle.handlers;

import com.emf.controlplane.lifecycle.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProfileLifecycleHandler Tests")
class ProfileLifecycleHandlerTest {

    private ProfileLifecycleHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProfileLifecycleHandler();
    }

    @Test
    @DisplayName("Should return 'profiles' as collection name")
    void shouldReturnCollectionName() {
        assertEquals("profiles", handler.getCollectionName());
    }

    @Nested
    @DisplayName("beforeCreate")
    class BeforeCreateTests {

        @Test
        @DisplayName("Should reject null name")
        void shouldRejectNullName() {
            Map<String, Object> record = new HashMap<>();

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
            assertEquals("name", result.getErrors().get(0).field());
            assertEquals("Profile name is required", result.getErrors().get(0).message());
        }

        @Test
        @DisplayName("Should reject blank name")
        void shouldRejectBlankName() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "   ");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept valid name")
        void shouldAcceptValidName() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "Standard User");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should set system=false when not provided")
        void shouldSetSystemDefault() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "Custom Profile");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals(false, result.getFieldUpdates().get("system"));
        }

        @Test
        @DisplayName("Should not override existing system flag")
        void shouldNotOverrideSystem() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "System Admin");
            record.put("system", true);

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            assertFalse(result.hasFieldUpdates());
        }
    }

    @Nested
    @DisplayName("afterCreate / afterUpdate")
    class AfterHookTests {

        @Test
        @DisplayName("afterCreate should not throw")
        void afterCreateShouldNotThrow() {
            assertDoesNotThrow(() ->
                    handler.afterCreate(Map.of("name", "Standard"), "tenant-1"));
        }

        @Test
        @DisplayName("afterUpdate should not throw")
        void afterUpdateShouldNotThrow() {
            assertDoesNotThrow(() ->
                    handler.afterUpdate("prof-1", Map.of("name", "Updated"),
                            Map.of("name", "Original"), "tenant-1"));
        }
    }
}
