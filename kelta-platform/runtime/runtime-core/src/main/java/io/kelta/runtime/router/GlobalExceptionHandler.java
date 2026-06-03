package io.kelta.runtime.router;

import io.kelta.jsonapi.JsonApiError;
import io.kelta.runtime.query.InvalidQueryException;
import io.kelta.runtime.storage.StorageException;
import io.kelta.runtime.storage.UniqueConstraintViolationException;
import io.kelta.runtime.validation.RecordValidationException;
import io.kelta.runtime.validation.ValidationException;
import io.kelta.runtime.validation.ValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for the REST API.
 *
 * <p>Maps exceptions to JSON:API error responses with at minimum {@code status},
 * {@code code}, {@code title}, and {@code detail}. Field-level errors carry a
 * {@code source.pointer} into the request body.
 *
 * <p>Codes are stable UPPER_SNAKE_CASE machine identifiers so clients can branch
 * on them without scraping titles or detail strings.
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
                String constraintCode = toCode(fieldError.constraint(), "VALIDATION_FAILED");
                JsonApiError error = new JsonApiError(
                    "400", constraintCode, "Validation Error", fieldError.message());
                error.setSource(Map.of("pointer", "/data/attributes/" + fieldError.fieldName()));
                error.setMeta(Map.of("requestId", requestId));
                errors.add(error);
            }
        } else {
            JsonApiError error = new JsonApiError(
                "400", "VALIDATION_FAILED", "Validation Error",
                fallbackDetail(ex.getMessage(), "Validation failed"));
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
                "400", "VALIDATION_RULE_FAILED", "Validation Error",
                fallbackDetail(ruleError.errorMessage(), "Validation rule '" + ruleError.ruleName() + "' failed"));
            error.setSource(Map.of("pointer", "/data/attributes/" + fieldName));
            error.setMeta(Map.of("requestId", requestId, "rule", ruleError.ruleName()));
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
        String detail = fallbackDetail(ex.getReason(), fallbackDetail(ex.getMessage(), "Invalid query"));
        if (ex.getFieldName() != null) {
            JsonApiError error = new JsonApiError(
                "400", "INVALID_QUERY", "Bad Request", detail);
            error.setSource(Map.of("pointer", "/data/attributes/" + ex.getFieldName()));
            error.setMeta(Map.of("requestId", requestId));
            errors.add(error);
        } else {
            JsonApiError error = new JsonApiError(
                "400", "INVALID_QUERY", "Bad Request", detail);
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

        JsonApiError error = new JsonApiError(
            "409", "UNIQUE_CONSTRAINT_VIOLATION", "Conflict",
            fallbackDetail(ex.getMessage(), "A record with the same value already exists"));
        error.setSource(Map.of("pointer", "/data/attributes/" + ex.getFieldName()));
        error.setMeta(Map.of("requestId", requestId));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles Spring's bean-validation failures on {@code @Valid} request bodies.
     * Returns 400 with one error object per field violation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Bean validation failed [requestId={}]: {}", requestId, ex.getMessage());

        List<JsonApiError> errors = new ArrayList<>();
        for (var fieldError : ex.getBindingResult().getFieldErrors()) {
            JsonApiError error = new JsonApiError(
                "400", "VALIDATION_FAILED", "Validation Error",
                fallbackDetail(fieldError.getDefaultMessage(),
                    "Invalid value for field '" + fieldError.getField() + "'"));
            error.setSource(Map.of("pointer", "/data/attributes/" + fieldError.getField()));
            error.setMeta(Map.of("requestId", requestId));
            errors.add(error);
        }
        if (errors.isEmpty()) {
            JsonApiError error = new JsonApiError(
                "400", "VALIDATION_FAILED", "Validation Error", "Request body failed validation");
            error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));
            errors.add(error);
        }

        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    /**
     * Handles unparseable request bodies (malformed JSON, wrong type, etc.).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Unreadable request body [requestId={}]: {}", requestId, ex.getMessage());

        JsonApiError error = new JsonApiError(
            "400", "INVALID_PAYLOAD", "Bad Request",
            fallbackDetail(ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : null,
                "Request body is missing or malformed"));
        error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));

        return ResponseEntity.badRequest().body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles missing required query parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Missing request parameter [requestId={}]: {}", requestId, ex.getMessage());

        JsonApiError error = new JsonApiError(
            "400", "MISSING_PARAMETER", "Bad Request",
            "Required " + ex.getParameterType() + " parameter '" + ex.getParameterName() + "' is missing");
        error.setSource(Map.of("parameter", ex.getParameterName()));
        error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));

        return ResponseEntity.badRequest().body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles query parameters whose value cannot be coerced to the target type.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Parameter type mismatch [requestId={}]: {}", requestId, ex.getMessage());

        String typeName = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "expected type";
        JsonApiError error = new JsonApiError(
            "400", "INVALID_PARAMETER", "Bad Request",
            "Parameter '" + ex.getName() + "' must be a valid " + typeName);
        error.setSource(Map.of("parameter", ex.getName()));
        error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));

        return ResponseEntity.badRequest().body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles an unsupported HTTP method on a known route. Returns 405 with the
     * allowed methods listed in {@code meta}.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        JsonApiError error = new JsonApiError(
            "405", "METHOD_NOT_ALLOWED", "Method Not Allowed",
            "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint");
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("requestId", requestId);
        meta.put("path", request.getRequestURI());
        if (ex.getSupportedMethods() != null) {
            meta.put("allowedMethods", List.of(ex.getSupportedMethods()));
        }
        error.setMeta(meta);

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles unsupported request {@code Content-Type}.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        JsonApiError error = new JsonApiError(
            "415", "UNSUPPORTED_MEDIA_TYPE", "Unsupported Media Type",
            "Content-Type '" + ex.getContentType() + "' is not supported");
        error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles unmatched routes — both Spring 6 ({@link NoResourceFoundException})
     * and the legacy {@link NoHandlerFoundException}. Returns 404 with the
     * request path so clients can distinguish a wrong path from a wrong payload.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNoHandler(
            Exception ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        JsonApiError error = new JsonApiError(
            "404", "NOT_FOUND", "Not Found",
            "No endpoint matches " + request.getMethod() + " " + request.getRequestURI());
        error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("errors", List.of(error)));
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
            "500", "STORAGE_ERROR", "Internal Server Error",
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
            "500", "INTERNAL_ERROR", "Internal Server Error", "An unexpected error occurred");
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

    /**
     * Normalizes a free-form constraint name (e.g. {@code "minLength"}) to the
     * canonical UPPER_SNAKE_CASE code. Falls back to {@code defaultCode} when
     * the input is null/blank.
     */
    private static String toCode(String constraint, String defaultCode) {
        if (constraint == null || constraint.isBlank()) {
            return defaultCode;
        }
        String snake = constraint
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("[^A-Za-z0-9]+", "_")
            .toUpperCase();
        if (snake.startsWith("_")) snake = snake.substring(1);
        if (snake.endsWith("_")) snake = snake.substring(0, snake.length() - 1);
        return snake.isEmpty() ? defaultCode : snake;
    }

    private static String fallbackDetail(String detail, String fallback) {
        return (detail != null && !detail.isBlank()) ? detail : fallback;
    }
}
