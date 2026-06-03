package io.kelta.runtime.validation;

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
 * @param severity              "ERROR" blocks the save; "WARNING" is logged only
 */
public record ValidationRuleDefinition(
        String name,
        String errorConditionFormula,
        String errorMessage,
        String errorField,
        String evaluateOn,
        boolean active,
        String severity
) {
    public static final String SEVERITY_ERROR = "ERROR";
    public static final String SEVERITY_WARNING = "WARNING";

    public ValidationRuleDefinition {
        if (severity == null || severity.isBlank()) {
            severity = SEVERITY_ERROR;
        }
    }

    /**
     * Legacy constructor without severity; defaults to {@code ERROR}.
     */
    public ValidationRuleDefinition(String name, String errorConditionFormula,
                                    String errorMessage, String errorField,
                                    String evaluateOn, boolean active) {
        this(name, errorConditionFormula, errorMessage, errorField,
                evaluateOn, active, SEVERITY_ERROR);
    }

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

    /**
     * True when this rule must block the save when it matches.
     * Returns false for {@code WARNING}-severity rules.
     */
    public boolean isBlocking() {
        return SEVERITY_ERROR.equalsIgnoreCase(severity);
    }
}
