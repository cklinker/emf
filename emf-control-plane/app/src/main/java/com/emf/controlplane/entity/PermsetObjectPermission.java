package com.emf.controlplane.entity;

import jakarta.persistence.*;

/**
 * Represents collection-level CRUD permissions within a permission set.
 */
@Entity
@Table(name = "permset_object_permission")
public class PermsetObjectPermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_set_id", nullable = false)
    private PermissionSet permissionSet;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "can_create")
    private boolean canCreate;

    @Column(name = "can_read")
    private boolean canRead;

    @Column(name = "can_edit")
    private boolean canEdit;

    @Column(name = "can_delete")
    private boolean canDelete;

    @Column(name = "can_view_all")
    private boolean canViewAll;

    @Column(name = "can_modify_all")
    private boolean canModifyAll;

    public PermsetObjectPermission() {
        super();
    }

    // Getters and setters

    public PermissionSet getPermissionSet() { return permissionSet; }
    public void setPermissionSet(PermissionSet permissionSet) { this.permissionSet = permissionSet; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public boolean isCanCreate() { return canCreate; }
    public void setCanCreate(boolean canCreate) { this.canCreate = canCreate; }

    public boolean isCanRead() { return canRead; }
    public void setCanRead(boolean canRead) { this.canRead = canRead; }

    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }

    public boolean isCanDelete() { return canDelete; }
    public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }

    public boolean isCanViewAll() { return canViewAll; }
    public void setCanViewAll(boolean canViewAll) { this.canViewAll = canViewAll; }

    public boolean isCanModifyAll() { return canModifyAll; }
    public void setCanModifyAll(boolean canModifyAll) { this.canModifyAll = canModifyAll; }
}
