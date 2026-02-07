package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class EncryptedFieldValidator implements FieldTypeValidator {

    @Override
    public String getFieldType() {
        return "ENCRYPTED";
    }

    @Override
    public void validateConfig(JsonNode config) {
        // Algorithm defaults to AES-256-GCM, no required config
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null && !(value instanceof String)) {
            throw new ValidationException("value", "ENCRYPTED value must be a string");
        }
    }
}
