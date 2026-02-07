package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class PicklistFieldValidator implements FieldTypeValidator {

    @Override
    public String getFieldType() {
        return "PICKLIST";
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
        if (value != null && !(value instanceof String)) {
            throw new ValidationException("value", "PICKLIST value must be a string");
        }
    }
}
