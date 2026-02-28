package com.emf.runtime.flow;

import java.util.List;

/**
 * Sealed interface representing a comparison rule used in Choice states.
 * <p>
 * Each rule evaluates a variable (JSONPath into state data) against an expected
 * value using a typed comparison operator. Compound rules (And, Or, Not) combine
 * multiple rules with boolean logic.
 *
 * @since 1.0.0
 */
public sealed interface ChoiceRule {

    /**
     * The state to transition to if this rule matches.
     */
    String next();

    // -------------------------------------------------------------------------
    // String Comparisons
    // -------------------------------------------------------------------------

    record StringEquals(String variable, String value, String next) implements ChoiceRule {}
    record StringNotEquals(String variable, String value, String next) implements ChoiceRule {}
    record StringGreaterThan(String variable, String value, String next) implements ChoiceRule {}
    record StringLessThan(String variable, String value, String next) implements ChoiceRule {}
    record StringGreaterThanEquals(String variable, String value, String next) implements ChoiceRule {}
    record StringLessThanEquals(String variable, String value, String next) implements ChoiceRule {}
    record StringMatches(String variable, String pattern, String next) implements ChoiceRule {}

    // -------------------------------------------------------------------------
    // Numeric Comparisons
    // -------------------------------------------------------------------------

    record NumericEquals(String variable, double value, String next) implements ChoiceRule {}
    record NumericNotEquals(String variable, double value, String next) implements ChoiceRule {}
    record NumericGreaterThan(String variable, double value, String next) implements ChoiceRule {}
    record NumericLessThan(String variable, double value, String next) implements ChoiceRule {}
    record NumericGreaterThanEquals(String variable, double value, String next) implements ChoiceRule {}
    record NumericLessThanEquals(String variable, double value, String next) implements ChoiceRule {}

    // -------------------------------------------------------------------------
    // Boolean Comparisons
    // -------------------------------------------------------------------------

    record BooleanEquals(String variable, boolean value, String next) implements ChoiceRule {}

    // -------------------------------------------------------------------------
    // Existence Check
    // -------------------------------------------------------------------------

    record IsPresent(String variable, boolean isPresent, String next) implements ChoiceRule {}
    record IsNull(String variable, boolean isNull, String next) implements ChoiceRule {}

    // -------------------------------------------------------------------------
    // Compound Rules (boolean logic)
    // -------------------------------------------------------------------------

    record And(List<ChoiceRule> rules, String next) implements ChoiceRule {}
    record Or(List<ChoiceRule> rules, String next) implements ChoiceRule {}
    record Not(ChoiceRule rule, String next) implements ChoiceRule {}
}
