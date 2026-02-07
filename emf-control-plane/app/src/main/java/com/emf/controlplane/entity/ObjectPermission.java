package com.emf.controlplane.entity;

import jakarta.persistence.*;

/**
 * Represents collection-level CRUD permissions assigned to a profile.
 * Controls what operations a user can perform on a specific collection.
 */
@Entity
@Table(name = "object_permission")
public class ObjectPermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

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

    public ObjectPermission() {
        super();
    }

    // Getters and setters

    public Profile getProfile() { return profile; }
    public void setProfile(Profile profile) { this.profile = profile; }

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
