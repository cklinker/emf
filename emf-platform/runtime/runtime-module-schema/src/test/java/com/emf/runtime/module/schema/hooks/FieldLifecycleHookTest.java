package com.emf.runtime.module.schema.hooks;

import com.emf.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FieldLifecycleHook")
class FieldLifecycleHookTest {

    private final FieldLifecycleHook hook = new FieldLifecycleHook();

    @Test
    @DisplayName("Should target 'fields' collection")
    void shouldTargetFields() {
        assertEquals("fields", hook.getCollectionName());
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
        @DisplayName("Should reject reserved name 'id'")
        void shouldRejectReservedNameId() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "id")), "t1");
            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().get(0).message().contains("reserved"));
        }

        @Test
        @DisplayName("Should reject reserved name 'createdAt'")
        void shouldRejectReservedNameCreatedAt() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "createdAt")), "t1");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject reserved name 'tenantId'")
        void shouldRejectReservedNameTenantId() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "tenantId")), "t1");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject name starting with number")
        void shouldRejectNameStartingWithNumber() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "1field")), "t1");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject name with hyphens")
        void shouldRejectNameWithHyphens() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "my-field")), "t1");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept valid camelCase name")
        void shouldAcceptCamelCaseName() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "myField")), "t1");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept valid snake_case name")
        void shouldAcceptSnakeCaseName() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "my_field")), "t1");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject invalid field type")
        void shouldRejectInvalidFieldType() {
            Map<String, Object> record = new HashMap<>(Map.of("name", "status", "type", "INVALID_TYPE"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertFalse(result.isSuccess());
            assertEquals("type", result.getErrors().get(0).field());
        }

        @Test
        @DisplayName("Should accept valid lowercase field type 'string'")
        void shouldAcceptLowercaseStringType() {
            Map<String, Object> record = new HashMap<>(Map.of("name", "status", "type", "string"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept valid uppercase field type 'PICKLIST'")
        void shouldAcceptPicklistType() {
            Map<String, Object> record = new HashMap<>(Map.of("name", "category", "type", "PICKLIST"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept valid field type 'FORMULA'")
        void shouldAcceptFormulaType() {
            Map<String, Object> record = new HashMap<>(Map.of("name", "total", "type", "FORMULA"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should set default active to true")
        void shouldSetDefaultActive() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "status")), "t1");
            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals(true, result.getFieldUpdates().get("active"));
        }

        @Test
        @DisplayName("Should not override existing active value")
        void shouldNotOverrideExistingActive() {
            Map<String, Object> record = new HashMap<>(Map.of("name", "status", "active", false));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
            assertFalse(result.hasFieldUpdates());
        }

        @Test
        @DisplayName("Should allow field with no type specified")
        void shouldAllowNoType() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("name", "description")), "t1");
            assertTrue(result.isSuccess());
        }
    }
}
