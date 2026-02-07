package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FormulaFieldValidator implements FieldTypeValidator {

    private static final Set<String> VALID_RETURN_TYPES = Set.of(
            "STRING", "DOUBLE", "BOOLEAN", "DATE", "DATETIME"
    );

    @Override
    public String getFieldType() {
        return "FORMULA";
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || !config.has("expression") || !config.has("returnType")) {
            throw new ValidationException("fieldTypeConfig",
                    "FORMULA requires 'expression' and 'returnType' in fieldTypeConfig");
        }
        String returnType = config.get("returnType").asText();
        if (!VALID_RETURN_TYPES.contains(returnType)) {
            throw new ValidationException("fieldTypeConfig.returnType",
                    "FORMULA returnType must be one of: " + String.join(", ", VALID_RETURN_TYPES));
        }
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null) {
            throw new ValidationException("value", "FORMULA fields cannot be set directly");
        }
    }
}
