package com.emf.runtime.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationRuleRegistry.
 */
class ValidationRuleRegistryTest {

    private ValidationRuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ValidationRuleRegistry();
    }

    @Test
    @DisplayName("Should return empty list for unregistered collection")
    void shouldReturnEmptyForUnregisteredCollection() {
        List<ValidationRuleDefinition> rules = registry.getActiveRules("unknown");
        assertNotNull(rules);
        assertTrue(rules.isEmpty());
    }

    @Test
    @DisplayName("Should register and retrieve active rules")
    void shouldRegisterAndRetrieveActiveRules() {
        var activeRule = new ValidationRuleDefinition(
                "rule1", "x > 0", "Error", null, "CREATE", true);
        var inactiveRule = new ValidationRuleDefinition(
                "rule2", "y < 0", "Error2", null, "UPDATE", false);

        registry.register("products", List.of(activeRule, inactiveRule));

        List<ValidationRuleDefinition> activeRules = registry.getActiveRules("products");
        assertEquals(1, activeRules.size());
        assertEquals("rule1", activeRules.get(0).name());
    }

    @Test
    @DisplayName("Should replace rules on re-registration")
    void shouldReplaceRulesOnReRegistration() {
        var rule1 = new ValidationRuleDefinition(
                "rule1", "x > 0", "Error", null, "CREATE", true);
        var rule2 = new ValidationRuleDefinition(
                "rule2", "y < 0", "Error2", null, "UPDATE", true);

        registry.register("products", List.of(rule1));
        assertEquals(1, registry.getActiveRules("products").size());

        registry.register("products", List.of(rule1, rule2));
        assertEquals(2, registry.getActiveRules("products").size());
    }

    @Test
    @DisplayName("Should unregister collection rules")
    void shouldUnregisterCollectionRules() {
        var rule = new ValidationRuleDefinition(
                "rule1", "x > 0", "Error", null, "CREATE", true);
        registry.register("products", List.of(rule));
        assertEquals(1, registry.size());

        registry.unregister("products");
        assertEquals(0, registry.size());
        assertTrue(registry.getActiveRules("products").isEmpty());
    }

    @Test
    @DisplayName("Should track size correctly")
    void shouldTrackSizeCorrectly() {
        assertEquals(0, registry.size());

        var rule = new ValidationRuleDefinition(
                "rule1", "x > 0", "Error", null, "CREATE", true);
        registry.register("products", List.of(rule));
        registry.register("orders", List.of(rule));

        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("Should return only active rules when mixed active/inactive")
    void shouldReturnOnlyActiveRules() {
        List<ValidationRuleDefinition> rules = List.of(
                new ValidationRuleDefinition("r1", "a > 0", "E1", null, "CREATE", true),
                new ValidationRuleDefinition("r2", "b > 0", "E2", null, "CREATE", false),
                new ValidationRuleDefinition("r3", "c > 0", "E3", null, "CREATE", true),
                new ValidationRuleDefinition("r4", "d > 0", "E4", null, "CREATE", false));

        registry.register("test", rules);
        List<ValidationRuleDefinition> active = registry.getActiveRules("test");
        assertEquals(2, active.size());
        assertTrue(active.stream().allMatch(ValidationRuleDefinition::active));
    }
}
