package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FieldDefinitionBuilder}.
 * 
 * Validates: Requirements 13.2 - Builder pattern for constructing Field_Definition objects
 * Validates: Requirements 13.3 - Validation of required fields when building field definitions
 * Validates: Requirements 13.4 - Method chaining in builder implementations
 * Validates: Requirements 13.5 - Immutable field definition objects after building
 */
@DisplayName("FieldDefinitionBuilder Tests")
class FieldDefinitionBuilderTest {

    @Nested
    @DisplayName("Method Chaining Tests")
    class MethodChainingTests {

        @Test
        @DisplayName("All builder methods should return the builder for chaining")
        void allMethodsShouldReturnBuilderForChaining() {
            FieldDefinitionBuilder builder = FieldDefinitionBuilder.builder();
            
            // Each method should return the same builder instance
            assertSame(builder, builder.name("field"));
            assertSame(builder, builder.type(FieldType.STRING));
            assertSame(builder, builder.nullable(true));
            assertSame(builder, builder.immutable(false));
            assertSame(builder, builder.unique(false));
            assertSame(builder, builder.defaultValue("default"));
            assertSame(builder, builder.validationRules(ValidationRules.empty()));
            assertSame(builder, builder.enumValues(List.of("A", "B")));
            assertSame(builder, builder.referenceConfig(ReferenceConfig.toCollection("users")));
        }

        @Test
        @DisplayName("Should support fluent method chaining")
        void shouldSupportFluentMethodChaining() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("email")
                .type(FieldType.STRING)
                .nullable(false)
                .immutable(false)
                .unique(true)
                .validationRules(ValidationRules.forString(5, 255, "^[\\w.-]+@[\\w.-]+\\.\\w+$"))
                .build();
            
            assertEquals("email", field.name());
            assertEquals(FieldType.STRING, field.type());
            assertFalse(field.nullable());
            assertFalse(field.immutable());
            assertTrue(field.unique());
            assertNotNull(field.validationRules());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw IllegalStateException when name is null")
        void shouldThrowWhenNameIsNull() {
            FieldDefinitionBuilder builder = FieldDefinitionBuilder.builder()
                .type(FieldType.STRING);
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
            assertEquals("Field name is required", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when name is blank")
        void shouldThrowWhenNameIsBlank() {
            FieldDefinitionBuilder builder = FieldDefinitionBuilder.builder()
                .name("   ")
                .type(FieldType.STRING);
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
            assertEquals("Field name is required", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when name is empty")
        void shouldThrowWhenNameIsEmpty() {
            FieldDefinitionBuilder builder = FieldDefinitionBuilder.builder()
                .name("")
                .type(FieldType.STRING);
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
            assertEquals("Field name is required", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when type is null")
        void shouldThrowWhenTypeIsNull() {
            FieldDefinitionBuilder builder = FieldDefinitionBuilder.builder()
                .name("field");
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
            assertEquals("Field type is required", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Default Values Tests")
    class DefaultValuesTests {

        @Test
        @DisplayName("Should default nullable to true")
        void shouldDefaultNullableToTrue() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("field")
                .type(FieldType.STRING)
                .build();
            
            assertTrue(field.nullable());
        }

        @Test
        @DisplayName("Should default immutable to false")
        void shouldDefaultImmutableToFalse() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("field")
                .type(FieldType.STRING)
                .build();
            
            assertFalse(field.immutable());
        }

        @Test
        @DisplayName("Should default unique to false")
        void shouldDefaultUniqueToFalse() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("field")
                .type(FieldType.STRING)
                .build();
            
            assertFalse(field.unique());
        }

        @Test
        @DisplayName("Should default optional fields to null")
        void shouldDefaultOptionalFieldsToNull() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("field")
                .type(FieldType.STRING)
                .build();
            
            assertNull(field.defaultValue());
            assertNull(field.validationRules());
            assertNull(field.enumValues());
            assertNull(field.referenceConfig());
        }
    }

    @Nested
    @DisplayName("Custom Configuration Tests")
    class CustomConfigurationTests {

        @Test
        @DisplayName("Should set nullable to false when specified")
        void shouldSetNullableToFalse() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("field")
                .type(FieldType.STRING)
                .nullable(false)
                .build();
            
            assertFalse(field.nullable());
        }

        @Test
        @DisplayName("Should set immutable to true when specified")
        void shouldSetImmutableToTrue() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("field")
                .type(FieldType.STRING)
                .immutable(true)
                .build();
            
            assertTrue(field.immutable());
        }

        @Test
        @DisplayName("Should set unique to true when specified")
        void shouldSetUniqueToTrue() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("field")
                .type(FieldType.STRING)
                .unique(true)
                .build();
            
            assertTrue(field.unique());
        }

        @Test
        @DisplayName("Should set default value when specified")
        void shouldSetDefaultValue() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("status")
                .type(FieldType.STRING)
                .defaultValue("ACTIVE")
                .build();
            
            assertEquals("ACTIVE", field.defaultValue());
        }

        @Test
        @DisplayName("Should set validation rules when specified")
        void shouldSetValidationRules() {
            ValidationRules rules = ValidationRules.forString(1, 100, "^[a-z]+$");
            
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("field")
                .type(FieldType.STRING)
                .validationRules(rules)
                .build();
            
            assertEquals(rules, field.validationRules());
        }

        @Test
        @DisplayName("Should set enum values when specified")
        void shouldSetEnumValues() {
            List<String> enumValues = List.of("ACTIVE", "INACTIVE", "PENDING");
            
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("status")
                .type(FieldType.STRING)
                .enumValues(enumValues)
                .build();
            
            assertEquals(enumValues, field.enumValues());
        }

        @Test
        @DisplayName("Should set reference config when specified")
        void shouldSetReferenceConfig() {
            ReferenceConfig refConfig = ReferenceConfig.toCollection("users");
            
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("userId")
                .type(FieldType.STRING)
                .referenceConfig(refConfig)
                .build();
            
            assertEquals(refConfig, field.referenceConfig());
        }
    }

    @Nested
    @DisplayName("Field Type Tests")
    class FieldTypeTests {

        @Test
        @DisplayName("Should create STRING field")
        void shouldCreateStringField() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("name")
                .type(FieldType.STRING)
                .build();
            
            assertEquals(FieldType.STRING, field.type());
        }

        @Test
        @DisplayName("Should create INTEGER field")
        void shouldCreateIntegerField() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("count")
                .type(FieldType.INTEGER)
                .build();
            
            assertEquals(FieldType.INTEGER, field.type());
        }

        @Test
        @DisplayName("Should create LONG field")
        void shouldCreateLongField() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("bigNumber")
                .type(FieldType.LONG)
                .build();
            
            assertEquals(FieldType.LONG, field.type());
        }

        @Test
        @DisplayName("Should create DOUBLE field")
        void shouldCreateDoubleField() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("price")
                .type(FieldType.DOUBLE)
                .build();
            
            assertEquals(FieldType.DOUBLE, field.type());
        }

        @Test
        @DisplayName("Should create BOOLEAN field")
        void shouldCreateBooleanField() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("active")
                .type(FieldType.BOOLEAN)
                .build();
            
            assertEquals(FieldType.BOOLEAN, field.type());
        }

        @Test
        @DisplayName("Should create DATE field")
        void shouldCreateDateField() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("birthDate")
                .type(FieldType.DATE)
                .build();
            
            assertEquals(FieldType.DATE, field.type());
        }

        @Test
        @DisplayName("Should create DATETIME field")
        void shouldCreateDatetimeField() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("createdAt")
                .type(FieldType.DATETIME)
                .build();
            
            assertEquals(FieldType.DATETIME, field.type());
        }

        @Test
        @DisplayName("Should create JSON field")
        void shouldCreateJsonField() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("metadata")
                .type(FieldType.JSON)
                .build();
            
            assertEquals(FieldType.JSON, field.type());
        }
    }

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Built field with enum values should have immutable enum list")
        void builtFieldShouldHaveImmutableEnumList() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("status")
                .type(FieldType.STRING)
                .enumValues(List.of("A", "B"))
                .build();
            
            // Attempting to modify the enum values list should throw
            assertThrows(UnsupportedOperationException.class, () -> {
                field.enumValues().add("C");
            });
        }

        @Test
        @DisplayName("Builder should create independent instances")
        void builderShouldCreateIndependentInstances() {
            FieldDefinitionBuilder builder = FieldDefinitionBuilder.builder()
                .name("field")
                .type(FieldType.STRING);
            
            FieldDefinition field1 = builder.nullable(true).build();
            FieldDefinition field2 = builder.nullable(false).build();
            
            // field1 should not be affected by subsequent builder modifications
            assertTrue(field1.nullable());
            assertFalse(field2.nullable());
        }
    }

    @Nested
    @DisplayName("Builder Factory Method Tests")
    class BuilderFactoryMethodTests {

        @Test
        @DisplayName("builder() static method should return new builder instance")
        void builderStaticMethodShouldReturnNewBuilderInstance() {
            FieldDefinitionBuilder builder1 = FieldDefinitionBuilder.builder();
            FieldDefinitionBuilder builder2 = FieldDefinitionBuilder.builder();
            
            assertNotNull(builder1);
            assertNotNull(builder2);
            assertNotSame(builder1, builder2);
        }
    }

    @Nested
    @DisplayName("Complex Field Configuration Tests")
    class ComplexFieldConfigurationTests {

        @Test
        @DisplayName("Should create fully configured field")
        void shouldCreateFullyConfiguredField() {
            ValidationRules rules = ValidationRules.forString(1, 50, "^[A-Z0-9-]+$");
            ReferenceConfig refConfig = ReferenceConfig.toCollectionWithCascade("categories");
            
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("sku")
                .type(FieldType.STRING)
                .nullable(false)
                .immutable(true)
                .unique(true)
                .defaultValue("SKU-000")
                .validationRules(rules)
                .enumValues(null) // Explicitly null
                .referenceConfig(refConfig)
                .build();
            
            assertEquals("sku", field.name());
            assertEquals(FieldType.STRING, field.type());
            assertFalse(field.nullable());
            assertTrue(field.immutable());
            assertTrue(field.unique());
            assertEquals("SKU-000", field.defaultValue());
            assertEquals(rules, field.validationRules());
            assertNull(field.enumValues());
            assertEquals(refConfig, field.referenceConfig());
        }

        @Test
        @DisplayName("Should create enum field with validation")
        void shouldCreateEnumFieldWithValidation() {
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("priority")
                .type(FieldType.STRING)
                .nullable(false)
                .enumValues(List.of("LOW", "MEDIUM", "HIGH", "CRITICAL"))
                .defaultValue("MEDIUM")
                .build();
            
            assertEquals("priority", field.name());
            assertFalse(field.nullable());
            assertEquals(4, field.enumValues().size());
            assertEquals("MEDIUM", field.defaultValue());
        }

        @Test
        @DisplayName("Should create numeric field with validation rules")
        void shouldCreateNumericFieldWithValidationRules() {
            ValidationRules rules = ValidationRules.forNumeric(0, 100);
            
            FieldDefinition field = FieldDefinitionBuilder.builder()
                .name("percentage")
                .type(FieldType.INTEGER)
                .nullable(false)
                .validationRules(rules)
                .defaultValue(0)
                .build();
            
            assertEquals("percentage", field.name());
            assertEquals(FieldType.INTEGER, field.type());
            assertFalse(field.nullable());
            assertEquals(Integer.valueOf(0), rules.minValue());
            assertEquals(Integer.valueOf(100), rules.maxValue());
        }
    }

    @Nested
    @DisplayName("Builder Reuse Tests")
    class BuilderReuseTests {

        @Test
        @DisplayName("Builder can be reused to create multiple fields")
        void builderCanBeReusedToCreateMultipleFields() {
            FieldDefinitionBuilder builder = FieldDefinitionBuilder.builder()
                .type(FieldType.STRING);
            
            FieldDefinition field1 = builder.name("field1").build();
            FieldDefinition field2 = builder.name("field2").build();
            
            assertEquals("field1", field1.name());
            assertEquals("field2", field2.name());
        }
    }
}
