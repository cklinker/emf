package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FieldDefinition} record.
 * 
 * Validates: Requirements 1.2 - Field definition with name, type, validation constraints,
 * nullability, immutability, uniqueness, enum values, and reference relationships
 */
@DisplayName("FieldDefinition Record Tests")
class FieldDefinitionTest {

    @Nested
    @DisplayName("Constructor Validation Tests")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Should create field definition with all parameters")
        void shouldCreateWithAllParameters() {
            ValidationRules rules = ValidationRules.forString(1, 100);
            List<String> enumValues = List.of("A", "B", "C");
            ReferenceConfig refConfig = ReferenceConfig.toCollection("users");
            
            FieldDefinition field = new FieldDefinition(
                "status",
                FieldType.STRING,
                false,
                true,
                true,
                "A",
                rules,
                enumValues,
                refConfig
            );
            
            assertEquals("status", field.name());
            assertEquals(FieldType.STRING, field.type());
            assertFalse(field.nullable());
            assertTrue(field.immutable());
            assertTrue(field.unique());
            assertEquals("A", field.defaultValue());
            assertEquals(rules, field.validationRules());
            assertEquals(enumValues, field.enumValues());
            assertEquals(refConfig, field.referenceConfig());
        }

        @Test
        @DisplayName("Should throw NullPointerException when name is null")
        void shouldThrowWhenNameIsNull() {
            NullPointerException exception = assertThrows(NullPointerException.class, () -> {
                new FieldDefinition(null, FieldType.STRING, true, false, false, null, null, null, null);
            });
            assertEquals("name cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when name is blank")
        void shouldThrowWhenNameIsBlank() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new FieldDefinition("  ", FieldType.STRING, true, false, false, null, null, null, null);
            });
            assertEquals("name cannot be blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw NullPointerException when type is null")
        void shouldThrowWhenTypeIsNull() {
            NullPointerException exception = assertThrows(NullPointerException.class, () -> {
                new FieldDefinition("field", null, true, false, false, null, null, null, null);
            });
            assertEquals("type cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should allow null for optional parameters")
        void shouldAllowNullForOptionalParameters() {
            FieldDefinition field = new FieldDefinition(
                "field",
                FieldType.STRING,
                true,
                false,
                false,
                null,
                null,
                null,
                null
            );
            
            assertNull(field.defaultValue());
            assertNull(field.validationRules());
            assertNull(field.enumValues());
            assertNull(field.referenceConfig());
        }
    }

    @Nested
    @DisplayName("Defensive Copying Tests")
    class DefensiveCopyingTests {

        @Test
        @DisplayName("Should perform defensive copy of enumValues")
        void shouldPerformDefensiveCopyOfEnumValues() {
            List<String> mutableList = new ArrayList<>();
            mutableList.add("A");
            mutableList.add("B");
            
            FieldDefinition field = new FieldDefinition(
                "status",
                FieldType.STRING,
                true,
                false,
                false,
                null,
                null,
                mutableList,
                null
            );
            
            // Modify original list
            mutableList.add("C");
            
            // Field should not be affected
            assertEquals(2, field.enumValues().size());
            assertFalse(field.enumValues().contains("C"));
        }

        @Test
        @DisplayName("Should return immutable enumValues")
        void shouldReturnImmutableEnumValues() {
            FieldDefinition field = new FieldDefinition(
                "status",
                FieldType.STRING,
                true,
                false,
                false,
                null,
                null,
                List.of("A", "B"),
                null
            );
            
            assertThrows(UnsupportedOperationException.class, () -> {
                field.enumValues().add("C");
            });
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("string() should create nullable string field")
        void stringShouldCreateNullableStringField() {
            FieldDefinition field = FieldDefinition.string("name");
            
            assertEquals("name", field.name());
            assertEquals(FieldType.STRING, field.type());
            assertTrue(field.nullable());
            assertFalse(field.immutable());
            assertFalse(field.unique());
        }

        @Test
        @DisplayName("requiredString() should create non-nullable string field")
        void requiredStringShouldCreateNonNullableStringField() {
            FieldDefinition field = FieldDefinition.requiredString("name");
            
            assertEquals("name", field.name());
            assertEquals(FieldType.STRING, field.type());
            assertFalse(field.nullable());
        }

        @Test
        @DisplayName("integer() should create nullable integer field")
        void integerShouldCreateNullableIntegerField() {
            FieldDefinition field = FieldDefinition.integer("count");
            
            assertEquals("count", field.name());
            assertEquals(FieldType.INTEGER, field.type());
            assertTrue(field.nullable());
        }

        @Test
        @DisplayName("requiredInteger() should create non-nullable integer field")
        void requiredIntegerShouldCreateNonNullableIntegerField() {
            FieldDefinition field = FieldDefinition.requiredInteger("count");
            
            assertEquals("count", field.name());
            assertEquals(FieldType.INTEGER, field.type());
            assertFalse(field.nullable());
        }

        @Test
        @DisplayName("longField() should create nullable long field")
        void longFieldShouldCreateNullableLongField() {
            FieldDefinition field = FieldDefinition.longField("bigNumber");
            
            assertEquals("bigNumber", field.name());
            assertEquals(FieldType.LONG, field.type());
            assertTrue(field.nullable());
        }

        @Test
        @DisplayName("doubleField() should create nullable double field")
        void doubleFieldShouldCreateNullableDoubleField() {
            FieldDefinition field = FieldDefinition.doubleField("price");
            
            assertEquals("price", field.name());
            assertEquals(FieldType.DOUBLE, field.type());
            assertTrue(field.nullable());
        }

        @Test
        @DisplayName("bool() should create nullable boolean field")
        void boolShouldCreateNullableBooleanField() {
            FieldDefinition field = FieldDefinition.bool("active");
            
            assertEquals("active", field.name());
            assertEquals(FieldType.BOOLEAN, field.type());
            assertTrue(field.nullable());
        }

        @Test
        @DisplayName("bool() with default should create boolean field with default value")
        void boolWithDefaultShouldCreateBooleanFieldWithDefault() {
            FieldDefinition field = FieldDefinition.bool("active", true);
            
            assertEquals("active", field.name());
            assertEquals(FieldType.BOOLEAN, field.type());
            assertFalse(field.nullable());
            assertEquals(true, field.defaultValue());
        }

        @Test
        @DisplayName("date() should create nullable date field")
        void dateShouldCreateNullableDateField() {
            FieldDefinition field = FieldDefinition.date("birthDate");
            
            assertEquals("birthDate", field.name());
            assertEquals(FieldType.DATE, field.type());
            assertTrue(field.nullable());
        }

        @Test
        @DisplayName("datetime() should create nullable datetime field")
        void datetimeShouldCreateNullableDatetimeField() {
            FieldDefinition field = FieldDefinition.datetime("createdAt");
            
            assertEquals("createdAt", field.name());
            assertEquals(FieldType.DATETIME, field.type());
            assertTrue(field.nullable());
        }

        @Test
        @DisplayName("json() should create nullable JSON field")
        void jsonShouldCreateNullableJsonField() {
            FieldDefinition field = FieldDefinition.json("metadata");
            
            assertEquals("metadata", field.name());
            assertEquals(FieldType.JSON, field.type());
            assertTrue(field.nullable());
        }

        @Test
        @DisplayName("enumField() should create enum field with allowed values")
        void enumFieldShouldCreateEnumFieldWithAllowedValues() {
            FieldDefinition field = FieldDefinition.enumField("status", List.of("ACTIVE", "INACTIVE", "PENDING"));
            
            assertEquals("status", field.name());
            assertEquals(FieldType.STRING, field.type());
            assertFalse(field.nullable());
            assertEquals(List.of("ACTIVE", "INACTIVE", "PENDING"), field.enumValues());
        }

        @Test
        @DisplayName("enumField() should throw on null values")
        void enumFieldShouldThrowOnNullValues() {
            assertThrows(NullPointerException.class, () -> {
                FieldDefinition.enumField("status", null);
            });
        }

        @Test
        @DisplayName("reference() should create reference field")
        void referenceShouldCreateReferenceField() {
            FieldDefinition field = FieldDefinition.reference("userId", "users");
            
            assertEquals("userId", field.name());
            assertEquals(FieldType.STRING, field.type());
            assertTrue(field.nullable());
            assertNotNull(field.referenceConfig());
            assertEquals("users", field.referenceConfig().targetCollection());
            assertEquals("id", field.referenceConfig().targetField());
        }
    }

    @Nested
    @DisplayName("Record Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            FieldDefinition field1 = new FieldDefinition(
                "name", FieldType.STRING, true, false, false, null, null, null, null
            );
            FieldDefinition field2 = new FieldDefinition(
                "name", FieldType.STRING, true, false, false, null, null, null, null
            );
            
            assertEquals(field1, field2);
            assertEquals(field1.hashCode(), field2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            FieldDefinition field1 = new FieldDefinition(
                "name", FieldType.STRING, true, false, false, null, null, null, null
            );
            FieldDefinition field2 = new FieldDefinition(
                "name", FieldType.INTEGER, true, false, false, null, null, null, null
            );
            
            assertNotEquals(field1, field2);
        }
    }
}
