package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A named permission bundle assigned to users (one profile per user per tenant).
 * Profiles define the base set of system, object, and field permissions.
 */
@Entity
@Table(name = "profile", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "name"})
})
public class Profile extends TenantScopedEntity {

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

    public Profile() {
        super();
    }

    public Profile(String tenantId, String name, String description, boolean isSystem) {
        super(tenantId);
        this.name = name;
        this.description = description;
        this.isSystem = isSystem;
    }

    // Getters and setters

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

    @Override
    public String toString() {
        return "Profile{id='" + getId() + "', name='" + name + "', isSystem=" + isSystem + "}";
    }
}
