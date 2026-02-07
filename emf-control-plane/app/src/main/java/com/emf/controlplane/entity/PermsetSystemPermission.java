package com.emf.controlplane.entity;

import jakarta.persistence.*;

/**
 * Represents system-wide permissions within a permission set.
 */
@Entity
@Table(name = "permset_system_permission")
public class PermsetSystemPermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_set_id", nullable = false)
    private PermissionSet permissionSet;

    @Column(name = "permission_key", nullable = false, length = 100)
    private String permissionKey;

    @Column(name = "granted")
    private boolean granted = false;

    public PermsetSystemPermission() {
        super();
    }

    // Getters and setters

    public PermissionSet getPermissionSet() { return permissionSet; }
    public void setPermissionSet(PermissionSet permissionSet) { this.permissionSet = permissionSet; }

    public String getPermissionKey() { return permissionKey; }
    public void setPermissionKey(String permissionKey) { this.permissionKey = permissionKey; }

    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }
}
