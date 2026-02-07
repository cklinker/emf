package com.emf.controlplane.validation;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Validates field type-specific configuration and values.
 * One implementation per FieldType that requires type-specific validation.
 */
public interface FieldTypeValidator {

    /**
     * The field type this validator handles (canonical uppercase name).
     */
    String getFieldType();

    /**
     * Validates the fieldTypeConfig JSON when a field of this type is created or updated.
     *
     * @param fieldTypeConfig the configuration JSON, may be null
     * @throws com.emf.controlplane.exception.ValidationException if config is invalid
     */
    void validateConfig(JsonNode fieldTypeConfig);

    /**
     * Validates a field value during record create/update.
     *
     * @param value           the value to validate
     * @param fieldTypeConfig the field's type-specific configuration
     * @throws com.emf.controlplane.exception.ValidationException if value is invalid
     */
    void validateValue(Object value, JsonNode fieldTypeConfig);
}
