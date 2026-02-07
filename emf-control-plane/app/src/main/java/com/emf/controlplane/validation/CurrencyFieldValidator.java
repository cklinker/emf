package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class CurrencyFieldValidator implements FieldTypeValidator {

    @Override
    public String getFieldType() {
        return "CURRENCY";
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config != null && config.has("precision")) {
            int precision = config.get("precision").asInt();
            if (precision < 0 || precision > 6) {
                throw new ValidationException("fieldTypeConfig.precision", "CURRENCY precision must be 0-6");
            }
        }
        if (config != null && config.has("defaultCurrencyCode")) {
            String code = config.get("defaultCurrencyCode").asText();
            if (code.length() != 3) {
                throw new ValidationException("fieldTypeConfig.defaultCurrencyCode", "Must be a 3-letter ISO 4217 code");
            }
        }
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null && !(value instanceof Number)) {
            throw new ValidationException("value", "CURRENCY value must be a number");
        }
    }
}
