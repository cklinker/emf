package com.emf.runtime.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChoiceRuleEvaluator")
class ChoiceRuleEvaluatorTest {

    private ChoiceRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        StateDataResolver resolver = new StateDataResolver(new ObjectMapper());
        evaluator = new ChoiceRuleEvaluator(resolver);
    }

    private Map<String, Object> data() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ACTIVE");
        data.put("amount", 250.0);
        data.put("count", 5);
        data.put("isVerified", true);
        data.put("name", "hello");
        data.put("nullField", null);
        return data;
    }

    @Nested
    @DisplayName("String Comparisons")
    class StringComparisons {

        @Test
        @DisplayName("StringEquals matches equal string")
        void stringEqualsMatches() {
            var rule = new ChoiceRule.StringEquals("$.status", "ACTIVE", "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("StringEquals does not match different string")
        void stringEqualsNoMatch() {
            var rule = new ChoiceRule.StringEquals("$.status", "INACTIVE", "Next");
            assertFalse(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("StringNotEquals matches different string")
        void stringNotEqualsMatches() {
            var rule = new ChoiceRule.StringNotEquals("$.status", "INACTIVE", "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("StringGreaterThan compares lexicographically")
        void stringGreaterThan() {
            var rule = new ChoiceRule.StringGreaterThan("$.name", "abc", "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("StringLessThan compares lexicographically")
        void stringLessThan() {
            var rule = new ChoiceRule.StringLessThan("$.name", "xyz", "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("StringMatches with wildcard")
        void stringMatchesWildcard() {
            var rule = new ChoiceRule.StringMatches("$.name", "hel*", "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("StringMatches fails on non-match")
        void stringMatchesNoMatch() {
            var rule = new ChoiceRule.StringMatches("$.name", "xyz*", "Next");
            assertFalse(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("StringEquals with null value returns false")
        void stringEqualsNullValue() {
            var rule = new ChoiceRule.StringEquals("$.missing", "test", "Next");
            assertFalse(evaluator.evaluate(rule, data()));
        }
    }

    @Nested
    @DisplayName("Numeric Comparisons")
    class NumericComparisons {

        @Test
        @DisplayName("NumericEquals matches equal value")
        void numericEquals() {
            var rule = new ChoiceRule.NumericEquals("$.amount", 250.0, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("NumericNotEquals matches different value")
        void numericNotEquals() {
            var rule = new ChoiceRule.NumericNotEquals("$.amount", 100.0, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("NumericGreaterThan matches")
        void numericGreaterThan() {
            var rule = new ChoiceRule.NumericGreaterThan("$.amount", 100.0, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("NumericLessThan matches")
        void numericLessThan() {
            var rule = new ChoiceRule.NumericLessThan("$.amount", 500.0, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("NumericGreaterThanEquals matches equal")
        void numericGreaterThanEquals() {
            var rule = new ChoiceRule.NumericGreaterThanEquals("$.amount", 250.0, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("NumericLessThanEquals matches equal")
        void numericLessThanEquals() {
            var rule = new ChoiceRule.NumericLessThanEquals("$.amount", 250.0, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("works with integer values")
        void numericWithInteger() {
            var rule = new ChoiceRule.NumericEquals("$.count", 5.0, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }
    }

    @Nested
    @DisplayName("Boolean Comparisons")
    class BooleanComparisons {

        @Test
        @DisplayName("BooleanEquals matches true")
        void booleanEqualsTrue() {
            var rule = new ChoiceRule.BooleanEquals("$.isVerified", true, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("BooleanEquals does not match false")
        void booleanEqualsFalse() {
            var rule = new ChoiceRule.BooleanEquals("$.isVerified", false, "Next");
            assertFalse(evaluator.evaluate(rule, data()));
        }
    }

    @Nested
    @DisplayName("Existence Checks")
    class ExistenceChecks {

        @Test
        @DisplayName("IsPresent true matches existing field")
        void isPresentTrue() {
            var rule = new ChoiceRule.IsPresent("$.status", true, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("IsPresent true does not match missing field")
        void isPresentTrueMissing() {
            var rule = new ChoiceRule.IsPresent("$.missing", true, "Next");
            assertFalse(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("IsPresent false matches missing field")
        void isPresentFalse() {
            var rule = new ChoiceRule.IsPresent("$.missing", false, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("IsNull true matches null field")
        void isNullTrue() {
            var rule = new ChoiceRule.IsNull("$.missing", true, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("IsNull false matches non-null field")
        void isNullFalse() {
            var rule = new ChoiceRule.IsNull("$.status", false, "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }
    }

    @Nested
    @DisplayName("Compound Rules")
    class CompoundRules {

        @Test
        @DisplayName("And rule matches when all sub-rules match")
        void andAllMatch() {
            var rule = new ChoiceRule.And(List.of(
                new ChoiceRule.StringEquals("$.status", "ACTIVE", null),
                new ChoiceRule.NumericGreaterThan("$.amount", 100.0, null)
            ), "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("And rule fails when one sub-rule fails")
        void andOneFails() {
            var rule = new ChoiceRule.And(List.of(
                new ChoiceRule.StringEquals("$.status", "ACTIVE", null),
                new ChoiceRule.NumericGreaterThan("$.amount", 500.0, null)
            ), "Next");
            assertFalse(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("Or rule matches when any sub-rule matches")
        void orAnyMatch() {
            var rule = new ChoiceRule.Or(List.of(
                new ChoiceRule.StringEquals("$.status", "INACTIVE", null),
                new ChoiceRule.NumericGreaterThan("$.amount", 100.0, null)
            ), "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("Or rule fails when no sub-rules match")
        void orNoneMatch() {
            var rule = new ChoiceRule.Or(List.of(
                new ChoiceRule.StringEquals("$.status", "INACTIVE", null),
                new ChoiceRule.NumericGreaterThan("$.amount", 500.0, null)
            ), "Next");
            assertFalse(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("Not rule negates sub-rule")
        void notNegates() {
            var rule = new ChoiceRule.Not(
                new ChoiceRule.StringEquals("$.status", "INACTIVE", null),
                "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }

        @Test
        @DisplayName("nested compound rules work")
        void nestedCompound() {
            // (status == ACTIVE AND amount > 100) OR isVerified == true
            var rule = new ChoiceRule.Or(List.of(
                new ChoiceRule.And(List.of(
                    new ChoiceRule.StringEquals("$.status", "ACTIVE", null),
                    new ChoiceRule.NumericGreaterThan("$.amount", 100.0, null)
                ), null),
                new ChoiceRule.BooleanEquals("$.isVerified", true, null)
            ), "Next");
            assertTrue(evaluator.evaluate(rule, data()));
        }
    }
}
