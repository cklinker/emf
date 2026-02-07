package com.emf.controlplane.dto;

import com.emf.controlplane.entity.RecordShare;

import java.time.Instant;

public class RecordShareDto {

    private String id;
    private String collectionId;
    private String recordId;
    private String sharedWithId;
    private String sharedWithType;
    private String accessLevel;
    private String reason;
    private String createdBy;
    private Instant createdAt;

    public RecordShareDto() {}

    public static RecordShareDto fromEntity(RecordShare entity) {
        RecordShareDto dto = new RecordShareDto();
        dto.setId(entity.getId());
        dto.setCollectionId(entity.getCollectionId());
        dto.setRecordId(entity.getRecordId());
        dto.setSharedWithId(entity.getSharedWithId());
        dto.setSharedWithType(entity.getSharedWithType());
        dto.setAccessLevel(entity.getAccessLevel());
        dto.setReason(entity.getReason());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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
}
