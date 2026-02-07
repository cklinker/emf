package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents a field definition within a collection.
 * A field is a typed attribute with optional constraints.
 */
@Entity
@Table(name = "field")
public class Field extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "required", nullable = false)
    private boolean required = false;

    @Column(name = "unique_constraint", nullable = false)
    private boolean unique = false;

    @Column(name = "indexed", nullable = false)
    private boolean indexed = false;

    @Column(name = "default_value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String defaultValue;

    @Column(name = "reference_target", length = 100)
    private String referenceTarget;

    @Column(name = "field_order", nullable = false)
    private Integer order = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "constraints", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String constraints;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "field_type_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String fieldTypeConfig;

    @Column(name = "auto_number_sequence_name", length = 100)
    private String autoNumberSequenceName;

    @Column(name = "relationship_type", length = 20)
    private String relationshipType;

    @Column(name = "relationship_name", length = 100)
    private String relationshipName;

    @Column(name = "cascade_delete", nullable = false)
    private boolean cascadeDelete = false;

    @Column(name = "reference_collection_id", length = 36)
    private String referenceCollectionId;

    public Field() {
        super();
    }

    public Field(String name, String type) {
        super();
        this.name = name;
        this.displayName = name;
        this.type = type;
    }

    public Collection getCollection() {
        return collection;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public void setIndexed(boolean indexed) {
        this.indexed = indexed;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getReferenceTarget() {
        return referenceTarget;
    }

    public void setReferenceTarget(String referenceTarget) {
        this.referenceTarget = referenceTarget;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getConstraints() {
        return constraints;
    }

    public void setConstraints(String constraints) {
        this.constraints = constraints;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFieldTypeConfig() {
        return fieldTypeConfig;
    }

    public void setFieldTypeConfig(String fieldTypeConfig) {
        this.fieldTypeConfig = fieldTypeConfig;
    }

    public String getAutoNumberSequenceName() {
        return autoNumberSequenceName;
    }

    public void setAutoNumberSequenceName(String autoNumberSequenceName) {
        this.autoNumberSequenceName = autoNumberSequenceName;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public String getRelationshipName() {
        return relationshipName;
    }

    public void setRelationshipName(String relationshipName) {
        this.relationshipName = relationshipName;
    }

    public boolean isCascadeDelete() {
        return cascadeDelete;
    }

    public void setCascadeDelete(boolean cascadeDelete) {
        this.cascadeDelete = cascadeDelete;
    }

    public String getReferenceCollectionId() {
        return referenceCollectionId;
    }

    public void setReferenceCollectionId(String referenceCollectionId) {
        this.referenceCollectionId = referenceCollectionId;
    }

    @Override
    public String toString() {
        return "Field{" +
                "id='" + getId() + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", required=" + required +
                ", active=" + active +
                '}';
    }
}
