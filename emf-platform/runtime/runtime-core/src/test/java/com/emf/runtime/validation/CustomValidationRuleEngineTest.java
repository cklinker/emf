package com.emf.runtime.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomValidationRuleEngine.
 */
class CustomValidationRuleEngineTest {

    private ValidationRuleRegistry ruleRegistry;
    private ValidationRuleEvaluator ruleEvaluator;
    private CustomValidationRuleEngine engine;

    @BeforeEach
    void setUp() {
        ruleRegistry = new ValidationRuleRegistry();
        ruleEvaluator = mock(ValidationRuleEvaluator.class);
        engine = new CustomValidationRuleEngine(ruleRegistry, ruleEvaluator);
    }

    @Nested
    @DisplayName("Evaluate with no rules")
    class NoRulesTests {

        @Test
        @DisplayName("Should not throw when no rules registered")
        void shouldNotThrowWhenNoRules() {
            Map<String, Object> data = Map.of("name", "test");
            assertDoesNotThrow(() ->
                    engine.evaluate("products", data, OperationType.CREATE));
        }

        @Test
        @DisplayName("Should not call evaluator when no rules registered")
        void shouldNotCallEvaluatorWhenNoRules() {
            Map<String, Object> data = Map.of("name", "test");
            engine.evaluate("products", data, OperationType.CREATE);
            verifyNoInteractions(ruleEvaluator);
        }
    }

    @Nested
    @DisplayName("Evaluate with passing rules")
    class PassingRulesTests {

        @Test
        @DisplayName("Should not throw when all rules pass")
        void shouldNotThrowWhenAllRulesPass() {
            var rule = new ValidationRuleDefinition(
                    "positive_price", "price < 0", "Price must be positive",
                    "price", "CREATE_AND_UPDATE", true);
            ruleRegistry.register("products", List.of(rule));

            Map<String, Object> data = Map.of("price", 10.0);
            when(ruleEvaluator.isInvalid("price < 0", data)).thenReturn(false);

            assertDoesNotThrow(() ->
                    engine.evaluate("products", data, OperationType.CREATE));
        }
    }

    @Nested
    @DisplayName("Evaluate with failing rules")
    class FailingRulesTests {

        @Test
        @DisplayName("Should throw RecordValidationException when rule fails")
        void shouldThrowWhenRuleFails() {
            var rule = new ValidationRuleDefinition(
                    "positive_price", "price < 0", "Price must be positive",
                    "price", "CREATE_AND_UPDATE", true);
            ruleRegistry.register("products", List.of(rule));

            Map<String, Object> data = Map.of("price", -5.0);
            when(ruleEvaluator.isInvalid("price < 0", data)).thenReturn(true);

            RecordValidationException ex = assertThrows(RecordValidationException.class, () ->
                    engine.evaluate("products", data, OperationType.CREATE));

            assertEquals(1, ex.getErrors().size());
            assertEquals("positive_price", ex.getErrors().get(0).ruleName());
            assertEquals("Price must be positive", ex.getErrors().get(0).errorMessage());
            assertEquals("price", ex.getErrors().get(0).errorField());
        }

        @Test
        @DisplayName("Should collect multiple rule violations")
        void shouldCollectMultipleViolations() {
            var rule1 = new ValidationRuleDefinition(
                    "positive_price", "price < 0", "Price must be positive",
                    "price", "CREATE_AND_UPDATE", true);
            var rule2 = new ValidationRuleDefinition(
                    "name_required", "name == null", "Name is required",
                    "name", "CREATE_AND_UPDATE", true);
            ruleRegistry.register("products", List.of(rule1, rule2));

            Map<String, Object> data = Map.of("price", -5.0);
            when(ruleEvaluator.isInvalid("price < 0", data)).thenReturn(true);
            when(ruleEvaluator.isInvalid("name == null", data)).thenReturn(true);

            RecordValidationException ex = assertThrows(RecordValidationException.class, () ->
                    engine.evaluate("products", data, OperationType.CREATE));

            assertEquals(2, ex.getErrors().size());
        }
    }

    @Nested
    @DisplayName("Operation type filtering")
    class OperationTypeFilteringTests {

        @Test
        @DisplayName("Should skip rules not applicable to CREATE")
        void shouldSkipRulesNotApplicableToCreate() {
            var updateOnlyRule = new ValidationRuleDefinition(
                    "update_check", "x > 0", "Error", null, "UPDATE", true);
            ruleRegistry.register("products", List.of(updateOnlyRule));

            Map<String, Object> data = Map.of("x", 100);
            engine.evaluate("products", data, OperationType.CREATE);

            verifyNoInteractions(ruleEvaluator);
        }

        @Test
        @DisplayName("Should skip rules not applicable to UPDATE")
        void shouldSkipRulesNotApplicableToUpdate() {
            var createOnlyRule = new ValidationRuleDefinition(
                    "create_check", "x > 0", "Error", null, "CREATE", true);
            ruleRegistry.register("products", List.of(createOnlyRule));

            Map<String, Object> data = Map.of("x", 100);
            engine.evaluate("products", data, OperationType.UPDATE);

            verifyNoInteractions(ruleEvaluator);
        }

        @Test
        @DisplayName("Should evaluate CREATE_AND_UPDATE rules for both operations")
        void shouldEvaluateCreateAndUpdateRulesForBoth() {
            var rule = new ValidationRuleDefinition(
                    "always_check", "x > 100", "Too high", "x",
                    "CREATE_AND_UPDATE", true);
            ruleRegistry.register("products", List.of(rule));

            Map<String, Object> data = Map.of("x", 50);
            when(ruleEvaluator.isInvalid("x > 100", data)).thenReturn(false);

            engine.evaluate("products", data, OperationType.CREATE);
            engine.evaluate("products", data, OperationType.UPDATE);

            verify(ruleEvaluator, times(2)).isInvalid("x > 100", data);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should not throw when evaluator throws exception")
        void shouldNotThrowWhenEvaluatorThrows() {
            var rule = new ValidationRuleDefinition(
                    "broken_rule", "invalid formula", "Error", null,
                    "CREATE_AND_UPDATE", true);
            ruleRegistry.register("products", List.of(rule));

            Map<String, Object> data = Map.of("x", 10);
            when(ruleEvaluator.isInvalid("invalid formula", data))
                    .thenThrow(new RuntimeException("Parse error"));

            assertDoesNotThrow(() ->
                    engine.evaluate("products", data, OperationType.CREATE));
        }

        @Test
        @DisplayName("Should skip inactive rules")
        void shouldSkipInactiveRules() {
            var inactiveRule = new ValidationRuleDefinition(
                    "disabled_rule", "x > 0", "Error", null,
                    "CREATE_AND_UPDATE", false);
            ruleRegistry.register("products", List.of(inactiveRule));

            Map<String, Object> data = Map.of("x", 100);
            engine.evaluate("products", data, OperationType.CREATE);

            verifyNoInteractions(ruleEvaluator);
        }
    }
}
