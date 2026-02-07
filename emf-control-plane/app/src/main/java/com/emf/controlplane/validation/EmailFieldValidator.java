package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class EmailFieldValidator implements FieldTypeValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@]+@[^@]+\\.[^@]+$");

    @Override
    public String getFieldType() {
        return "EMAIL";
    }

    @Override
    public void validateConfig(JsonNode config) {
        // No required config for EMAIL fields
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null) {
            if (!(value instanceof String s)) {
                throw new ValidationException("value", "EMAIL value must be a string");
            }
            if (!EMAIL_PATTERN.matcher(s).matches()) {
                throw new ValidationException("value", "Invalid email format");
            }
        }
    }
}
