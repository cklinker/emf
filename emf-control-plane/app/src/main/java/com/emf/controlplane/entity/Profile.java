package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A named permission bundle assigned to users (one profile per user per tenant).
 * Profiles define the base set of system, object, and field permissions.
 */
@Entity
@Table(name = "profile", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "name"})
})
@EntityListeners(AuditingEntityListener.class)
public class Profile {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false;

    @OneToMany(mappedBy = "profileId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProfileSystemPermission> systemPermissions = new ArrayList<>();

    @OneToMany(mappedBy = "profileId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProfileObjectPermission> objectPermissions = new ArrayList<>();

    @OneToMany(mappedBy = "profileId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProfileFieldPermission> fieldPermissions = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Profile() {
        this.id = UUID.randomUUID().toString();
    }

    public Profile(String tenantId, String name, String description, boolean isSystem) {
        this();
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.isSystem = isSystem;
    }

    // Getters and setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isSystem() { return isSystem; }
    public void setSystem(boolean system) { isSystem = system; }

    public List<ProfileSystemPermission> getSystemPermissions() { return systemPermissions; }
    public void setSystemPermissions(List<ProfileSystemPermission> systemPermissions) { this.systemPermissions = systemPermissions; }

    public List<ProfileObjectPermission> getObjectPermissions() { return objectPermissions; }
    public void setObjectPermissions(List<ProfileObjectPermission> objectPermissions) { this.objectPermissions = objectPermissions; }

    public List<ProfileFieldPermission> getFieldPermissions() { return fieldPermissions; }
    public void setFieldPermissions(List<ProfileFieldPermission> fieldPermissions) { this.fieldPermissions = fieldPermissions; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Profile profile = (Profile) o;
        return Objects.equals(id, profile.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Profile{id='" + id + "', name='" + name + "', isSystem=" + isSystem + "}";
    }
}
