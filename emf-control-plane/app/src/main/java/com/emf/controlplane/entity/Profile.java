package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a permission profile defining what a user can do.
 * Each user is assigned exactly one profile, which provides base-level permissions.
 */
@Entity
@Table(name = "profile")
public class Profile extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_system")
    private boolean system = false;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ObjectPermission> objectPermissions = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FieldPermission> fieldPermissions = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SystemPermission> systemPermissions = new ArrayList<>();

    public Profile() {
        super();
    }

    public Profile(String name, String description) {
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

    public List<ObjectPermission> getObjectPermissions() { return objectPermissions; }
    public void setObjectPermissions(List<ObjectPermission> objectPermissions) { this.objectPermissions = objectPermissions; }

    public List<FieldPermission> getFieldPermissions() { return fieldPermissions; }
    public void setFieldPermissions(List<FieldPermission> fieldPermissions) { this.fieldPermissions = fieldPermissions; }

    public List<SystemPermission> getSystemPermissions() { return systemPermissions; }
    public void setSystemPermissions(List<SystemPermission> systemPermissions) { this.systemPermissions = systemPermissions; }

    @Override
    public String toString() {
        return "Profile{" +
                "id='" + getId() + '\'' +
                ", name='" + name + '\'' +
                ", system=" + system +
                '}';
    }
}
