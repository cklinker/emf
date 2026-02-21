package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Additional permission bundles that can be assigned to users or groups (many-to-many).
 * Permission sets are additive — they grant additional access on top of the user's profile.
 */
@Entity
@Table(name = "permission_set", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "name"})
})
public class PermissionSet extends TenantScopedEntity {

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

    public PermissionSet() {
        super();
    }

    public PermissionSet(String tenantId, String name, String description) {
        super(tenantId);
        this.name = name;
        this.description = description;
    }

    public PermissionSet(String tenantId, String name, String description, boolean isSystem) {
        this(tenantId, name, description);
        this.isSystem = isSystem;
    }

    // Getters and setters

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

    @Override
    public String toString() {
        return "PermissionSet{id='" + getId() + "', name='" + name + "'}";
    }
}
