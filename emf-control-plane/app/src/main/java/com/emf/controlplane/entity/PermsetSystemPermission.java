package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

/**
 * A system permission grant for a permission set.
 */
@Entity
@Table(name = "permset_system_permission", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"permission_set_id", "permission_name"})
})
public class PermsetSystemPermission {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "permission_set_id", nullable = false, length = 36)
    private String permissionSetId;

    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName;

    @Column(name = "granted", nullable = false)
    private boolean granted = false;

    public PermsetSystemPermission() {
        this.id = UUID.randomUUID().toString();
    }

    public PermsetSystemPermission(String permissionSetId, String permissionName, boolean granted) {
        this();
        this.permissionSetId = permissionSetId;
        this.permissionName = permissionName;
        this.granted = granted;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPermissionSetId() { return permissionSetId; }
    public void setPermissionSetId(String permissionSetId) { this.permissionSetId = permissionSetId; }

    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }

    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermsetSystemPermission that = (PermsetSystemPermission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
