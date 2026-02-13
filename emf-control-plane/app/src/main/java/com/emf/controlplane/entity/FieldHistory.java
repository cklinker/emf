package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Tracks changes to individual field values on records.
 * Each entry represents a single field change with old and new values.
 */
@Entity
@Table(name = "field_history")
public class FieldHistory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "record_id", nullable = false, length = 36)
    private String recordId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

    @Column(name = "changed_by", nullable = false, length = 36)
    private String changedBy;

    /**
     * Explicit change timestamp. Falls back to createdAt (set automatically by
     * BaseEntity) when not set, avoiding redundant Instant.now() calls.
     */
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "change_source", nullable = false, length = 20)
    private String changeSource = "UI";

    public FieldHistory() {
        super();
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public Instant getChangedAt() {
        return changedAt != null ? changedAt : getCreatedAt();
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getChangeSource() {
        return changeSource;
    }

    public void setChangeSource(String changeSource) {
        this.changeSource = changeSource;
    }

    @Override
    public String toString() {
        return "FieldHistory{" +
                "id='" + getId() + '\'' +
                ", collectionId='" + collectionId + '\'' +
                ", recordId='" + recordId + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", changedBy='" + changedBy + '\'' +
                ", changedAt=" + changedAt +
                '}';
    }
}
