package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ValidationRules} record.
 * 
 * Validates: Requirements 1.2 - Field validation constraints
 */
@DisplayName("ValidationRules Record Tests")
class ValidationRulesTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create validation rules with all parameters")
        void shouldCreateWithAllParameters() {
            ValidationRules rules = new ValidationRules(0, 100, 1, 255, "^[a-z]+$");
            
            assertEquals(0, rules.minValue());
            assertEquals(100, rules.maxValue());
            assertEquals(1, rules.minLength());
            assertEquals(255, rules.maxLength());
            assertEquals("^[a-z]+$", rules.pattern());
        }

        @Test
        @DisplayName("Should allow null values for all parameters")
        void shouldAllowNullValues() {
            ValidationRules rules = new ValidationRules(null, null, null, null, null);
            
            assertNull(rules.minValue());
            assertNull(rules.maxValue());
            assertNull(rules.minLength());
            assertNull(rules.maxLength());
            assertNull(rules.pattern());
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("empty() should create rules with all null values")
        void emptyShouldCreateNullRules() {
            ValidationRules rules = ValidationRules.empty();
            
            assertNull(rules.minValue());
            assertNull(rules.maxValue());
            assertNull(rules.minLength());
            assertNull(rules.maxLength());
            assertNull(rules.pattern());
        }

        @Test
        @DisplayName("forNumeric() should create rules with only value constraints")
        void forNumericShouldCreateValueConstraints() {
            ValidationRules rules = ValidationRules.forNumeric(0, 100);
            
            assertEquals(0, rules.minValue());
            assertEquals(100, rules.maxValue());
            assertNull(rules.minLength());
            assertNull(rules.maxLength());
            assertNull(rules.pattern());
        }

        @Test
        @DisplayName("forString() with length should create rules with length constraints")
        void forStringShouldCreateLengthConstraints() {
            ValidationRules rules = ValidationRules.forString(1, 255);
            
            assertNull(rules.minValue());
            assertNull(rules.maxValue());
            assertEquals(1, rules.minLength());
            assertEquals(255, rules.maxLength());
            assertNull(rules.pattern());
        }

        @Test
        @DisplayName("forString() with pattern should create rules with length and pattern constraints")
        void forStringWithPatternShouldCreateAllStringConstraints() {
            ValidationRules rules = ValidationRules.forString(1, 50, "^[A-Z]+$");
            
            assertNull(rules.minValue());
            assertNull(rules.maxValue());
            assertEquals(1, rules.minLength());
            assertEquals(50, rules.maxLength());
            assertEquals("^[A-Z]+$", rules.pattern());
        }
    }

    @Nested
    @DisplayName("Record Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            ValidationRules rules1 = new ValidationRules(0, 100, 1, 255, "^[a-z]+$");
            ValidationRules rules2 = new ValidationRules(0, 100, 1, 255, "^[a-z]+$");
            
            assertEquals(rules1, rules2);
            assertEquals(rules1.hashCode(), rules2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            ValidationRules rules1 = new ValidationRules(0, 100, 1, 255, "^[a-z]+$");
            ValidationRules rules2 = new ValidationRules(1, 100, 1, 255, "^[a-z]+$");
            
            assertNotEquals(rules1, rules2);
        }
    }
}
