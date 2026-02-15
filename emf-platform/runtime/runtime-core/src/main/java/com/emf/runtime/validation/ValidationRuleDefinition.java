package com.emf.runtime.validation;

/**
 * Lightweight definition of a validation rule for runtime evaluation.
 *
 * <p>This record carries rule metadata from the control plane to the worker
 * so that validation rules can be evaluated during record create/update
 * without requiring a round-trip to the control plane.
 *
 * @param name                  unique rule name within the collection
 * @param errorConditionFormula formula that evaluates to TRUE when the record is INVALID
 * @param errorMessage          user-facing error message
 * @param errorField            optional field to attach the error to (may be null)
 * @param evaluateOn            when to evaluate: "CREATE", "UPDATE", or "CREATE_AND_UPDATE"
 * @param active                whether the rule is active
 */
public record ValidationRuleDefinition(
        String name,
        String errorConditionFormula,
        String errorMessage,
        String errorField,
        String evaluateOn,
        boolean active
) {
    /**
     * Returns true if this rule applies to the given operation type.
     *
     * @param operationType "CREATE" or "UPDATE"
     * @return true if the rule should be evaluated
     */
    public boolean appliesTo(String operationType) {
        return "CREATE_AND_UPDATE".equals(evaluateOn)
                || evaluateOn.equals(operationType);
    }
}
