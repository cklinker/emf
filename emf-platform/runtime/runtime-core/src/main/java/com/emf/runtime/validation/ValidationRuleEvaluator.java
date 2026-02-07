package com.emf.runtime.validation;

import com.emf.runtime.formula.FormulaEvaluator;
import com.emf.runtime.formula.FormulaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Evaluates validation rule formulas against record data.
 * Wraps FormulaEvaluator with error-resilient boolean evaluation.
 */
@Component
public class ValidationRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ValidationRuleEvaluator.class);

    private final FormulaEvaluator formulaEvaluator;

    public ValidationRuleEvaluator(FormulaEvaluator formulaEvaluator) {
        this.formulaEvaluator = formulaEvaluator;
    }

    /**
     * Evaluates a validation rule's error condition against a record.
     *
     * @param errorConditionFormula formula that returns TRUE when the record is INVALID
     * @param recordData            record's field values
     * @return true if record FAILS validation (error condition is true)
     */
    public boolean isInvalid(String errorConditionFormula, Map<String, Object> recordData) {
        try {
            return formulaEvaluator.evaluateBoolean(errorConditionFormula, recordData);
        } catch (FormulaException e) {
            log.warn("Validation rule formula evaluation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates that a formula expression is syntactically correct.
     * Called when creating or updating a validation rule.
     *
     * @throws FormulaException if expression has syntax errors
     */
    public void validateFormulaSyntax(String expression) {
        formulaEvaluator.validate(expression);
    }
}
