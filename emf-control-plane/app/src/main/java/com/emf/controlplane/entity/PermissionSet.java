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
 * Additional permission bundles that can be assigned to users or groups (many-to-many).
 * Permission sets are additive — they grant additional access on top of the user's profile.
 */
@Entity
@Table(name = "permission_set", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "name"})
})
@EntityListeners(AuditingEntityListener.class)
public class PermissionSet {

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

    @OneToMany(mappedBy = "permissionSetId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PermsetSystemPermission> systemPermissions = new ArrayList<>();

    @OneToMany(mappedBy = "permissionSetId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PermsetObjectPermission> objectPermissions = new ArrayList<>();

    @OneToMany(mappedBy = "permissionSetId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PermsetFieldPermission> fieldPermissions = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public PermissionSet() {
        this.id = UUID.randomUUID().toString();
    }

    public PermissionSet(String tenantId, String name, String description) {
        this();
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
    }

    public PermissionSet(String tenantId, String name, String description, boolean isSystem) {
        this(tenantId, name, description);
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

    public List<PermsetSystemPermission> getSystemPermissions() { return systemPermissions; }
    public void setSystemPermissions(List<PermsetSystemPermission> systemPermissions) { this.systemPermissions = systemPermissions; }

    public List<PermsetObjectPermission> getObjectPermissions() { return objectPermissions; }
    public void setObjectPermissions(List<PermsetObjectPermission> objectPermissions) { this.objectPermissions = objectPermissions; }

    public List<PermsetFieldPermission> getFieldPermissions() { return fieldPermissions; }
    public void setFieldPermissions(List<PermsetFieldPermission> fieldPermissions) { this.fieldPermissions = fieldPermissions; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermissionSet that = (PermissionSet) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PermissionSet{id='" + id + "', name='" + name + "'}";
    }
}
