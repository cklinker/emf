package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class PercentFieldValidator implements FieldTypeValidator {

    @Override
    public String getFieldType() {
        return "PERCENT";
    }

    @Override
    public void validateConfig(JsonNode config) {
        // No required config for PERCENT fields
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null && !(value instanceof Number)) {
            throw new ValidationException("value", "PERCENT value must be a number");
        }
    }
}
