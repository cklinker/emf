package com.emf.gateway.jsonapi;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Represents a JSON:API error object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
