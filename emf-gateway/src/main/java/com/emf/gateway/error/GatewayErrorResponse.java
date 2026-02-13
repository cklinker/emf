package com.emf.gateway.error;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standard JSON:API error response for API Gateway errors.
 * Serializes as {"errors": [{...}]} per JSON:API specification.
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

    /**
     * Serializes in JSON:API error format: {"errors": [{status, code, title, detail, meta}]}
     */
    @JsonProperty("errors")
    public List<Map<String, Object>> getErrors() {
        Map<String, Object> error = new java.util.LinkedHashMap<>();
        error.put("status", String.valueOf(status));
        error.put("code", code);
        error.put("title", code);
        error.put("detail", message);

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("timestamp", timestamp);
        if (path != null) {
            meta.put("path", path);
        }
        if (correlationId != null) {
            meta.put("correlationId", correlationId);
        }
        error.put("meta", meta);

        return List.of(error);
    }

    // Getters/setters remain for internal use (hidden from JSON serialization)

    @JsonIgnore
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @JsonIgnore
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @JsonIgnore
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @JsonIgnore
    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @JsonIgnore
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @JsonIgnore
    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
