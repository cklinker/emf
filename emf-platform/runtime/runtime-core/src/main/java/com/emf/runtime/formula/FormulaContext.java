package com.emf.runtime.formula;

import java.util.Map;

/**
 * Context for formula evaluation containing field values and available functions.
 */
public record FormulaContext(
    Map<String, Object> fieldValues,
    Map<String, FormulaFunction> functions
) {
    public Object getFieldValue(String name) {
        return fieldValues.get(name);
    }

    public FormulaFunction getFunction(String name) {
        return functions.get(name);
    }
}
