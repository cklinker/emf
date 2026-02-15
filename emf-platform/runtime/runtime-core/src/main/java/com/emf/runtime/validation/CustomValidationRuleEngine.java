package com.emf.runtime.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates custom (formula-based) validation rules against record data.
 *
 * <p>This engine reads rules from the {@link ValidationRuleRegistry} and
 * evaluates each rule's error condition formula using the
 * {@link ValidationRuleEvaluator}. If any rule's formula evaluates to
 * {@code true}, the record is considered invalid and a
 * {@link RecordValidationException} is thrown.
 *
 * <p>This is called by {@code DefaultQueryEngine} after field-level
 * validation and before persistence.
 */
public class CustomValidationRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(CustomValidationRuleEngine.class);

    private final ValidationRuleRegistry ruleRegistry;
    private final ValidationRuleEvaluator ruleEvaluator;

    public CustomValidationRuleEngine(ValidationRuleRegistry ruleRegistry,
                                       ValidationRuleEvaluator ruleEvaluator) {
        this.ruleRegistry = ruleRegistry;
        this.ruleEvaluator = ruleEvaluator;
    }

    /**
     * Evaluates all active validation rules for the given collection and
     * operation type against the record data.
     *
     * @param collectionName the collection name
     * @param recordData     the record's field values
     * @param operationType  the operation type (CREATE or UPDATE)
     * @throws RecordValidationException if any rules are violated
     */
    public void evaluate(String collectionName, Map<String, Object> recordData,
                         OperationType operationType) {
        List<ValidationRuleDefinition> rules = ruleRegistry.getActiveRules(collectionName);
        if (rules.isEmpty()) {
            return;
        }

        String opType = operationType.name();
        List<ValidationError> errors = new ArrayList<>();

        for (ValidationRuleDefinition rule : rules) {
            if (!rule.appliesTo(opType)) {
                continue;
            }

            try {
                if (ruleEvaluator.isInvalid(rule.errorConditionFormula(), recordData)) {
                    errors.add(new ValidationError(
                            rule.name(),
                            rule.errorMessage(),
                            rule.errorField()));
                    log.debug("Validation rule '{}' failed for collection '{}'",
                            rule.name(), collectionName);
                }
            } catch (Exception e) {
                // Don't block saves for broken formulas — log and skip
                log.warn("Error evaluating validation rule '{}' for collection '{}': {}",
                        rule.name(), collectionName, e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new RecordValidationException(errors);
        }
    }
}
