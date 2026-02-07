package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class ExternalIdFieldValidator implements FieldTypeValidator {

    @Override
    public String getFieldType() {
        return "EXTERNAL_ID";
    }

    @Override
    public void validateConfig(JsonNode config) {
        // No required config for EXTERNAL_ID fields
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null && !(value instanceof String)) {
            throw new ValidationException("value", "EXTERNAL_ID value must be a string");
        }
    }
}
