package com.emf.runtime.validation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Result of a validation operation.
 * 
 * <p>Contains whether the validation passed and a list of field-level errors if it failed.
 * Provides a method to convert the result to an HTTP error response format.
 * 
 * @param valid true if validation passed, false otherwise
 * @param errors list of field-level errors (empty if valid)
 * 
 * @since 1.0.0
 */
public record ValidationResult(
    boolean valid,
    List<FieldError> errors
) {
    /**
     * Compact constructor with validation and defensive copying.
     */
    public ValidationResult {
        Objects.requireNonNull(errors, "errors cannot be null");
        errors = List.copyOf(errors);
    }
    
    /**
     * Creates a successful validation result.
     * 
     * @return a ValidationResult indicating success
     */
    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }
    
    /**
     * Creates a failed validation result with the given errors.
     * 
     * @param errors the list of field errors
     * @return a ValidationResult indicating failure
     */
    public static ValidationResult failure(List<FieldError> errors) {
        Objects.requireNonNull(errors, "errors cannot be null");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("failure result must have at least one error");
        }
        return new ValidationResult(false, errors);
    }
    
    /**
     * Creates a failed validation result with a single error.
     * 
     * @param error the field error
     * @return a ValidationResult indicating failure
     */
    public static ValidationResult failure(FieldError error) {
        Objects.requireNonNull(error, "error cannot be null");
        return new ValidationResult(false, List.of(error));
    }
    
    /**
     * Converts this validation result to an HTTP error response format.
     * 
     * <p>The response format groups errors by field name:
     * <pre>{@code
     * {
     *   "valid": false,
     *   "errors": {
     *     "fieldName1": ["error message 1", "error message 2"],
     *     "fieldName2": ["error message 3"]
     *   }
     * }
     * }</pre>
     * 
     * @return a map suitable for JSON serialization as an HTTP response body
     */
    public Map<String, Object> toErrorResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("valid", valid);
        
        if (!valid) {
            Map<String, List<String>> groupedErrors = errors.stream()
                .collect(Collectors.groupingBy(
                    FieldError::fieldName,
                    Collectors.mapping(FieldError::message, Collectors.toList())
                ));
            response.put("errors", groupedErrors);
        }
        
        return response;
    }
    
    /**
     * Checks if this result has any errors for the specified field.
     * 
     * @param fieldName the field name to check
     * @return true if there are errors for the field, false otherwise
     */
    public boolean hasErrorsForField(String fieldName) {
        return errors.stream().anyMatch(e -> e.fieldName().equals(fieldName));
    }
    
    /**
     * Gets all errors for a specific field.
     * 
     * @param fieldName the field name
     * @return list of errors for the field (may be empty)
     */
    public List<FieldError> getErrorsForField(String fieldName) {
        return errors.stream()
            .filter(e -> e.fieldName().equals(fieldName))
            .toList();
    }
    
    /**
     * Gets the count of validation errors.
     * 
     * @return the number of errors
     */
    public int errorCount() {
        return errors.size();
    }
}
