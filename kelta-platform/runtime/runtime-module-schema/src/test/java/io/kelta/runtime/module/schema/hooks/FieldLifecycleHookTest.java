package io.kelta.runtime.module.schema.hooks;

import io.kelta.runtime.workflow.BeforeSaveResult;
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

        @Test
        @DisplayName("Should normalize lowercase 'rollup_summary' to canonical 'ROLLUP_SUMMARY'")
        void shouldNormalizeRollupSummary() {
            Map<String, Object> record = new HashMap<>(Map.of("name", "total", "type", "rollup_summary"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals("ROLLUP_SUMMARY", result.getFieldUpdates().get("type"));
        }

        @Test
        @DisplayName("Should map UI synonym 'number' to canonical 'DOUBLE'")
        void shouldMapNumberToDouble() {
            Map<String, Object> record = new HashMap<>(Map.of("name", "amount", "type", "number"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
            assertEquals("DOUBLE", result.getFieldUpdates().get("type"));
        }

        @Test
        @DisplayName("Should map UI synonym 'reference' to canonical 'MASTER_DETAIL'")
        void shouldMapReferenceToMasterDetail() {
            Map<String, Object> record = new HashMap<>(Map.of("name", "owner", "type", "reference"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
            assertEquals("MASTER_DETAIL", result.getFieldUpdates().get("type"));
        }

        @Test
        @DisplayName("Should accept new long-form text and vector types in lowercase")
        void shouldAcceptLongFormTextAndVectorLowercase() {
            for (String t : new String[]{"text", "rich_text", "vector"}) {
                Map<String, Object> record = new HashMap<>(Map.of("name", "f", "type", t));
                BeforeSaveResult result = hook.beforeCreate(record, "t1");
                assertTrue(result.isSuccess(),
                        "lowercase '" + t + "' should pass FieldLifecycleHook");
                assertEquals(t.toUpperCase(), result.getFieldUpdates().get("type"),
                        "type '" + t + "' should canonicalize to upper");
            }
        }

        @Test
        @DisplayName("Should accept new long-form text and vector types in canonical (uppercase) form")
        void shouldAcceptLongFormTextAndVectorCanonical() {
            for (String t : new String[]{"TEXT", "RICH_TEXT", "VECTOR"}) {
                Map<String, Object> record = new HashMap<>(Map.of("name", "f", "type", t));
                BeforeSaveResult result = hook.beforeCreate(record, "t1");
                assertTrue(result.isSuccess(),
                        "canonical '" + t + "' should pass FieldLifecycleHook");
            }
        }

        @Test
        @DisplayName("Should leave already-canonical type unchanged")
        void shouldLeaveCanonicalTypeUnchanged() {
            Map<String, Object> record = new HashMap<>(Map.of(
                    "name", "total", "type", "ROLLUP_SUMMARY", "active", true));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
            assertFalse(result.hasFieldUpdates(),
                    "canonical type + active set should not produce updates");
        }
    }

    @Nested
    @DisplayName("beforeUpdate")
    class BeforeUpdate {

        @Test
        @DisplayName("Should normalize a lowercase type passed during update")
        void shouldNormalizeTypeOnUpdate() {
            Map<String, Object> record = new HashMap<>(Map.of("type", "rollup_summary"));
            BeforeSaveResult result = hook.beforeUpdate("id-1", record, Map.of(), "t1");
            assertTrue(result.isSuccess());
            assertEquals("ROLLUP_SUMMARY", result.getFieldUpdates().get("type"));
        }

        @Test
        @DisplayName("Should pass through canonical type without updates")
        void shouldPassThroughCanonical() {
            Map<String, Object> record = new HashMap<>(Map.of("type", "STRING"));
            BeforeSaveResult result = hook.beforeUpdate("id-1", record, Map.of(), "t1");
            assertTrue(result.isSuccess());
            assertFalse(result.hasFieldUpdates());
        }

        @Test
        @DisplayName("Should reject invalid type on update")
        void shouldRejectInvalidTypeOnUpdate() {
            Map<String, Object> record = new HashMap<>(Map.of("type", "BOGUS"));
            BeforeSaveResult result = hook.beforeUpdate("id-1", record, Map.of(), "t1");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should be a no-op when type is absent")
        void shouldNoOpWithoutType() {
            BeforeSaveResult result = hook.beforeUpdate("id-1",
                    new HashMap<>(Map.of("displayName", "X")), Map.of(), "t1");
            assertTrue(result.isSuccess());
            assertFalse(result.hasFieldUpdates());
        }
    }
}
