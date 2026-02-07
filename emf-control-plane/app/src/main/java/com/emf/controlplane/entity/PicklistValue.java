package com.emf.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "picklist_value")
public class PicklistValue extends BaseEntity {

    @Column(name = "picklist_source_type", nullable = false, length = 20)
    private String picklistSourceType;

    @Column(name = "picklist_source_id", nullable = false, length = 36)
    private String picklistSourceId;

    @Column(name = "value", nullable = false, length = 255)
    private String value;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Column(name = "is_default")
    private boolean isDefault = false;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "description", length = 500)
    private String description;

    public PicklistValue() {
        super();
    }

    public PicklistValue(String sourceType, String sourceId, String value, String label) {
        super();
        this.picklistSourceType = sourceType;
        this.picklistSourceId = sourceId;
        this.value = value;
        this.label = label;
    }

    public String getPicklistSourceType() { return picklistSourceType; }
    public void setPicklistSourceType(String picklistSourceType) { this.picklistSourceType = picklistSourceType; }

    public String getPicklistSourceId() { return picklistSourceId; }
    public void setPicklistSourceId(String picklistSourceId) { this.picklistSourceId = picklistSourceId; }

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
