package com.emf.runtime.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationRuleDefinition.
 */
class ValidationRuleDefinitionTest {

    @Test
    @DisplayName("Should apply to CREATE when evaluateOn is CREATE")
    void shouldApplyToCreateWhenEvaluateOnIsCreate() {
        var rule = new ValidationRuleDefinition(
                "rule1", "x > 10", "Error", null, "CREATE", true);
        assertTrue(rule.appliesTo("CREATE"));
        assertFalse(rule.appliesTo("UPDATE"));
    }

    @Test
    @DisplayName("Should apply to UPDATE when evaluateOn is UPDATE")
    void shouldApplyToUpdateWhenEvaluateOnIsUpdate() {
        var rule = new ValidationRuleDefinition(
                "rule1", "x > 10", "Error", null, "UPDATE", true);
        assertFalse(rule.appliesTo("CREATE"));
        assertTrue(rule.appliesTo("UPDATE"));
    }

    @Test
    @DisplayName("Should apply to both when evaluateOn is CREATE_AND_UPDATE")
    void shouldApplyToBothWhenCreateAndUpdate() {
        var rule = new ValidationRuleDefinition(
                "rule1", "x > 10", "Error", null, "CREATE_AND_UPDATE", true);
        assertTrue(rule.appliesTo("CREATE"));
        assertTrue(rule.appliesTo("UPDATE"));
    }

    @Test
    @DisplayName("Should expose all record fields")
    void shouldExposeAllFields() {
        var rule = new ValidationRuleDefinition(
                "myRule", "amount < 0", "Amount must be positive", "amount",
                "CREATE_AND_UPDATE", true);
        assertEquals("myRule", rule.name());
        assertEquals("amount < 0", rule.errorConditionFormula());
        assertEquals("Amount must be positive", rule.errorMessage());
        assertEquals("amount", rule.errorField());
        assertEquals("CREATE_AND_UPDATE", rule.evaluateOn());
        assertTrue(rule.active());
    }
}
