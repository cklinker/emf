package com.emf.controlplane.lifecycle.handlers;

import com.emf.controlplane.lifecycle.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FieldLifecycleHandler Tests")
class FieldLifecycleHandlerTest {

    private FieldLifecycleHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FieldLifecycleHandler();
    }

    @Test
    @DisplayName("Should return 'fields' as collection name")
    void shouldReturnCollectionName() {
        assertEquals("fields", handler.getCollectionName());
    }

    @Nested
    @DisplayName("beforeCreate - Name Validation")
    class NameValidationTests {

        @Test
        @DisplayName("Should reject null name")
        void shouldRejectNullName() {
            Map<String, Object> record = new HashMap<>();

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
            assertEquals("name", result.getErrors().get(0).field());
            assertEquals("Field name is required", result.getErrors().get(0).message());
        }

        @Test
        @DisplayName("Should reject blank name")
        void shouldRejectBlankName() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
        }

        @ParameterizedTest
        @ValueSource(strings = {"id", "createdAt", "updatedAt", "createdBy", "updatedBy",
                "tenantId", "type", "attributes", "relationships"})
        @DisplayName("Should reject reserved field names")
        void shouldRejectReservedNames(String reservedName) {
            Map<String, Object> record = new HashMap<>();
            record.put("name", reservedName);

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().get(0).message().contains("reserved"));
        }

        @Test
        @DisplayName("Should reject name starting with number")
        void shouldRejectNameStartingWithNumber() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "1field");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject name with hyphens")
        void shouldRejectNameWithHyphens() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "my-field");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject name with spaces")
        void shouldRejectNameWithSpaces() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "my field");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept camelCase name")
        void shouldAcceptCamelCase() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "firstName");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept snake_case name")
        void shouldAcceptSnakeCase() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "first_name");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept simple lowercase name")
        void shouldAcceptSimpleName() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "email");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("beforeCreate - Type Validation")
    class TypeValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {"string", "number", "boolean", "date", "datetime",
                "reference", "array", "object", "STRING", "INTEGER", "DOUBLE",
                "BOOLEAN", "DATE", "DATETIME", "JSON", "PICKLIST", "CURRENCY",
                "EMAIL", "URL", "FORMULA", "LOOKUP"})
        @DisplayName("Should accept valid field types")
        void shouldAcceptValidTypes(String type) {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "testField");
            record.put("type", type);

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject invalid field type")
        void shouldRejectInvalidType() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "testField");
            record.put("type", "INVALID_TYPE");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
            assertEquals("type", result.getErrors().get(0).field());
            assertTrue(result.getErrors().get(0).message().contains("INVALID_TYPE"));
        }

        @Test
        @DisplayName("Should accept record without type")
        void shouldAcceptNoType() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "testField");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("beforeCreate - Defaults")
    class DefaultsTests {

        @Test
        @DisplayName("Should set active=true when not provided")
        void shouldSetActiveDefault() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "testField");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals(true, result.getFieldUpdates().get("active"));
        }

        @Test
        @DisplayName("Should not override existing active")
        void shouldNotOverrideActive() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", "testField");
            record.put("active", false);

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
                    handler.afterCreate(Map.of("name", "testField"), "tenant-1"));
        }

        @Test
        @DisplayName("afterDelete should not throw")
        void afterDeleteShouldNotThrow() {
            assertDoesNotThrow(() ->
                    handler.afterDelete("field-1", "tenant-1"));
        }
    }
}
