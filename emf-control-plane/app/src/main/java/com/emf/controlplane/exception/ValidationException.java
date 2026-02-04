package com.emf.controlplane.exception;

/**
 * Exception thrown when validation fails for a request.
 */
public class ValidationException extends RuntimeException {

    private final String fieldName;
    private final String errorMessage;

    public ValidationException(String fieldName, String errorMessage) {
        super(String.format("Validation failed for field '%s': %s", fieldName, errorMessage));
        this.fieldName = fieldName;
        this.errorMessage = errorMessage;
    }

    public ValidationException(String message) {
        super(message);
        this.fieldName = null;
        this.errorMessage = message;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
