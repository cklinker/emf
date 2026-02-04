package com.emf.controlplane.dto;

import java.util.List;
import java.util.Objects;

/**
 * DTO representing metadata for a single resource (collection).
 * Contains the collection's schema, available operations, and authorization hints.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>8.1: Return collection with schema</li>
 *   <li>8.2: Include field definitions, types, and constraints</li>
 *   <li>8.3: Include available operations</li>
 *   <li>8.4: Include authorization hints</li>
 * </ul>
 */
public class ResourceMetadataDto {

    private String id;
    private String name;
    private String description;
    private Integer currentVersion;
    private List<FieldMetadataDto> fields;
    private List<String> availableOperations;
    private AuthorizationHintsDto authorizationHints;

    public ResourceMetadataDto() {
    }

    public ResourceMetadataDto(String id, String name, String description, Integer currentVersion,
                               List<FieldMetadataDto> fields, List<String> availableOperations,
                               AuthorizationHintsDto authorizationHints) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.currentVersion = currentVersion;
        this.fields = fields;
        this.availableOperations = availableOperations;
        this.authorizationHints = authorizationHints;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    public List<FieldMetadataDto> getFields() {
        return fields;
    }

    public void setFields(List<FieldMetadataDto> fields) {
        this.fields = fields;
    }

    public List<String> getAvailableOperations() {
        return availableOperations;
    }

    public void setAvailableOperations(List<String> availableOperations) {
        this.availableOperations = availableOperations;
    }

    public AuthorizationHintsDto getAuthorizationHints() {
        return authorizationHints;
    }

    public void setAuthorizationHints(AuthorizationHintsDto authorizationHints) {
        this.authorizationHints = authorizationHints;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceMetadataDto that = (ResourceMetadataDto) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(currentVersion, that.currentVersion) &&
                Objects.equals(fields, that.fields) &&
                Objects.equals(availableOperations, that.availableOperations) &&
                Objects.equals(authorizationHints, that.authorizationHints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, currentVersion, fields, availableOperations, authorizationHints);
    }

    @Override
    public String toString() {
        return "ResourceMetadataDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", currentVersion=" + currentVersion +
                ", fields=" + fields +
                ", availableOperations=" + availableOperations +
                ", authorizationHints=" + authorizationHints +
                '}';
    }
}
