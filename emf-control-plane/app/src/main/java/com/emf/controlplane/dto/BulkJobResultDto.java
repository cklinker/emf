package com.emf.controlplane.dto;

import com.emf.controlplane.entity.BulkJobResult;

import java.time.Instant;

public class BulkJobResultDto {

    private String id;
    private String bulkJobId;
    private int recordIndex;
    private String recordId;
    private String status;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public static BulkJobResultDto fromEntity(BulkJobResult entity) {
        BulkJobResultDto dto = new BulkJobResultDto();
        dto.setId(entity.getId());
        dto.setBulkJobId(entity.getBulkJob().getId());
        dto.setRecordIndex(entity.getRecordIndex());
        dto.setRecordId(entity.getRecordId());
        dto.setStatus(entity.getStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBulkJobId() { return bulkJobId; }
    public void setBulkJobId(String bulkJobId) { this.bulkJobId = bulkJobId; }
    public int getRecordIndex() { return recordIndex; }
    public void setRecordIndex(int recordIndex) { this.recordIndex = recordIndex; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
