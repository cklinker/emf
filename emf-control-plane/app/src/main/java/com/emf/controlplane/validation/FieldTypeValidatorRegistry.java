package com.emf.controlplane.validation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Registry of {@link FieldTypeValidator} implementations.
 * Auto-discovers all validators via Spring component scan.
 */
@Component
public class FieldTypeValidatorRegistry {

    private final Map<String, FieldTypeValidator> validators;

    public FieldTypeValidatorRegistry(List<FieldTypeValidator> validatorList) {
        this.validators = validatorList.stream()
                .collect(Collectors.toMap(FieldTypeValidator::getFieldType, v -> v));
    }

    /**
     * Returns the validator for the given field type, if one exists.
     */
    public Optional<FieldTypeValidator> getValidator(String fieldType) {
        return Optional.ofNullable(validators.get(fieldType));
    }
}
