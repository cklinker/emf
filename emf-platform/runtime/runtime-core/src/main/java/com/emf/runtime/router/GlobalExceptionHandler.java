package com.emf.runtime.router;

import com.emf.runtime.query.InvalidQueryException;
import com.emf.runtime.storage.StorageException;
import com.emf.runtime.storage.UniqueConstraintViolationException;
import com.emf.runtime.validation.ValidationException;
import com.emf.runtime.validation.ValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for the REST API.
 * 
 * <p>Maps exceptions to appropriate HTTP status codes and error responses:
 * <ul>
 *   <li>ValidationException → 400 Bad Request</li>
 *   <li>InvalidQueryException → 400 Bad Request</li>
 *   <li>UniqueConstraintViolationException → 409 Conflict</li>
 *   <li>StorageException → 500 Internal Server Error</li>
 *   <li>Other exceptions → 500 Internal Server Error</li>
 * </ul>
 * 
 * <p>All error responses include a unique request ID for tracing.
 * 
 * @since 1.0.0
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handles validation exceptions.
     * Returns 400 Bad Request with field-level error details.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex, HttpServletRequest request) {
        
        String requestId = generateRequestId();
        logger.warn("Validation failed [requestId={}]: {}", requestId, ex.getMessage());
        
        List<ErrorResponse.FieldErrorResponse> fieldErrors = List.of();
        ValidationResult result = ex.getValidationResult();
        if (result != null) {
            fieldErrors = result.errors().stream()
                .map(e -> new ErrorResponse.FieldErrorResponse(e.fieldName(), e.message(), e.constraint()))
                .collect(Collectors.toList());
        }
        
        ErrorResponse response = ErrorResponse.withErrors(
            requestId,
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "Validation failed",
            request.getRequestURI(),
            fieldErrors
        );
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * Handles invalid query exceptions.
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQueryException(
            InvalidQueryException ex, HttpServletRequest request) {
        
        String requestId = generateRequestId();
        logger.warn("Invalid query [requestId={}]: {}", requestId, ex.getMessage());
        
        List<ErrorResponse.FieldErrorResponse> fieldErrors = List.of();
        if (ex.getFieldName() != null) {
            fieldErrors = List.of(new ErrorResponse.FieldErrorResponse(
                ex.getFieldName(), ex.getReason(), "invalidQuery"));
        }
        
        ErrorResponse response = ErrorResponse.withErrors(
            requestId,
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI(),
            fieldErrors
        );
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles unique constraint violation exceptions.
     * Returns 409 Conflict.
     */
    @ExceptionHandler(UniqueConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleUniqueConstraintViolation(
            UniqueConstraintViolationException ex, HttpServletRequest request) {
        
        String requestId = generateRequestId();
        logger.warn("Unique constraint violation [requestId={}]: {}", requestId, ex.getMessage());
        
        List<ErrorResponse.FieldErrorResponse> fieldErrors = List.of(
            new ErrorResponse.FieldErrorResponse(ex.getFieldName(), ex.getMessage(), "unique")
        );
        
        ErrorResponse response = ErrorResponse.withErrors(
            requestId,
            HttpStatus.CONFLICT.value(),
            "Conflict",
            "Unique constraint violation",
            request.getRequestURI(),
            fieldErrors
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * Handles storage exceptions.
     * Returns 500 Internal Server Error with generic message.
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageException(
            StorageException ex, HttpServletRequest request) {
        
        String requestId = generateRequestId();
        logger.error("Storage error [requestId={}]: {}", requestId, ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.of(
            requestId,
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An error occurred while processing your request",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Handles all other exceptions.
     * Returns 500 Internal Server Error with generic message.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        String requestId = generateRequestId();
        logger.error("Unexpected error [requestId={}]: {}", requestId, ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.of(
            requestId,
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Generates a unique request ID for tracing.
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
