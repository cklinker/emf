package com.emf.gateway.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standard error response for API Gateway errors.
 * Provides a consistent error format for authentication, authorization,
 * rate limiting, routing, and internal errors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayErrorResponse {

    private int status;
    private String code;
    private String message;
    private Instant timestamp;
    private String path;
    private String correlationId;

    public GatewayErrorResponse() {
        this.timestamp = Instant.now();
    }

    public GatewayErrorResponse(int status, String code, String message) {
        this();
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public GatewayErrorResponse(int status, String code, String message, String path) {
        this(status, code, message);
        this.path = path;
    }

    public GatewayErrorResponse(int status, String code, String message, String path, String correlationId) {
        this(status, code, message, path);
        this.correlationId = correlationId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
