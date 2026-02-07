package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an additive permission set that can be assigned to users.
 * Permission sets extend user permissions beyond their profile.
 * Permissions from sets are OR-merged with profile permissions.
 */
@Entity
@Table(name = "permission_set")
public class PermissionSet extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_system")
    private boolean system = false;

    @OneToMany(mappedBy = "permissionSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PermsetObjectPermission> objectPermissions = new ArrayList<>();

    @OneToMany(mappedBy = "permissionSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PermsetFieldPermission> fieldPermissions = new ArrayList<>();

    @OneToMany(mappedBy = "permissionSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PermsetSystemPermission> systemPermissions = new ArrayList<>();

    public PermissionSet() {
        super();
    }

    public PermissionSet(String name, String description) {
        super();
        this.name = name;
        this.description = description;
    }

    // Getters and setters

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isSystem() { return system; }
    public void setSystem(boolean system) { this.system = system; }

    public List<PermsetObjectPermission> getObjectPermissions() { return objectPermissions; }
    public void setObjectPermissions(List<PermsetObjectPermission> objectPermissions) { this.objectPermissions = objectPermissions; }

    public List<PermsetFieldPermission> getFieldPermissions() { return fieldPermissions; }
    public void setFieldPermissions(List<PermsetFieldPermission> fieldPermissions) { this.fieldPermissions = fieldPermissions; }

    public List<PermsetSystemPermission> getSystemPermissions() { return systemPermissions; }
    public void setSystemPermissions(List<PermsetSystemPermission> systemPermissions) { this.systemPermissions = systemPermissions; }

    @Override
    public String toString() {
        return "PermissionSet{" +
                "id='" + getId() + '\'' +
                ", name='" + name + '\'' +
                ", system=" + system +
                '}';
    }
}
