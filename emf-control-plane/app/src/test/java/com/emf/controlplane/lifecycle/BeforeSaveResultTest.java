package com.emf.controlplane.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BeforeSaveResult Tests")
class BeforeSaveResultTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("ok() should return successful result with no updates")
        void okShouldReturnSuccessful() {
            BeforeSaveResult result = BeforeSaveResult.ok();

            assertTrue(result.isSuccess());
            assertFalse(result.hasErrors());
            assertFalse(result.hasFieldUpdates());
            assertTrue(result.getFieldUpdates().isEmpty());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("withFieldUpdates() should return successful result with updates")
        void withFieldUpdatesShouldReturnUpdates() {
            Map<String, Object> updates = Map.of("status", "ACTIVE", "version", 1L);
            BeforeSaveResult result = BeforeSaveResult.withFieldUpdates(updates);

            assertTrue(result.isSuccess());
            assertFalse(result.hasErrors());
            assertTrue(result.hasFieldUpdates());
            assertEquals("ACTIVE", result.getFieldUpdates().get("status"));
            assertEquals(1L, result.getFieldUpdates().get("version"));
        }

        @Test
        @DisplayName("withFieldUpdates() should defensively copy the map")
        void withFieldUpdatesShouldDefensivelyCopy() {
            java.util.HashMap<String, Object> mutable = new java.util.HashMap<>();
            mutable.put("key", "value");
            BeforeSaveResult result = BeforeSaveResult.withFieldUpdates(mutable);

            mutable.put("extra", "data");
            assertFalse(result.getFieldUpdates().containsKey("extra"));
        }

        @Test
        @DisplayName("error() should return failed result with single error")
        void errorShouldReturnFailed() {
            BeforeSaveResult result = BeforeSaveResult.error("email", "Invalid email format");

            assertFalse(result.isSuccess());
            assertTrue(result.hasErrors());
            assertFalse(result.hasFieldUpdates());
            assertEquals(1, result.getErrors().size());
            assertEquals("email", result.getErrors().get(0).field());
            assertEquals("Invalid email format", result.getErrors().get(0).message());
        }

        @Test
        @DisplayName("error() should support null field for record-level errors")
        void errorShouldSupportNullField() {
            BeforeSaveResult result = BeforeSaveResult.error(null, "Record is invalid");

            assertFalse(result.isSuccess());
            assertTrue(result.hasErrors());
            assertNull(result.getErrors().get(0).field());
            assertEquals("Record is invalid", result.getErrors().get(0).message());
        }

        @Test
        @DisplayName("errors() should return failed result with multiple errors")
        void errorsShouldReturnMultiple() {
            List<BeforeSaveResult.ValidationError> errors = List.of(
                    new BeforeSaveResult.ValidationError("name", "Name is required"),
                    new BeforeSaveResult.ValidationError("email", "Email is required")
            );
            BeforeSaveResult result = BeforeSaveResult.errors(errors);

            assertFalse(result.isSuccess());
            assertTrue(result.hasErrors());
            assertEquals(2, result.getErrors().size());
        }
    }

    @Nested
    @DisplayName("toResponseMap()")
    class ToResponseMapTests {

        @Test
        @DisplayName("ok result should produce map with empty field updates")
        void okResultResponseMap() {
            Map<String, Object> map = BeforeSaveResult.ok().toResponseMap();

            assertNotNull(map.get("fieldUpdates"));
            assertTrue(((Map<?, ?>) map.get("fieldUpdates")).isEmpty());
            assertFalse(map.containsKey("errors"));
        }

        @Test
        @DisplayName("field updates should appear in response map")
        void fieldUpdatesInResponseMap() {
            Map<String, Object> map = BeforeSaveResult
                    .withFieldUpdates(Map.of("status", "ACTIVE"))
                    .toResponseMap();

            @SuppressWarnings("unchecked")
            Map<String, Object> updates = (Map<String, Object>) map.get("fieldUpdates");
            assertEquals("ACTIVE", updates.get("status"));
        }

        @Test
        @DisplayName("errors should appear in response map")
        void errorsInResponseMap() {
            Map<String, Object> map = BeforeSaveResult
                    .error("slug", "Slug is required")
                    .toResponseMap();

            @SuppressWarnings("unchecked")
            List<Map<String, String>> errors = (List<Map<String, String>>) map.get("errors");
            assertNotNull(errors);
            assertEquals(1, errors.size());
            assertEquals("slug", errors.get(0).get("field"));
            assertEquals("Slug is required", errors.get(0).get("message"));
        }

        @Test
        @DisplayName("null field in error should become empty string in response map")
        void nullFieldBecomesEmptyString() {
            Map<String, Object> map = BeforeSaveResult
                    .error(null, "General error")
                    .toResponseMap();

            @SuppressWarnings("unchecked")
            List<Map<String, String>> errors = (List<Map<String, String>>) map.get("errors");
            assertEquals("", errors.get(0).get("field"));
        }
    }

    @Nested
    @DisplayName("ValidationError Record")
    class ValidationErrorTests {

        @Test
        @DisplayName("ValidationError should store field and message")
        void validationErrorShouldStoreValues() {
            BeforeSaveResult.ValidationError error =
                    new BeforeSaveResult.ValidationError("name", "Name is required");

            assertEquals("name", error.field());
            assertEquals("Name is required", error.message());
        }

        @Test
        @DisplayName("ValidationError equality should work")
        void validationErrorEquality() {
            BeforeSaveResult.ValidationError a =
                    new BeforeSaveResult.ValidationError("name", "Required");
            BeforeSaveResult.ValidationError b =
                    new BeforeSaveResult.ValidationError("name", "Required");

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }
}
