package io.kelta.worker.scim.controller;

import io.kelta.worker.scim.ScimConstants;
import io.kelta.worker.scim.model.ScimError;
import io.kelta.worker.scim.model.ScimException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.kelta.worker.scim.controller")
public class ScimExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ScimExceptionHandler.class);

    @ExceptionHandler(ScimException.class)
    public ResponseEntity<ScimError> handleScimException(ScimException ex) {
        log.warn("SCIM error: {} - {}", ex.getHttpStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .header("Content-Type", ScimConstants.CONTENT_TYPE_SCIM)
                .body(ex.toScimError());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ScimError> handleGenericException(Exception ex) {
        log.error("Unexpected error in SCIM endpoint", ex);
        return ResponseEntity.status(500)
                .header("Content-Type", ScimConstants.CONTENT_TYPE_SCIM)
                .body(new ScimError("500", "Internal server error"));
    }
}
