package io.kelta.runtime.router;

import io.kelta.jsonapi.JsonApiError;
import io.kelta.runtime.query.InvalidQueryException;
import io.kelta.runtime.storage.StorageException;
import io.kelta.runtime.storage.UniqueConstraintViolationException;
import io.kelta.runtime.validation.RecordValidationException;
import io.kelta.runtime.validation.ValidationException;
import io.kelta.runtime.validation.ValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
     * Handles Spring {@code @Valid} bean-validation failures, emitting one JSON:API
     * error per field with a {@code source.pointer}.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Request validation failed [requestId={}]: {}", requestId, ex.getMessage());

        List<JsonApiError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            JsonApiError error = new JsonApiError(
                "400", "VALIDATION_FAILED", "Validation Error",
                fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Field is invalid");
            error.setSource(Map.of("pointer", "/data/attributes/" + fe.getField()));
            error.setMeta(Map.of("requestId", requestId));
            errors.add(error);
        });
        ex.getBindingResult().getGlobalErrors().forEach(ge -> {
            JsonApiError error = new JsonApiError(
                "400", "VALIDATION_FAILED", "Validation Error",
                ge.getDefaultMessage() != null ? ge.getDefaultMessage() : "Request is invalid");
            error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));
            errors.add(error);
        });
        if (errors.isEmpty()) {
            JsonApiError error = new JsonApiError(
                "400", "VALIDATION_FAILED", "Validation Error", "Request is invalid");
            error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));
            errors.add(error);
        }

        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    /**
     * Handles Jakarta Bean Validation constraint violations on query/path parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Constraint violation [requestId={}]: {}", requestId, ex.getMessage());

        List<JsonApiError> errors = new ArrayList<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            JsonApiError error = new JsonApiError(
                "400", "VALIDATION_FAILED", "Validation Error",
                cv.getMessage() != null ? cv.getMessage() : "Constraint violation");
            String path = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : "";
            if (!path.isBlank()) {
                error.setSource(Map.of("parameter", path));
            }
            error.setMeta(Map.of("requestId", requestId));
            errors.add(error);
        }
        if (errors.isEmpty()) {
            JsonApiError error = new JsonApiError(
                "400", "VALIDATION_FAILED", "Validation Error",
                ex.getMessage() != null ? ex.getMessage() : "Request is invalid");
            error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));
            errors.add(error);
        }

        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    /**
     * Handles malformed/unreadable request bodies (e.g. invalid JSON).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Unreadable request body [requestId={}]: {}", requestId, ex.getMessage());

        JsonApiError error = new JsonApiError(
            "400", "INVALID_PAYLOAD", "Bad Request",
            "Request body is missing or not parseable as JSON");
        error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));

        return ResponseEntity.badRequest().body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles a missing required servlet request parameter.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Missing request parameter [requestId={}]: {}", requestId, ex.getMessage());

        JsonApiError error = new JsonApiError(
            "400", "MISSING_PARAMETER", "Bad Request",
            "Required parameter '" + ex.getParameterName() + "' is missing");
        error.setSource(Map.of("parameter", ex.getParameterName()));
        error.setMeta(Map.of("requestId", requestId));

        return ResponseEntity.badRequest().body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles a missing required request header (e.g. {@code X-Tenant-ID}).
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Missing request header [requestId={}]: {}", requestId, ex.getMessage());

        JsonApiError error = new JsonApiError(
            "400", "MISSING_HEADER", "Bad Request",
            "Required header '" + ex.getHeaderName() + "' is missing");
        error.setSource(Map.of("header", ex.getHeaderName()));
        error.setMeta(Map.of("requestId", requestId));

        return ResponseEntity.badRequest().body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles a request parameter that cannot be coerced to the declared type.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Type mismatch on parameter [requestId={}]: {}", requestId, ex.getMessage());

        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "expected type";
        JsonApiError error = new JsonApiError(
            "400", "INVALID_PARAMETER", "Bad Request",
            "Parameter '" + ex.getName() + "' could not be converted to " + expectedType);
        error.setSource(Map.of("parameter", ex.getName()));
        error.setMeta(Map.of("requestId", requestId));

        return ResponseEntity.badRequest().body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles 404 for unmapped routes when
     * {@code spring.mvc.throw-exception-if-no-handler-found} is enabled.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(
            NoHandlerFoundException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("No handler for {} {} [requestId={}]",
                ex.getHttpMethod(), ex.getRequestURL(), requestId);

        JsonApiError error = new JsonApiError(
            "404", "NOT_FOUND", "Not Found",
            "No handler for " + ex.getHttpMethod() + " " + ex.getRequestURL());
        error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles 405 when the HTTP method is not supported on a route.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Method not allowed [requestId={}]: {}", requestId, ex.getMessage());

        JsonApiError error = new JsonApiError(
            "405", "METHOD_NOT_ALLOWED", "Method Not Allowed",
            "HTTP method '" + ex.getMethod() + "' is not supported on this endpoint");
        error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles 415 when the request Content-Type is not supported.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        logger.warn("Unsupported media type [requestId={}]: {}", requestId, ex.getMessage());

        JsonApiError error = new JsonApiError(
            "415", "UNSUPPORTED_MEDIA_TYPE", "Unsupported Media Type",
            ex.getContentType() != null
                ? "Content-Type '" + ex.getContentType() + "' is not supported"
                : "Request Content-Type is not supported");
        error.setMeta(Map.of("requestId", requestId, "path", request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(Map.of("errors", List.of(error)));
    }

    /**
     * Handles Spring's {@link ResponseStatusException} — used by controllers that throw
     * an explicit HTTP status. Preserves the original status and reason.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (status.is5xxServerError()) {
            logger.error("Response status {} [requestId={}]: {}",
                    status.value(), requestId, ex.getReason(), ex);
        } else {
            logger.warn("Response status {} [requestId={}]: {}",
                    status.value(), requestId, ex.getReason());
        }

        String code = status.name().replace(' ', '_');
        String detail = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        JsonApiError error = new JsonApiError(
            String.valueOf(status.value()), code, status.getReasonPhrase(), detail);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", requestId);
        meta.put("path", request.getRequestURI());
        error.setMeta(meta);

        return ResponseEntity.status(status).body(Map.of("errors", List.of(error)));
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
}
