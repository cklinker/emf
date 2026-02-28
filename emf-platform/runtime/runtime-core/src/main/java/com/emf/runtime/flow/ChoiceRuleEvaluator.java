package com.emf.runtime.flow;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Evaluates {@link ChoiceRule} instances against state data to determine
 * which branch a Choice state should take.
 *
 * @since 1.0.0
 */
public class ChoiceRuleEvaluator {

    private final StateDataResolver dataResolver;

    public ChoiceRuleEvaluator(StateDataResolver dataResolver) {
        this.dataResolver = dataResolver;
    }

    /**
     * Evaluates a choice rule against the given state data.
     *
     * @param rule      the choice rule to evaluate
     * @param stateData the current state data
     * @return true if the rule matches
     */
    public boolean evaluate(ChoiceRule rule, Map<String, Object> stateData) {
        return switch (rule) {
            case ChoiceRule.StringEquals r -> stringEquals(r.variable(), r.value(), stateData);
            case ChoiceRule.StringNotEquals r -> !stringEquals(r.variable(), r.value(), stateData);
            case ChoiceRule.StringGreaterThan r -> stringCompare(r.variable(), r.value(), stateData) > 0;
            case ChoiceRule.StringLessThan r -> stringCompare(r.variable(), r.value(), stateData) < 0;
            case ChoiceRule.StringGreaterThanEquals r -> stringCompare(r.variable(), r.value(), stateData) >= 0;
            case ChoiceRule.StringLessThanEquals r -> stringCompare(r.variable(), r.value(), stateData) <= 0;
            case ChoiceRule.StringMatches r -> stringMatches(r.variable(), r.pattern(), stateData);
            case ChoiceRule.NumericEquals r -> numericCompare(r.variable(), r.value(), stateData) == 0;
            case ChoiceRule.NumericNotEquals r -> numericCompare(r.variable(), r.value(), stateData) != 0;
            case ChoiceRule.NumericGreaterThan r -> numericCompare(r.variable(), r.value(), stateData) > 0;
            case ChoiceRule.NumericLessThan r -> numericCompare(r.variable(), r.value(), stateData) < 0;
            case ChoiceRule.NumericGreaterThanEquals r -> numericCompare(r.variable(), r.value(), stateData) >= 0;
            case ChoiceRule.NumericLessThanEquals r -> numericCompare(r.variable(), r.value(), stateData) <= 0;
            case ChoiceRule.BooleanEquals r -> booleanEquals(r.variable(), r.value(), stateData);
            case ChoiceRule.IsPresent r -> isPresent(r.variable(), stateData) == r.isPresent();
            case ChoiceRule.IsNull r -> isNull(r.variable(), stateData) == r.isNull();
            case ChoiceRule.And r -> r.rules().stream().allMatch(sub -> evaluate(sub, stateData));
            case ChoiceRule.Or r -> r.rules().stream().anyMatch(sub -> evaluate(sub, stateData));
            case ChoiceRule.Not r -> !evaluate(r.rule(), stateData);
        };
    }

    private boolean stringEquals(String variable, String expected, Map<String, Object> stateData) {
        Object value = dataResolver.readPath(stateData, variable);
        if (value == null) return expected == null;
        return String.valueOf(value).equals(expected);
    }

    private int stringCompare(String variable, String expected, Map<String, Object> stateData) {
        Object value = dataResolver.readPath(stateData, variable);
        if (value == null) return expected == null ? 0 : -1;
        return String.valueOf(value).compareTo(expected);
    }

    private boolean stringMatches(String variable, String pattern, Map<String, Object> stateData) {
        Object value = dataResolver.readPath(stateData, variable);
        if (value == null || pattern == null) return false;
        // Convert glob-style pattern to regex: * → .*, ? → .
        String regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        return Pattern.matches(regex, String.valueOf(value));
    }

    private int numericCompare(String variable, double expected, Map<String, Object> stateData) {
        Object value = dataResolver.readPath(stateData, variable);
        if (value == null) return -1;
        double actual = toDouble(value);
        return Double.compare(actual, expected);
    }

    private boolean booleanEquals(String variable, boolean expected, Map<String, Object> stateData) {
        Object value = dataResolver.readPath(stateData, variable);
        if (value == null) return false;
        if (value instanceof Boolean b) return b == expected;
        return Boolean.parseBoolean(String.valueOf(value)) == expected;
    }

    private boolean isPresent(String variable, Map<String, Object> stateData) {
        Object value = dataResolver.readPath(stateData, variable);
        return value != null;
    }

    private boolean isNull(String variable, Map<String, Object> stateData) {
        Object value = dataResolver.readPath(stateData, variable);
        return value == null;
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
