package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

/**
 * Object-level (collection-level) CRUD permission for a profile.
 */
@Entity
@Table(name = "profile_object_permission", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"profile_id", "collection_id"})
})
public class ProfileObjectPermission {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "profile_id", nullable = false, length = 36)
    private String profileId;

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

    public ProfileObjectPermission() {
        this.id = UUID.randomUUID().toString();
    }

    public ProfileObjectPermission(String profileId, String collectionId,
                                   boolean canCreate, boolean canRead, boolean canEdit,
                                   boolean canDelete, boolean canViewAll, boolean canModifyAll) {
        this();
        this.profileId = profileId;
        this.collectionId = collectionId;
        this.canCreate = canCreate;
        this.canRead = canRead;
        this.canEdit = canEdit;
        this.canDelete = canDelete;
        this.canViewAll = canViewAll;
        this.canModifyAll = canModifyAll;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

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
        ProfileObjectPermission that = (ProfileObjectPermission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
