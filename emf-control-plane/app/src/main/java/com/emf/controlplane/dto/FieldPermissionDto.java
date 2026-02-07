package com.emf.controlplane.dto;

import com.emf.controlplane.entity.FieldPermission;
import com.emf.controlplane.entity.PermsetFieldPermission;

public class FieldPermissionDto {

    private String id;
    private String fieldId;
    private String visibility;

    public FieldPermissionDto() {}

    public static FieldPermissionDto fromFieldPermission(FieldPermission entity) {
        if (entity == null) return null;
        FieldPermissionDto dto = new FieldPermissionDto();
        dto.id = entity.getId();
        dto.fieldId = entity.getFieldId();
        dto.visibility = entity.getVisibility();
        return dto;
    }

    public static FieldPermissionDto fromPermsetFieldPermission(PermsetFieldPermission entity) {
        if (entity == null) return null;
        FieldPermissionDto dto = new FieldPermissionDto();
        dto.id = entity.getId();
        dto.fieldId = entity.getFieldId();
        dto.visibility = entity.getVisibility();
        return dto;
    }

    // Getters and setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
}
