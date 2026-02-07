package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MultiPicklistFieldValidator implements FieldTypeValidator {

    @Override
    public String getFieldType() {
        return "MULTI_PICKLIST";
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config != null && config.has("globalPicklistId")) {
            JsonNode id = config.get("globalPicklistId");
            if (!id.isTextual() || id.asText().isBlank()) {
                throw new ValidationException("fieldTypeConfig.globalPicklistId", "Must be a non-empty string");
            }
        }
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null && !(value instanceof List)) {
            throw new ValidationException("value", "MULTI_PICKLIST value must be an array of strings");
        }
    }
}
