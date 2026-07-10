package io.kelta.jsonapi;

import java.util.Map;

/**
 * Represents a JSON:API error object.
 */
public class JsonApiError {
    private String id;
    private String status;
    private String code;
    private String title;
    private String detail;
    private Map<String, Object> source;
    private Map<String, Object> meta;

    public JsonApiError() {
    }

    public JsonApiError(String status, String code, String title, String detail) {
        this.status = status;
        this.code = code;
        this.title = title;
        this.detail = detail;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Map<String, Object> getSource() {
        return source;
    }

    public void setSource(Map<String, Object> source) {
        this.source = source;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    /**
     * The error as a plain map (nulls omitted), in JSON:API member order.
     *
     * <p>Response bodies are built from these maps rather than the bean itself:
     * plain maps serialize identically under every converter/mapper
     * configuration, whereas bean serialization has been observed to drop all
     * members on the deployed worker ({@code {"errors":[{}]}}), leaving the
     * real failure reason only in the logs.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (id != null) map.put("id", id);
        if (status != null) map.put("status", status);
        if (code != null) map.put("code", code);
        if (title != null) map.put("title", title);
        if (detail != null) map.put("detail", detail);
        if (source != null) map.put("source", source);
        if (meta != null) map.put("meta", meta);
        return map;
    }

    @Override
    public String toString() {
        return "JsonApiError{" +
                "id='" + id + '\'' +
                ", status='" + status + '\'' +
                ", code='" + code + '\'' +
                ", title='" + title + '\'' +
                ", detail='" + detail + '\'' +
                ", source=" + source +
                ", meta=" + meta +
                '}';
    }
}
