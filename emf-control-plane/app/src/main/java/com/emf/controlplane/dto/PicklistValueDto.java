package com.emf.controlplane.dto;

import com.emf.controlplane.entity.PicklistValue;

public class PicklistValueDto {

    private String id;
    private String value;
    private String label;
    private boolean isDefault;
    private boolean active;
    private Integer sortOrder;
    private String color;
    private String description;

    public PicklistValueDto() {}

    public static PicklistValueDto fromEntity(PicklistValue entity) {
        if (entity == null) return null;
        PicklistValueDto dto = new PicklistValueDto();
        dto.id = entity.getId();
        dto.value = entity.getValue();
        dto.label = entity.getLabel();
        dto.isDefault = entity.isDefault();
        dto.active = entity.isActive();
        dto.sortOrder = entity.getSortOrder();
        dto.color = entity.getColor();
        dto.description = entity.getDescription();
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
