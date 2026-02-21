package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

/**
 * Object-level (collection-level) CRUD permission for a permission set.
 */
@Entity
@Table(name = "permset_object_permission", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"permission_set_id", "collection_id"})
})
public class PermsetObjectPermission {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "permission_set_id", nullable = false, length = 36)
    private String permissionSetId;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "can_create", nullable = false)
    private boolean canCreate = false;

    @Column(name = "can_read", nullable = false)
    private boolean canRead = false;

    @Column(name = "can_edit", nullable = false)
    private boolean canEdit = false;

    @Column(name = "can_delete", nullable = false)
    private boolean canDelete = false;

    @Column(name = "can_view_all", nullable = false)
    private boolean canViewAll = false;

    @Column(name = "can_modify_all", nullable = false)
    private boolean canModifyAll = false;

    public PermsetObjectPermission() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPermissionSetId() { return permissionSetId; }
    public void setPermissionSetId(String permissionSetId) { this.permissionSetId = permissionSetId; }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermsetObjectPermission that = (PermsetObjectPermission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
