package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RollupSummaryFieldValidator implements FieldTypeValidator {

    private static final Set<String> VALID_FUNCTIONS = Set.of("COUNT", "SUM", "MIN", "MAX", "AVG");

    @Override
    public String getFieldType() {
        return "ROLLUP_SUMMARY";
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null) {
            throw new ValidationException("fieldTypeConfig", "ROLLUP_SUMMARY requires fieldTypeConfig");
        }
        if (!config.has("childCollection")) {
            throw new ValidationException("fieldTypeConfig.childCollection",
                    "ROLLUP_SUMMARY requires 'childCollection'");
        }
        String fn = config.has("aggregateFunction") ? config.get("aggregateFunction").asText() : null;
        if (!VALID_FUNCTIONS.contains(fn)) {
            throw new ValidationException("fieldTypeConfig.aggregateFunction",
                    "ROLLUP_SUMMARY aggregateFunction must be one of: " + String.join(", ", VALID_FUNCTIONS));
        }
        if (!"COUNT".equals(fn) && !config.has("aggregateField")) {
            throw new ValidationException("fieldTypeConfig.aggregateField",
                    "ROLLUP_SUMMARY requires 'aggregateField' for " + fn);
        }
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null) {
            throw new ValidationException("value", "ROLLUP_SUMMARY fields cannot be set directly");
        }
    }
}
