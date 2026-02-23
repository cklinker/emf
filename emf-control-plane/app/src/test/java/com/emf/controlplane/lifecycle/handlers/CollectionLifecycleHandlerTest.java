package com.emf.controlplane.lifecycle.handlers;

import com.emf.controlplane.lifecycle.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CollectionLifecycleHandler Tests")
class CollectionLifecycleHandlerTest {

    private CollectionLifecycleHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CollectionLifecycleHandler();
    }

    @Test
    @DisplayName("Should return 'collections' as collection name")
    void shouldReturnCollectionName() {
        assertEquals("collections", handler.getCollectionName());
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
            assertTrue(result.hasErrors());
            assertEquals("name", result.getErrors().get(0).field());
        }

        @Test
        @DisplayName("Should reject blank name")
        void shouldRejectBlankName() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "  ");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
            assertEquals("name", result.getErrors().get(0).field());
        }

        @Test
        @DisplayName("Should reject name starting with number")
        void shouldRejectNameStartingWithNumber() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "123-invalid");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
            assertEquals("name", result.getErrors().get(0).field());
        }

        @Test
        @DisplayName("Should reject name with uppercase letters")
        void shouldRejectUppercaseName() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "MyCollection");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject name with spaces")
        void shouldRejectNameWithSpaces() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "my collection");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept valid lowercase-hyphen name")
        void shouldAcceptValidName() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "my-collection");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should set currentVersion=1 when not provided")
        void shouldSetDefaultVersion() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "products");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals(1L, result.getFieldUpdates().get("currentVersion"));
        }

        @Test
        @DisplayName("Should not override existing currentVersion")
        void shouldNotOverrideVersion() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "products");
            record.put("currentVersion", 5L);
            record.put("active", true);
            record.put("path", "/api/products");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            // All defaults are already set, so no field updates
            assertFalse(result.hasFieldUpdates());
        }

        @Test
        @DisplayName("Should set active=true when not provided")
        void shouldSetDefaultActive() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "orders");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            assertEquals(true, result.getFieldUpdates().get("active"));
        }

        @Test
        @DisplayName("Should generate API path when not provided")
        void shouldGenerateApiPath() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "products");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            assertEquals("/api/products", result.getFieldUpdates().get("path"));
        }

        @Test
        @DisplayName("Should not override existing path")
        void shouldNotOverridePath() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "products");
            record.put("path", "/custom/path");
            record.put("currentVersion", 1L);
            record.put("active", true);

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

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
                    handler.afterCreate(Map.of("name", "test"), "tenant-1"));
        }

        @Test
        @DisplayName("afterDelete should not throw")
        void afterDeleteShouldNotThrow() {
            assertDoesNotThrow(() ->
                    handler.afterDelete("col-1", "tenant-1"));
        }
    }
}
