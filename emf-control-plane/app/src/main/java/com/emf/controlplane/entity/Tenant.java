package com.emf.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents a tenant — the root organizational boundary for multi-tenant operations.
 * Every resource in the system (users, collections, data, config) belongs to exactly one tenant.
 */
@Entity
@Table(name = "tenant")
public class Tenant extends BaseEntity {

    @Column(name = "slug", nullable = false, unique = true, length = 63)
    private String slug;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "edition", nullable = false, length = 20)
    private String edition = "PROFESSIONAL";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PROVISIONING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private String settings = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limits", columnDefinition = "jsonb")
    private String limits = "{}";

    public Tenant() {
        super();
    }

    public Tenant(String slug, String name) {
        super();
        this.slug = slug;
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSettings() {
        return settings;
    }

    public void setSettings(String settings) {
        this.settings = settings;
    }

    public String getLimits() {
        return limits;
    }

    public void setLimits(String limits) {
        this.limits = limits;
    }

    @Override
    public String toString() {
        return "Tenant{" +
                "id='" + getId() + '\'' +
                ", slug='" + slug + '\'' +
                ", name='" + name + '\'' +
                ", edition='" + edition + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
