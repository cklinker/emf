package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a snapshot of a field's definition at a specific collection version.
 * Used for tracking field changes over time.
 */
@Entity
@Table(name = "field_version")
@EntityListeners(AuditingEntityListener.class)
public class FieldVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "collection_version_id", nullable = false)
    private CollectionVersion collectionVersion;

    @Column(name = "field_id", nullable = false, length = 36)
    private String fieldId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "constraints", columnDefinition = "jsonb")
    private String constraints;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public FieldVersion() {
        this.id = UUID.randomUUID().toString();
    }

    public FieldVersion(CollectionVersion collectionVersion, Field field) {
        this();
        this.collectionVersion = collectionVersion;
        this.fieldId = field.getId();
        this.name = field.getName();
        this.type = field.getType();
        this.required = field.isRequired();
        this.active = field.isActive();
        this.constraints = field.getConstraints();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CollectionVersion getCollectionVersion() {
        return collectionVersion;
    }

    public void setCollectionVersion(CollectionVersion collectionVersion) {
        this.collectionVersion = collectionVersion;
    }

    public String getFieldId() {
        return fieldId;
    }

    public void setFieldId(String fieldId) {
        this.fieldId = fieldId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldVersion that = (FieldVersion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FieldVersion{" +
                "id='" + id + '\'' +
                ", fieldId='" + fieldId + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
