package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Represents a collection-to-worker assignment in the EMF platform.
 * Tracks which collections are assigned to which workers and their assignment status.
 */
@Entity
@Table(name = "collection_assignment", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"collection_id", "worker_id"})
})
public class CollectionAssignment extends BaseEntity {

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "worker_id", nullable = false, length = 36)
    private String workerId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "default";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    public CollectionAssignment() {
        super();
    }

    public CollectionAssignment(String collectionId, String workerId, String tenantId) {
        super();
        this.collectionId = collectionId;
        this.workerId = workerId;
        this.tenantId = tenantId;
        this.assignedAt = Instant.now();
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(Instant readyAt) {
        this.readyAt = readyAt;
    }

    @Override
    public String toString() {
        return "CollectionAssignment{" +
                "id='" + getId() + '\'' +
                ", collectionId='" + collectionId + '\'' +
                ", workerId='" + workerId + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
