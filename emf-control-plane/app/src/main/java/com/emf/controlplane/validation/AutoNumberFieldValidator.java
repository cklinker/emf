package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class AutoNumberFieldValidator implements FieldTypeValidator {

    @Override
    public String getFieldType() {
        return "AUTO_NUMBER";
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || !config.has("prefix")) {
            throw new ValidationException("fieldTypeConfig", "AUTO_NUMBER requires 'prefix' in fieldTypeConfig");
        }
        if (!config.has("padding") || config.get("padding").asInt() < 1 || config.get("padding").asInt() > 10) {
            throw new ValidationException("fieldTypeConfig.padding", "AUTO_NUMBER requires 'padding' between 1 and 10");
        }
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        // AUTO_NUMBER values are system-generated, not user-provided
    }
}
