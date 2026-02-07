package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class UrlFieldValidator implements FieldTypeValidator {

    @Override
    public String getFieldType() {
        return "URL";
    }

    @Override
    public void validateConfig(JsonNode config) {
        // No required config for URL fields
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null) {
            if (!(value instanceof String s)) {
                throw new ValidationException("value", "URL value must be a string");
            }
            try {
                URI uri = URI.create(s);
                if (uri.getScheme() == null || uri.getHost() == null) {
                    throw new ValidationException("value", "URL must have a scheme and host");
                }
            } catch (IllegalArgumentException e) {
                throw new ValidationException("value", "Invalid URL format");
            }
        }
    }
}
