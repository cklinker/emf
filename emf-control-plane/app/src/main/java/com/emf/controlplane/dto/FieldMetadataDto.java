package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Field;

import java.util.Map;
import java.util.Objects;

/**
 * DTO representing metadata for a field within a resource.
 * Contains the field's type, constraints, and authorization hints.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>8.2: Include field definitions, types, and constraints</li>
 *   <li>8.4: Include authorization hints for fields</li>
 * </ul>
 */
public class FieldMetadataDto {

    private String id;
    private String name;
    private String type;
    private boolean required;
    private String description;
    private Map<String, Object> constraints;
    private FieldAuthorizationHintsDto authorizationHints;

    public FieldMetadataDto() {
    }

    public FieldMetadataDto(String id, String name, String type, boolean required, String description,
                            Map<String, Object> constraints, FieldAuthorizationHintsDto authorizationHints) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
        this.constraints = constraints;
        this.authorizationHints = authorizationHints;
    }

    /**
     * Creates a FieldMetadataDto from a Field entity without authorization hints.
     * 
     * @param field The field entity to convert
     * @param constraints Parsed constraints map
     * @return A new FieldMetadataDto with data from the entity
     */
    public static FieldMetadataDto fromEntity(Field field, Map<String, Object> constraints) {
        return fromEntity(field, constraints, null);
    }

    /**
     * Creates a FieldMetadataDto from a Field entity with authorization hints.
     * 
     * @param field The field entity to convert
     * @param constraints Parsed constraints map
     * @param authorizationHints Authorization hints for the field
     * @return A new FieldMetadataDto with data from the entity
     */
    public static FieldMetadataDto fromEntity(Field field, Map<String, Object> constraints,
                                              FieldAuthorizationHintsDto authorizationHints) {
        if (field == null) {
            return null;
        }
        return new FieldMetadataDto(
                field.getId(),
                field.getName(),
                field.getType(),
                field.isRequired(),
                field.getDescription(),
                constraints,
                authorizationHints
        );
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getConstraints() {
        return constraints;
    }

    public void setConstraints(Map<String, Object> constraints) {
        this.constraints = constraints;
    }

    public FieldAuthorizationHintsDto getAuthorizationHints() {
        return authorizationHints;
    }

    public void setAuthorizationHints(FieldAuthorizationHintsDto authorizationHints) {
        this.authorizationHints = authorizationHints;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldMetadataDto that = (FieldMetadataDto) o;
        return required == that.required &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(type, that.type) &&
                Objects.equals(description, that.description) &&
                Objects.equals(constraints, that.constraints) &&
                Objects.equals(authorizationHints, that.authorizationHints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, required, description, constraints, authorizationHints);
    }

    @Override
    public String toString() {
        return "FieldMetadataDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", required=" + required +
                ", description='" + description + '\'' +
                ", constraints=" + constraints +
                ", authorizationHints=" + authorizationHints +
                '}';
    }
}
