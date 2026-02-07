package com.emf.controlplane.dto;

import com.emf.controlplane.entity.FieldHistory;

import java.time.Instant;

/**
 * Response DTO for field history entries.
 */
public class FieldHistoryDto {

    private String id;
    private String collectionId;
    private String recordId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String changedBy;
    private Instant changedAt;
    private String changeSource;

    public FieldHistoryDto() {
    }

    public static FieldHistoryDto fromEntity(FieldHistory entity) {
        if (entity == null) {
            return null;
        }
        FieldHistoryDto dto = new FieldHistoryDto();
        dto.setId(entity.getId());
        dto.setCollectionId(entity.getCollectionId());
        dto.setRecordId(entity.getRecordId());
        dto.setFieldName(entity.getFieldName());
        dto.setOldValue(entity.getOldValue());
        dto.setNewValue(entity.getNewValue());
        dto.setChangedBy(entity.getChangedBy());
        dto.setChangedAt(entity.getChangedAt());
        dto.setChangeSource(entity.getChangeSource());
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
        return changedAt;
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
}
