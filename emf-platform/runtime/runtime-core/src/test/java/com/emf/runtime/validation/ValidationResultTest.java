package com.emf.runtime.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ValidationResult} record.
 */
@DisplayName("ValidationResult Tests")
class ValidationResultTest {

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("success() should create valid result with no errors")
        void successShouldCreateValidResultWithNoErrors() {
            ValidationResult result = ValidationResult.success();
            
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
            assertEquals(0, result.errorCount());
        }

        @Test
        @DisplayName("failure(List) should create invalid result with errors")
        void failureListShouldCreateInvalidResultWithErrors() {
            List<FieldError> errors = List.of(
                FieldError.nullable("field1"),
                FieldError.minValue("field2", 10)
            );
            
            ValidationResult result = ValidationResult.failure(errors);
            
            assertFalse(result.valid());
            assertEquals(2, result.errorCount());
        }

        @Test
        @DisplayName("failure(FieldError) should create invalid result with single error")
        void failureSingleShouldCreateInvalidResultWithSingleError() {
            ValidationResult result = ValidationResult.failure(FieldError.nullable("name"));
            
            assertFalse(result.valid());
            assertEquals(1, result.errorCount());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("failure(List) should throw on null errors")
        void failureListShouldThrowOnNullErrors() {
            assertThrows(NullPointerException.class, () -> {
                ValidationResult.failure((List<FieldError>) null);
            });
        }

        @Test
        @DisplayName("failure(List) should throw on empty errors")
        void failureListShouldThrowOnEmptyErrors() {
            assertThrows(IllegalArgumentException.class, () -> {
                ValidationResult.failure(List.of());
            });
        }

        @Test
        @DisplayName("failure(FieldError) should throw on null error")
        void failureSingleShouldThrowOnNullError() {
            assertThrows(NullPointerException.class, () -> {
                ValidationResult.failure((FieldError) null);
            });
        }

        @Test
        @DisplayName("Constructor should throw on null errors list")
        void constructorShouldThrowOnNullErrorsList() {
            assertThrows(NullPointerException.class, () -> {
                new ValidationResult(true, null);
            });
        }
    }

    @Nested
    @DisplayName("Defensive Copying Tests")
    class DefensiveCopyingTests {

        @Test
        @DisplayName("Should perform defensive copy of errors list")
        void shouldPerformDefensiveCopyOfErrorsList() {
            List<FieldError> mutableList = new ArrayList<>();
            mutableList.add(FieldError.nullable("field1"));
            
            ValidationResult result = new ValidationResult(false, mutableList);
            
            // Modify original list
            mutableList.add(FieldError.nullable("field2"));
            
            // Result should not be affected
            assertEquals(1, result.errorCount());
        }

        @Test
        @DisplayName("Should return immutable errors list")
        void shouldReturnImmutableErrorsList() {
            ValidationResult result = ValidationResult.failure(FieldError.nullable("field"));
            
            assertThrows(UnsupportedOperationException.class, () -> {
                result.errors().add(FieldError.nullable("another"));
            });
        }
    }

    @Nested
    @DisplayName("Query Method Tests")
    class QueryMethodTests {

        @Test
        @DisplayName("hasErrorsForField should return true when field has errors")
        void hasErrorsForFieldShouldReturnTrueWhenFieldHasErrors() {
            ValidationResult result = ValidationResult.failure(List.of(
                FieldError.nullable("name"),
                FieldError.minLength("name", 3)
            ));
            
            assertTrue(result.hasErrorsForField("name"));
        }

        @Test
        @DisplayName("hasErrorsForField should return false when field has no errors")
        void hasErrorsForFieldShouldReturnFalseWhenFieldHasNoErrors() {
            ValidationResult result = ValidationResult.failure(FieldError.nullable("name"));
            
            assertFalse(result.hasErrorsForField("other"));
        }

        @Test
        @DisplayName("getErrorsForField should return all errors for field")
        void getErrorsForFieldShouldReturnAllErrorsForField() {
            ValidationResult result = ValidationResult.failure(List.of(
                FieldError.nullable("name"),
                FieldError.minLength("name", 3),
                FieldError.nullable("age")
            ));
            
            List<FieldError> nameErrors = result.getErrorsForField("name");
            
            assertEquals(2, nameErrors.size());
        }

        @Test
        @DisplayName("getErrorsForField should return empty list when no errors")
        void getErrorsForFieldShouldReturnEmptyListWhenNoErrors() {
            ValidationResult result = ValidationResult.failure(FieldError.nullable("name"));
            
            List<FieldError> otherErrors = result.getErrorsForField("other");
            
            assertTrue(otherErrors.isEmpty());
        }
    }

    @Nested
    @DisplayName("toErrorResponse Tests")
    class ToErrorResponseTests {

        @Test
        @DisplayName("Should include valid flag in response")
        void shouldIncludeValidFlagInResponse() {
            ValidationResult result = ValidationResult.success();
            Map<String, Object> response = result.toErrorResponse();
            
            assertTrue((Boolean) response.get("valid"));
        }

        @Test
        @DisplayName("Should group errors by field name")
        void shouldGroupErrorsByFieldName() {
            ValidationResult result = ValidationResult.failure(List.of(
                FieldError.nullable("name"),
                FieldError.minLength("name", 3),
                FieldError.nullable("age")
            ));
            
            Map<String, Object> response = result.toErrorResponse();
            
            assertFalse((Boolean) response.get("valid"));
            
            @SuppressWarnings("unchecked")
            Map<String, List<String>> errors = (Map<String, List<String>>) response.get("errors");
            
            assertEquals(2, errors.size());
            assertEquals(2, errors.get("name").size());
            assertEquals(1, errors.get("age").size());
        }

        @Test
        @DisplayName("Should not include errors key for success result")
        void shouldNotIncludeErrorsKeyForSuccessResult() {
            ValidationResult result = ValidationResult.success();
            Map<String, Object> response = result.toErrorResponse();
            
            assertFalse(response.containsKey("errors"));
        }
    }
}
