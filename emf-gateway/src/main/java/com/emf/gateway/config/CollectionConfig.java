package com.emf.gateway.config;

import java.util.List;

/**
 * Configuration for a collection (JSON:API resource type).
 *
 * A collection represents a resource type that is exposed through the gateway,
 * with associated fields and routing information.
 */
public class CollectionConfig {

    private String id;
    private String name;
    private String path;
    private String workerBaseUrl;
    private List<FieldConfig> fields;
    private boolean systemCollection;

    public CollectionConfig() {
    }

    public CollectionConfig(String id, String name, String path, List<FieldConfig> fields) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.fields = fields;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getWorkerBaseUrl() {
        return workerBaseUrl;
    }

    public void setWorkerBaseUrl(String workerBaseUrl) {
        this.workerBaseUrl = workerBaseUrl;
    }

    public List<FieldConfig> getFields() {
        return fields;
    }

    public void setFields(List<FieldConfig> fields) {
        this.fields = fields;
    }

    public boolean isSystemCollection() {
        return systemCollection;
    }

    public void setSystemCollection(boolean systemCollection) {
        this.systemCollection = systemCollection;
    }

    @Override
    public String toString() {
        return "CollectionConfig{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", path='" + path + '\'' +
               ", fields=" + (fields != null ? fields.size() : 0) + " fields" +
               '}';
    }
}
