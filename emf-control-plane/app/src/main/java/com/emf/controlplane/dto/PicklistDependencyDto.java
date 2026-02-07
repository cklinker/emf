package com.emf.controlplane.dto;

import com.emf.controlplane.entity.PicklistDependency;

public class PicklistDependencyDto {

    private String id;
    private String controllingFieldId;
    private String dependentFieldId;
    private String mapping;

    public PicklistDependencyDto() {}

    public static PicklistDependencyDto fromEntity(PicklistDependency entity) {
        if (entity == null) return null;
        PicklistDependencyDto dto = new PicklistDependencyDto();
        dto.id = entity.getId();
        dto.controllingFieldId = entity.getControllingField() != null
                ? entity.getControllingField().getId() : null;
        dto.dependentFieldId = entity.getDependentField() != null
                ? entity.getDependentField().getId() : null;
        dto.mapping = entity.getMapping();
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getControllingFieldId() { return controllingFieldId; }
    public void setControllingFieldId(String controllingFieldId) { this.controllingFieldId = controllingFieldId; }

    public String getDependentFieldId() { return dependentFieldId; }
    public void setDependentFieldId(String dependentFieldId) { this.dependentFieldId = dependentFieldId; }

    public String getMapping() { return mapping; }
    public void setMapping(String mapping) { this.mapping = mapping; }
}
