package com.emf.controlplane.config;

import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.runtime.router.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;
import java.util.UUID;

/**
 * Exception handler for control-plane specific exceptions.
 *
 * <p>Handles control-plane exceptions that the runtime GlobalExceptionHandler
 * does not know about (different package/class). Ordered with higher priority
 * so these handlers are checked before the runtime's generic Exception handler.
 *
 * <p>Exception mapping:
 * <ul>
 *   <li>ValidationException → 400 Bad Request</li>
 *   <li>AccessDeniedException → 403 Forbidden</li>
 *   <li>ResourceNotFoundException → 404 Not Found</li>
 *   <li>DuplicateResourceException → 409 Conflict</li>
 * </ul>
 */
@ControllerAdvice
@Order(0) // Higher priority than runtime GlobalExceptionHandler
public class ControlPlaneExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneExceptionHandler.class);

    /**
     * Handles control-plane validation exceptions.
     * Returns 400 Bad Request with field-level error details.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        log.warn("Validation failed [requestId={}]: {}", requestId, ex.getMessage());

        List<ErrorResponse.FieldErrorResponse> fieldErrors = List.of();
        if (ex.getFieldName() != null) {
            fieldErrors = List.of(new ErrorResponse.FieldErrorResponse(
                    ex.getFieldName(), ex.getErrorMessage(), "validation"));
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
     * Handles Spring Security access denied exceptions.
     * Returns 403 Forbidden when a user lacks required roles/authorities.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        log.warn("Access denied [requestId={}]: {} {}", requestId, request.getMethod(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(
                requestId,
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Access denied: insufficient permissions",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handles resource not found exceptions.
     * Returns 404 Not Found.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        log.warn("Resource not found [requestId={}]: {}", requestId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                requestId,
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handles duplicate resource exceptions.
     * Returns 409 Conflict with field-level error details.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResourceException(
            DuplicateResourceException ex, HttpServletRequest request) {

        String requestId = generateRequestId();
        log.warn("Duplicate resource [requestId={}]: {}", requestId, ex.getMessage());

        List<ErrorResponse.FieldErrorResponse> fieldErrors = List.of();
        if (ex.getFieldName() != null) {
            fieldErrors = List.of(new ErrorResponse.FieldErrorResponse(
                    ex.getFieldName(), ex.getMessage(), "duplicate"));
        }

        ErrorResponse response = ErrorResponse.withErrors(
                requestId,
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage(),
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
