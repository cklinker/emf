package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a manual record share granting access to a specific user, group, or role.
 * Created when a record owner explicitly shares a record.
 */
@Entity
@Table(name = "record_share")
@EntityListeners(AuditingEntityListener.class)
public class RecordShare {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "record_id", nullable = false, length = 36)
    private String recordId;

    @Column(name = "shared_with_id", nullable = false, length = 36)
    private String sharedWithId;

    @Column(name = "shared_with_type", nullable = false, length = 20)
    private String sharedWithType; // USER, GROUP, ROLE

    @Column(name = "access_level", nullable = false, length = 20)
    private String accessLevel; // READ or READ_WRITE

    @Column(name = "reason", length = 20)
    private String reason = "MANUAL"; // MANUAL, RULE, TEAM, TERRITORY

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RecordShare() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public String getSharedWithId() { return sharedWithId; }
    public void setSharedWithId(String sharedWithId) { this.sharedWithId = sharedWithId; }

    public String getSharedWithType() { return sharedWithType; }
    public void setSharedWithType(String sharedWithType) { this.sharedWithType = sharedWithType; }

    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordShare that = (RecordShare) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
