package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PicklistValueRequest {

    @NotBlank(message = "Value is required")
    @Size(max = 255, message = "Value must not exceed 255 characters")
    private String value;

    @NotBlank(message = "Label is required")
    @Size(max = 255, message = "Label must not exceed 255 characters")
    private String label;

    private boolean isDefault = false;
    private boolean active = true;
    private Integer sortOrder = 0;

    @Size(max = 20, message = "Color must not exceed 20 characters")
    private String color;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    public PicklistValueRequest() {}

    public PicklistValueRequest(String value, String label) {
        this.value = value;
        this.label = label;
    }

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
