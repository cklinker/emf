package com.emf.controlplane.dto;

import com.emf.controlplane.entity.BulkJob;

import java.time.Instant;

public class BulkJobDto {

    private String id;
    private String tenantId;
    private String collectionId;
    private String operation;
    private String status;
    private int totalRecords;
    private int processedRecords;
    private int successRecords;
    private int errorRecords;
    private String externalIdField;
    private String contentType;
    private int batchSize;
    private String createdBy;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static BulkJobDto fromEntity(BulkJob entity) {
        BulkJobDto dto = new BulkJobDto();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setCollectionId(entity.getCollectionId());
        dto.setOperation(entity.getOperation());
        dto.setStatus(entity.getStatus());
        dto.setTotalRecords(entity.getTotalRecords());
        dto.setProcessedRecords(entity.getProcessedRecords());
        dto.setSuccessRecords(entity.getSuccessRecords());
        dto.setErrorRecords(entity.getErrorRecords());
        dto.setExternalIdField(entity.getExternalIdField());
        dto.setContentType(entity.getContentType());
        dto.setBatchSize(entity.getBatchSize());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
