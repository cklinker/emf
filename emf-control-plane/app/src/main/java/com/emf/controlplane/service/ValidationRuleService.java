package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateValidationRuleRequest;
import com.emf.controlplane.dto.UpdateValidationRuleRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.ValidationRule;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.repository.ValidationRuleRepository;
import com.emf.runtime.formula.FormulaException;
import com.emf.runtime.validation.ValidationError;
import com.emf.runtime.validation.ValidationRuleEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ValidationRuleService {

    private static final Logger log = LoggerFactory.getLogger(ValidationRuleService.class);
    private static final Set<String> VALID_EVALUATE_ON = Set.of("CREATE", "UPDATE", "CREATE_AND_UPDATE");

    private final ValidationRuleRepository validationRuleRepository;
    private final CollectionRepository collectionRepository;
    private final FieldRepository fieldRepository;
    private final ValidationRuleEvaluator validationRuleEvaluator;

    public ValidationRuleService(
            ValidationRuleRepository validationRuleRepository,
            CollectionRepository collectionRepository,
            FieldRepository fieldRepository,
            @Nullable ValidationRuleEvaluator validationRuleEvaluator) {
        this.validationRuleRepository = validationRuleRepository;
        this.collectionRepository = collectionRepository;
        this.fieldRepository = fieldRepository;
        this.validationRuleEvaluator = validationRuleEvaluator;
    }

    @Transactional(readOnly = true)
    public List<ValidationRule> listRules(String collectionId) {
        verifyCollection(collectionId);
        return validationRuleRepository.findByCollectionIdOrderByNameAsc(collectionId);
    }

    @Transactional
    public ValidationRule createRule(String collectionId, String tenantId,
                                     CreateValidationRuleRequest request) {
        log.info("Creating validation rule '{}' for collection: {}", request.getName(), collectionId);

        Collection collection = verifyCollection(collectionId);

        if (validationRuleRepository.existsByTenantIdAndCollectionIdAndName(
                tenantId, collectionId, request.getName())) {
            throw new DuplicateResourceException("ValidationRule", "name", request.getName());
        }

        // Validate formula syntax
        validateFormula(request.getErrorConditionFormula());

        // Validate errorField exists if provided
        if (request.getErrorField() != null && !request.getErrorField().isBlank()) {
            if (!fieldRepository.existsByCollectionIdAndNameAndActiveTrue(collectionId, request.getErrorField())) {
                throw new ValidationException("errorField",
                        "Field '" + request.getErrorField() + "' not found in collection");
            }
        }

        // Validate evaluateOn
        String evaluateOn = request.getEvaluateOn();
        if (evaluateOn != null && !evaluateOn.isBlank()) {
            if (!VALID_EVALUATE_ON.contains(evaluateOn)) {
                throw new ValidationException("evaluateOn",
                        "Must be one of: " + String.join(", ", VALID_EVALUATE_ON));
            }
        } else {
            evaluateOn = "CREATE_AND_UPDATE";
        }

        ValidationRule rule = new ValidationRule(tenantId, collection, request.getName(),
                request.getErrorConditionFormula(), request.getErrorMessage());
        rule.setDescription(request.getDescription());
        rule.setErrorField(request.getErrorField());
        rule.setEvaluateOn(evaluateOn);

        rule = validationRuleRepository.save(rule);
        log.info("Created validation rule '{}' with id: {}", request.getName(), rule.getId());
        return rule;
    }

    @Transactional(readOnly = true)
    public ValidationRule getRule(String ruleId) {
        return validationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("ValidationRule", ruleId));
    }

    @Transactional
    public ValidationRule updateRule(String ruleId, UpdateValidationRuleRequest request) {
        log.info("Updating validation rule: {}", ruleId);

        ValidationRule rule = validationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("ValidationRule", ruleId));

        if (request.getName() != null && !request.getName().equals(rule.getName())) {
            if (validationRuleRepository.existsByTenantIdAndCollectionIdAndName(
                    rule.getTenantId(), rule.getCollection().getId(), request.getName())) {
                throw new DuplicateResourceException("ValidationRule", "name", request.getName());
            }
            rule.setName(request.getName());
        }

        if (request.getDescription() != null) {
            rule.setDescription(request.getDescription());
        }

        if (request.getErrorConditionFormula() != null) {
            validateFormula(request.getErrorConditionFormula());
            rule.setErrorConditionFormula(request.getErrorConditionFormula());
        }

        if (request.getErrorMessage() != null) {
            rule.setErrorMessage(request.getErrorMessage());
        }

        if (request.getErrorField() != null) {
            rule.setErrorField(request.getErrorField().isBlank() ? null : request.getErrorField());
        }

        if (request.getEvaluateOn() != null) {
            if (!VALID_EVALUATE_ON.contains(request.getEvaluateOn())) {
                throw new ValidationException("evaluateOn",
                        "Must be one of: " + String.join(", ", VALID_EVALUATE_ON));
            }
            rule.setEvaluateOn(request.getEvaluateOn());
        }

        if (request.getActive() != null) {
            rule.setActive(request.getActive());
        }

        return validationRuleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(String ruleId) {
        log.info("Deleting validation rule: {}", ruleId);
        ValidationRule rule = validationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("ValidationRule", ruleId));
        validationRuleRepository.delete(rule);
    }

    @Transactional
    public void activateRule(String ruleId) {
        ValidationRule rule = validationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("ValidationRule", ruleId));
        rule.setActive(true);
        validationRuleRepository.save(rule);
    }

    @Transactional
    public void deactivateRule(String ruleId) {
        ValidationRule rule = validationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("ValidationRule", ruleId));
        rule.setActive(false);
        validationRuleRepository.save(rule);
    }

    /**
     * Evaluates all active rules for a collection against a record.
     *
     * @param collectionId  the collection ID
     * @param recordData    the record's field values
     * @param operationType "CREATE" or "UPDATE"
     * @return list of validation errors (empty if all rules pass)
     */
    @Transactional(readOnly = true)
    public List<ValidationError> evaluate(String collectionId,
                                           Map<String, Object> recordData,
                                           String operationType) {
        if (validationRuleEvaluator == null) {
            return List.of();
        }

        List<ValidationRule> rules = validationRuleRepository
                .findByCollectionIdAndActiveTrueOrderByNameAsc(collectionId);

        List<ValidationError> errors = new ArrayList<>();
        for (ValidationRule rule : rules) {
            if (!appliesTo(rule.getEvaluateOn(), operationType)) {
                continue;
            }

            if (validationRuleEvaluator.isInvalid(rule.getErrorConditionFormula(), recordData)) {
                errors.add(new ValidationError(
                        rule.getName(),
                        rule.getErrorMessage(),
                        rule.getErrorField()));
            }
        }
        return errors;
    }

    /**
     * Tests all active rules against a sample record without persisting.
     */
    @Transactional(readOnly = true)
    public List<ValidationError> testRules(String collectionId, Map<String, Object> testRecord) {
        return evaluate(collectionId, testRecord, "CREATE_AND_UPDATE");
    }

    private boolean appliesTo(String evaluateOn, String operationType) {
        return "CREATE_AND_UPDATE".equals(evaluateOn)
                || evaluateOn.equals(operationType);
    }

    private void validateFormula(String formula) {
        if (validationRuleEvaluator != null) {
            try {
                validationRuleEvaluator.validateFormulaSyntax(formula);
            } catch (FormulaException e) {
                throw new ValidationException("errorConditionFormula",
                        "Invalid formula syntax: " + e.getMessage());
            }
        }
    }

    private Collection verifyCollection(String collectionId) {
        return collectionRepository.findByIdAndActiveTrue(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", collectionId));
    }
}
