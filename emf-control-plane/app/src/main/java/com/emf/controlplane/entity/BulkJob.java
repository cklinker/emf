package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bulk_job")
public class BulkJob extends TenantScopedEntity {

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "operation", nullable = false, length = 30)
    private String operation;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "QUEUED";

    @Column(name = "total_records")
    private int totalRecords;

    @Column(name = "processed_records")
    private int processedRecords;

    @Column(name = "success_records")
    private int successRecords;

    @Column(name = "error_records")
    private int errorRecords;

    @Column(name = "external_id_field", length = 200)
    private String externalIdField;

    @Column(name = "content_type", length = 100)
    private String contentType = "application/json";

    @Column(name = "batch_size")
    private int batchSize = 200;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "bulkJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("recordIndex ASC")
    private List<BulkJobResult> results = new ArrayList<>();

    public BulkJob() { super(); }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
    public int getProcessedRecords() { return processedRecords; }
    public void setProcessedRecords(int processedRecords) { this.processedRecords = processedRecords; }
    public int getSuccessRecords() { return successRecords; }
    public void setSuccessRecords(int successRecords) { this.successRecords = successRecords; }
    public int getErrorRecords() { return errorRecords; }
    public void setErrorRecords(int errorRecords) { this.errorRecords = errorRecords; }
    public String getExternalIdField() { return externalIdField; }
    public void setExternalIdField(String externalIdField) { this.externalIdField = externalIdField; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public List<BulkJobResult> getResults() { return results; }
    public void setResults(List<BulkJobResult> results) { this.results = results; }
}
