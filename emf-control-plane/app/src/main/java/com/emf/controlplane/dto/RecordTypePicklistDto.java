package com.emf.controlplane.dto;

import com.emf.controlplane.entity.RecordTypePicklist;

public class RecordTypePicklistDto {

    private String id;
    private String fieldId;
    private String fieldName;
    private String availableValues;
    private String defaultValue;

    public RecordTypePicklistDto() {}

    public static RecordTypePicklistDto fromEntity(RecordTypePicklist entity) {
        if (entity == null) return null;
        RecordTypePicklistDto dto = new RecordTypePicklistDto();
        dto.id = entity.getId();
        dto.fieldId = entity.getField().getId();
        dto.fieldName = entity.getField().getName();
        dto.availableValues = entity.getAvailableValues();
        dto.defaultValue = entity.getDefaultValue();
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getAvailableValues() { return availableValues; }
    public void setAvailableValues(String availableValues) { this.availableValues = availableValues; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
}
