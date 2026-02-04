package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ReferenceConfig} record.
 * 
 * Validates: Requirements 1.2 - Reference relationships
 */
@DisplayName("ReferenceConfig Record Tests")
class ReferenceConfigTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create reference config with all parameters")
        void shouldCreateWithAllParameters() {
            ReferenceConfig config = new ReferenceConfig("users", "id", true);
            
            assertEquals("users", config.targetCollection());
            assertEquals("id", config.targetField());
            assertTrue(config.cascadeDelete());
        }

        @Test
        @DisplayName("Should throw on null targetCollection")
        void shouldThrowOnNullTargetCollection() {
            assertThrows(NullPointerException.class, () -> {
                new ReferenceConfig(null, "id", false);
            });
        }

        @Test
        @DisplayName("Should default to 'id' when targetField is null")
        void shouldDefaultToIdWhenTargetFieldIsNull() {
            ReferenceConfig config = new ReferenceConfig("users", null, false);
            
            assertEquals("id", config.targetField());
        }

        @Test
        @DisplayName("Should default to 'id' when targetField is blank")
        void shouldDefaultToIdWhenTargetFieldIsBlank() {
            ReferenceConfig config = new ReferenceConfig("users", "  ", false);
            
            assertEquals("id", config.targetField());
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("toCollection() should create reference to ID field without cascade")
        void toCollectionShouldCreateReferenceToIdWithoutCascade() {
            ReferenceConfig config = ReferenceConfig.toCollection("users");
            
            assertEquals("users", config.targetCollection());
            assertEquals("id", config.targetField());
            assertFalse(config.cascadeDelete());
        }

        @Test
        @DisplayName("toCollectionWithCascade() should create reference with cascade delete")
        void toCollectionWithCascadeShouldCreateReferenceWithCascade() {
            ReferenceConfig config = ReferenceConfig.toCollectionWithCascade("users");
            
            assertEquals("users", config.targetCollection());
            assertEquals("id", config.targetField());
            assertTrue(config.cascadeDelete());
        }

        @Test
        @DisplayName("toField() should create reference to specific field")
        void toFieldShouldCreateReferenceToSpecificField() {
            ReferenceConfig config = ReferenceConfig.toField("users", "email", true);
            
            assertEquals("users", config.targetCollection());
            assertEquals("email", config.targetField());
            assertTrue(config.cascadeDelete());
        }
    }

    @Nested
    @DisplayName("Record Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            ReferenceConfig config1 = new ReferenceConfig("users", "id", true);
            ReferenceConfig config2 = new ReferenceConfig("users", "id", true);
            
            assertEquals(config1, config2);
            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            ReferenceConfig config1 = new ReferenceConfig("users", "id", true);
            ReferenceConfig config2 = new ReferenceConfig("users", "id", false);
            
            assertNotEquals(config1, config2);
        }
    }
}
