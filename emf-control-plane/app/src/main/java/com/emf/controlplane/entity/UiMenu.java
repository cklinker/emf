package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a UI menu configuration.
 * Menus contain menu items that define navigation structure.
 */
@Entity
@Table(name = "ui_menu")
public class UiMenu extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    private List<UiMenuItem> items = new ArrayList<>();

    public UiMenu() {
        super();
    }

    public UiMenu(String name) {
        super();
        this.name = name;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<UiMenuItem> getItems() {
        return items;
    }

    public void setItems(List<UiMenuItem> items) {
        this.items = items;
    }

    public void addItem(UiMenuItem item) {
        items.add(item);
        item.setMenu(this);
    }

    public void removeItem(UiMenuItem item) {
        items.remove(item);
        item.setMenu(null);
    }

    @Override
    public String toString() {
        return "UiMenu{" +
                "id='" + getId() + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
