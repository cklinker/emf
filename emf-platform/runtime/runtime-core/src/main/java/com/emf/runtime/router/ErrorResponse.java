package com.emf.runtime.router;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Standard error response format for API errors.
 * 
 * <p>Provides a consistent structure for all error responses:
 * <pre>{@code
 * {
 *   "requestId": "abc-123",
 *   "timestamp": "2024-01-15T10:30:00Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Validation failed",
 *   "path": "/api/collections/products",
 *   "errors": [
 *     {"field": "name", "message": "Field is required", "code": "nullable"}
 *   ]
 * }
 * }</pre>
 * 
 * @param requestId unique identifier for the request
 * @param timestamp when the error occurred
 * @param status HTTP status code
 * @param error HTTP status reason phrase
 * @param message human-readable error message
 * @param path the request path
 * @param errors list of field-level errors (may be empty)
 * 
 * @since 1.0.0
 */
public record ErrorResponse(
    String requestId,
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldErrorResponse> errors
) {
    /**
     * Compact constructor with validation.
     */
    public ErrorResponse {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(error, "error cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        errors = errors != null ? List.copyOf(errors) : List.of();
    }
    
    /**
     * Creates an error response without field-level errors.
     */
    public static ErrorResponse of(String requestId, int status, String error, String message, String path) {
        return new ErrorResponse(requestId, Instant.now(), status, error, message, path, List.of());
    }
    
    /**
     * Creates an error response with field-level errors.
     */
    public static ErrorResponse withErrors(String requestId, int status, String error, String message, 
                                           String path, List<FieldErrorResponse> errors) {
        return new ErrorResponse(requestId, Instant.now(), status, error, message, path, errors);
    }
    
    /**
     * Field-level error details.
     * 
     * @param field the field name
     * @param message the error message
     * @param code the error code/constraint name
     */
    public record FieldErrorResponse(
        String field,
        String message,
        String code
    ) {
        public FieldErrorResponse {
            Objects.requireNonNull(field, "field cannot be null");
            Objects.requireNonNull(message, "message cannot be null");
        }
    }
}
