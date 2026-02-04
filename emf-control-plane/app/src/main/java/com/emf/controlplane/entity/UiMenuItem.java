package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a menu item within a UI menu.
 * Menu items define navigation links with labels and paths.
 */
@Entity
@Table(name = "ui_menu_item")
@EntityListeners(AuditingEntityListener.class)
public class UiMenuItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "menu_id", nullable = false)
    private UiMenu menu;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "path", nullable = false, length = 200)
    private String path;

    @Column(name = "icon", length = 100)
    private String icon;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UiMenuItem() {
        this.id = UUID.randomUUID().toString();
    }

    public UiMenuItem(String label, String path, Integer displayOrder) {
        this();
        this.label = label;
        this.path = path;
        this.displayOrder = displayOrder;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UiMenu getMenu() {
        return menu;
    }

    public void setMenu(UiMenu menu) {
        this.menu = menu;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UiMenuItem that = (UiMenuItem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UiMenuItem{" +
                "id='" + id + '\'' +
                ", label='" + label + '\'' +
                ", path='" + path + '\'' +
                ", displayOrder=" + displayOrder +
                '}';
    }
}
