package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating or updating a UI menu item.
 */
public class UiMenuItemRequest {

    private String id;

    @NotBlank(message = "Menu item label is required")
    @Size(min = 1, max = 100, message = "Menu item label must be between 1 and 100 characters")
    private String label;

    @NotBlank(message = "Menu item path is required")
    @Size(min = 1, max = 200, message = "Menu item path must be between 1 and 200 characters")
    @Pattern(regexp = "^/.*", message = "Menu item path must start with /")
    private String path;

    @Size(max = 100, message = "Menu item icon must not exceed 100 characters")
    private String icon;

    private Integer displayOrder;

    private Boolean active;

    public UiMenuItemRequest() {
    }

    public UiMenuItemRequest(String label, String path, Integer displayOrder) {
        this.label = label;
        this.path = path;
        this.displayOrder = displayOrder;
    }

    public UiMenuItemRequest(String id, String label, String path, String icon, Integer displayOrder, Boolean active) {
        this.id = id;
        this.label = label;
        this.path = path;
        this.icon = icon;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "UiMenuItemRequest{" +
                "id='" + id + '\'' +
                ", label='" + label + '\'' +
                ", path='" + path + '\'' +
                ", icon='" + icon + '\'' +
                ", displayOrder=" + displayOrder +
                ", active=" + active +
                '}';
    }
}
