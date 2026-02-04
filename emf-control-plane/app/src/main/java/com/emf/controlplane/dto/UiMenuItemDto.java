package com.emf.controlplane.dto;

import com.emf.controlplane.entity.UiMenuItem;

import java.time.Instant;
import java.util.Objects;

/**
 * Response DTO for UI Menu Item API responses.
 * Provides a clean API representation of a UiMenuItem entity.
 */
public class UiMenuItemDto {

    private String id;
    private String label;
    private String path;
    private String icon;
    private Integer displayOrder;
    private boolean active;
    private Instant createdAt;

    public UiMenuItemDto() {
    }

    public UiMenuItemDto(String id, String label, String path, String icon,
                         Integer displayOrder, boolean active, Instant createdAt) {
        this.id = id;
        this.label = label;
        this.path = path;
        this.icon = icon;
        this.displayOrder = displayOrder;
        this.active = active;
        this.createdAt = createdAt;
    }

    /**
     * Creates a UiMenuItemDto from a UiMenuItem entity.
     *
     * @param item The UI menu item entity to convert
     * @return A new UiMenuItemDto with data from the entity
     */
    public static UiMenuItemDto fromEntity(UiMenuItem item) {
        if (item == null) {
            return null;
        }
        return new UiMenuItemDto(
                item.getId(),
                item.getLabel(),
                item.getPath(),
                item.getIcon(),
                item.getDisplayOrder(),
                item.isActive(),
                item.getCreatedAt()
        );
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "UiMenuItemDto{" +
                "id='" + id + '\'' +
                ", label='" + label + '\'' +
                ", path='" + path + '\'' +
                ", icon='" + icon + '\'' +
                ", displayOrder=" + displayOrder +
                ", active=" + active +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UiMenuItemDto that = (UiMenuItemDto) o;
        return active == that.active &&
                Objects.equals(id, that.id) &&
                Objects.equals(label, that.label) &&
                Objects.equals(path, that.path) &&
                Objects.equals(icon, that.icon) &&
                Objects.equals(displayOrder, that.displayOrder) &&
                Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label, path, icon, displayOrder, active, createdAt);
    }
}
