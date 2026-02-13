package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents a UI page configuration.
 * Pages define the structure and behavior of admin interface screens.
 */
@Entity
@Table(name = "ui_page")
public class UiPage extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "path", nullable = false, length = 200)
    private String path;

    @Column(name = "title", length = 200)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private String config;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public UiPage() {
        super();
    }

    public UiPage(String name, String path) {
        super();
        this.name = name;
        this.path = path;
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "UiPage{" +
                "id='" + getId() + '\'' +
                ", name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", active=" + active +
                '}';
    }
}
