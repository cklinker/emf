package com.emf.controlplane.entity;

import jakarta.persistence.*;

/**
 * Text note attached to a record for collaboration and annotation.
 *
 * <p>Notes are scoped to a tenant and linked to a specific record
 * within a collection. The {@code recordId} references a record in
 * a dynamic runtime collection (no FK constraint).
 *
 * @since 1.0.0
 */
@Entity
@Table(name = "note")
public class Note extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "record_id", nullable = false, length = 36)
    private String recordId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_by", nullable = false, length = 320)
    private String createdBy;

    public Note() {
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String toString() {
        return "Note{" +
                "id='" + getId() + '\'' +
                ", collectionId='" + collectionId + '\'' +
                ", recordId='" + recordId + '\'' +
                ", createdBy='" + createdBy + '\'' +
                ", createdAt=" + getCreatedAt() +
                '}';
    }
}
