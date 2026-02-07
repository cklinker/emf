package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PhoneFieldValidator implements FieldTypeValidator {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[\\d\\s\\-().]+$");

    @Override
    public String getFieldType() {
        return "PHONE";
    }

    @Override
    public void validateConfig(JsonNode config) {
        // No required config for PHONE fields
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null) {
            if (!(value instanceof String s)) {
                throw new ValidationException("value", "PHONE value must be a string");
            }
            if (!PHONE_PATTERN.matcher(s).matches()) {
                throw new ValidationException("value", "Invalid phone number format");
            }
        }
    }
}
