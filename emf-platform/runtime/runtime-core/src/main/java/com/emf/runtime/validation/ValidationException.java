package com.emf.runtime.validation;

/**
 * Exception thrown when validation fails.
 * 
 * <p>This exception wraps a {@link ValidationResult} containing detailed
 * information about all validation errors.
 * 
 * @since 1.0.0
 */
public class ValidationException extends RuntimeException {
    
    private final ValidationResult validationResult;
    
    /**
     * Creates a new ValidationException.
     * 
     * @param validationResult the validation result containing errors
     */
    public ValidationException(ValidationResult validationResult) {
        super(buildMessage(validationResult));
        this.validationResult = validationResult;
    }
    
    /**
     * Creates a new ValidationException with a custom message.
     * 
     * @param message the error message
     */
    public ValidationException(String message) {
        super(message);
        this.validationResult = null;
    }
    
    /**
     * Gets the validation result containing detailed error information.
     * 
     * @return the validation result, or null if not available
     */
    public ValidationResult getValidationResult() {
        return validationResult;
    }
    
    private static String buildMessage(ValidationResult result) {
        if (result == null || result.errors().isEmpty()) {
            return "Validation failed";
        }
        
        StringBuilder sb = new StringBuilder("Validation failed: ");
        sb.append(result.errors().size()).append(" error(s) - ");
        
        result.errors().stream()
            .limit(3)
            .forEach(error -> sb.append(error.fieldName()).append(": ").append(error.message()).append("; "));
        
        if (result.errors().size() > 3) {
            sb.append("and ").append(result.errors().size() - 3).append(" more");
        }
        
        return sb.toString();
    }
}
