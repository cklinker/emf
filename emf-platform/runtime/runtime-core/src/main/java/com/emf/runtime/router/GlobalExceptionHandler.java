package com.emf.runtime.router;

import com.emf.jsonapi.JsonApiError;
import com.emf.runtime.query.InvalidQueryException;
import com.emf.runtime.storage.StorageException;
import com.emf.runtime.storage.UniqueConstraintViolationException;
import com.emf.runtime.validation.RecordValidationException;
import com.emf.runtime.validation.ValidationException;
import com.emf.runtime.validation.ValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for the REST API.
 *
 * <p>Maps exceptions to appropriate HTTP status codes and JSON:API error responses:
 * <ul>
 *   <li>ValidationException -> 400 Bad Request</li>
 *   <li>InvalidQueryException -> 400 Bad Request</li>
 *   <li>UniqueConstraintViolationException -> 409 Conflict</li>
 *   <li>StorageException -> 500 Internal Server Error</li>
 *   <li>Other exceptions -> 500 Internal Server Error</li>
 * </ul>
 *
 * <p>All error responses follow the JSON:API error format with a unique request ID for tracing.
 *
 * @since 1.0.0
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles validation exceptions.
     * Returns 400 Bad Request with field-level error details in JSON:API format.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            ValidationException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Validation failed [requestId={}]: {}", requestId, ex.getMessage());

        List<JsonApiError> errors = new ArrayList<>();
        ValidationResult result = ex.getValidationResult();
        if (result != null && !result.errors().isEmpty()) {
            for (var fieldError : result.errors()) {
                JsonApiError error = new JsonApiError(
                    "400", fieldError.constraint(), "Validation Error", fieldError.message());
                error.setSource(Map.of("pointer", "/data/attributes/" + fieldError.fieldName()));
                error.setMeta(Map.of("requestId", requestId));
                errors.add(error);
            }
        } else {
            JsonApiError error = new JsonApiError("400", "validation", "Validation Error", ex.getMessage());
            error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));
            errors.add(error);
        }

        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    /**
     * Handles record validation rule exceptions (custom formula-based rules).
     * Returns 400 Bad Request with validation rule error details in JSON:API format.
     */
    @ExceptionHandler(RecordValidationException.class)
    public ResponseEntity<Map<String, Object>> handleRecordValidationException(
            RecordValidationException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Record validation rule(s) failed [requestId={}]: {}", requestId, ex.getMessage());

        List<JsonApiError> errors = new ArrayList<>();
        for (var ruleError : ex.getErrors()) {
            String fieldName = ruleError.errorField() != null ? ruleError.errorField() : ruleError.ruleName();
            JsonApiError error = new JsonApiError(
                "400", "validationRule", "Validation Error", ruleError.errorMessage());
            error.setSource(Map.of("pointer", "/data/attributes/" + fieldName));
            error.setMeta(Map.of("requestId", requestId));
            errors.add(error);
        }

        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    /**
     * Handles invalid query exceptions.
     * Returns 400 Bad Request in JSON:API format.
     */
    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidQueryException(
            InvalidQueryException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Invalid query [requestId={}]: {}", requestId, ex.getMessage());

        List<JsonApiError> errors = new ArrayList<>();
        if (ex.getFieldName() != null) {
            JsonApiError error = new JsonApiError("400", "invalidQuery", "Bad Request", ex.getReason());
            error.setSource(Map.of("pointer", "/data/attributes/" + ex.getFieldName()));
            error.setMeta(Map.of("requestId", requestId));
            errors.add(error);
        } else {
            JsonApiError error = new JsonApiError("400", "invalidQuery", "Bad Request", ex.getMessage());
            error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));
            errors.add(error);
        }

        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    /**
     * Handles unique constraint violation exceptions.
     * Returns 409 Conflict in JSON:API format.
     */
    @ExceptionHandler(UniqueConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleUniqueConstraintViolation(
            UniqueConstraintViolationException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Unique constraint violation [requestId={}]: {}", requestId, ex.getMessage());

        JsonApiError error = new JsonApiError("409", "unique", "Conflict", ex.getMessage());
        error.setSource(Map.of("pointer", "/data/attributes/" + ex.getFieldName()));
        error.setMeta(Map.of("requestId", requestId));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles storage exceptions.
     * Returns 500 Internal Server Error with generic message in JSON:API format.
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorageException(
            StorageException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.error("Storage error [requestId={}]: {}", requestId, ex.getMessage(), ex);

        JsonApiError error = new JsonApiError(
            "500", "storageError", "Internal Server Error",
            "An error occurred while processing your request");
        error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles all other exceptions.
     * Returns 500 Internal Server Error with generic message in JSON:API format.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.error("Unexpected error [requestId={}]: {}", requestId, ex.getMessage(), ex);

        JsonApiError error = new JsonApiError(
            "500", "internalError", "Internal Server Error", "An unexpected error occurred");
        error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("errors", List.of(error)));
    }

    /**
     * Generates a unique request ID for tracing.
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
