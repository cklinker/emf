package com.emf.runtime.model;

/**
 * Validation rules for a field definition.
 * 
 * <p>All constraints are optional. When specified, they are enforced by the validation engine.
 * 
 * @param minValue Minimum value for numeric fields (INTEGER, LONG, DOUBLE)
 * @param maxValue Maximum value for numeric fields (INTEGER, LONG, DOUBLE)
 * @param minLength Minimum length for string fields
 * @param maxLength Maximum length for string fields
 * @param pattern Regex pattern for string validation
 * 
 * @since 1.0.0
 */
public record ValidationRules(
    Integer minValue,
    Integer maxValue,
    Integer minLength,
    Integer maxLength,
    String pattern
) {
    /**
     * Creates an empty validation rules instance with no constraints.
     * 
     * @return empty validation rules
     */
    public static ValidationRules empty() {
        return new ValidationRules(null, null, null, null, null);
    }
    
    /**
     * Creates validation rules with only min/max value constraints.
     * 
     * @param minValue minimum value (nullable)
     * @param maxValue maximum value (nullable)
     * @return validation rules with value constraints
     */
    public static ValidationRules forNumeric(Integer minValue, Integer maxValue) {
        return new ValidationRules(minValue, maxValue, null, null, null);
    }
    
    /**
     * Creates validation rules with only length constraints.
     * 
     * @param minLength minimum length (nullable)
     * @param maxLength maximum length (nullable)
     * @return validation rules with length constraints
     */
    public static ValidationRules forString(Integer minLength, Integer maxLength) {
        return new ValidationRules(null, null, minLength, maxLength, null);
    }
    
    /**
     * Creates validation rules with length and pattern constraints.
     * 
     * @param minLength minimum length (nullable)
     * @param maxLength maximum length (nullable)
     * @param pattern regex pattern (nullable)
     * @return validation rules with string constraints
     */
    public static ValidationRules forString(Integer minLength, Integer maxLength, String pattern) {
        return new ValidationRules(null, null, minLength, maxLength, pattern);
    }
}
