package com.emf.controlplane.entity;

import jakarta.persistence.*;

/**
 * Represents field-level visibility permissions within a permission set.
 */
@Entity
@Table(name = "permset_field_permission")
public class PermsetFieldPermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_set_id", nullable = false)
    private PermissionSet permissionSet;

    @Column(name = "field_id", nullable = false, length = 36)
    private String fieldId;

    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility = "VISIBLE";

    public PermsetFieldPermission() {
        super();
    }

    // Getters and setters

    public PermissionSet getPermissionSet() { return permissionSet; }
    public void setPermissionSet(PermissionSet permissionSet) { this.permissionSet = permissionSet; }

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
}
