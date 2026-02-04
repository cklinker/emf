package com.emf.gateway.jsonapi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a JSON:API document with data, included, meta, and errors fields.
 * This is the top-level structure for JSON:API responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonApiDocument {
    private List<ResourceObject> data;
    private List<ResourceObject> included;
    private Map<String, Object> meta;
    private List<JsonApiError> errors;
    
    // Track whether the original data was a single resource (not an array)
    // This is needed for correct serialization
    @JsonIgnore
    private boolean singleResource = false;

    public JsonApiDocument() {
    }

    public JsonApiDocument(List<ResourceObject> data) {
        this.data = data;
    }

    public List<ResourceObject> getData() {
        return data;
    }

    public void setData(List<ResourceObject> data) {
        this.data = data;
    }

    public List<ResourceObject> getIncluded() {
        return included;
    }

    public void setIncluded(List<ResourceObject> included) {
        this.included = included;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    public List<JsonApiError> getErrors() {
        return errors;
    }

    public void setErrors(List<JsonApiError> errors) {
        this.errors = errors;
    }

    /**
     * Add a resource to the data array.
     */
    public void addData(ResourceObject resource) {
        if (this.data == null) {
            this.data = new ArrayList<>();
        }
        this.data.add(resource);
    }

    /**
     * Add a resource to the included array.
     */
    public void addIncluded(ResourceObject resource) {
        if (this.included == null) {
            this.included = new ArrayList<>();
        }
        this.included.add(resource);
    }

    /**
     * Add an error to the errors array.
     */
    public void addError(JsonApiError error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
    }

    /**
     * Add a meta field.
     */
    public void addMeta(String key, Object value) {
        if (this.meta == null) {
            this.meta = new HashMap<>();
        }
        this.meta.put(key, value);
    }

    /**
     * Check if this document contains errors.
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    /**
     * Check if this document contains data.
     */
    public boolean hasData() {
        return data != null && !data.isEmpty();
    }

    /**
     * Check if this document contains included resources.
     */
    public boolean hasIncluded() {
        return included != null && !included.isEmpty();
    }
    
    /**
     * Check if the original data was a single resource (not an array).
     */
    public boolean isSingleResource() {
        return singleResource;
    }
    
    /**
     * Set whether the original data was a single resource.
     */
    public void setSingleResource(boolean singleResource) {
        this.singleResource = singleResource;
    }

    @Override
    public String toString() {
        return "JsonApiDocument{" +
                "data=" + data +
                ", included=" + included +
                ", meta=" + meta +
                ", errors=" + errors +
                '}';
    }
}
