package com.emf.controlplane.dto;

import com.emf.controlplane.entity.ObjectPermission;
import com.emf.controlplane.entity.PermsetObjectPermission;

public class ObjectPermissionDto {

    private String id;
    private String collectionId;
    private boolean canCreate;
    private boolean canRead;
    private boolean canEdit;
    private boolean canDelete;
    private boolean canViewAll;
    private boolean canModifyAll;

    public ObjectPermissionDto() {}

    public static ObjectPermissionDto fromEntity(ObjectPermission entity) {
        if (entity == null) return null;
        ObjectPermissionDto dto = new ObjectPermissionDto();
        dto.id = entity.getId();
        dto.collectionId = entity.getCollectionId();
        dto.canCreate = entity.isCanCreate();
        dto.canRead = entity.isCanRead();
        dto.canEdit = entity.isCanEdit();
        dto.canDelete = entity.isCanDelete();
        dto.canViewAll = entity.isCanViewAll();
        dto.canModifyAll = entity.isCanModifyAll();
        return dto;
    }

    public static ObjectPermissionDto fromPermsetEntity(PermsetObjectPermission entity) {
        if (entity == null) return null;
        ObjectPermissionDto dto = new ObjectPermissionDto();
        dto.id = entity.getId();
        dto.collectionId = entity.getCollectionId();
        dto.canCreate = entity.isCanCreate();
        dto.canRead = entity.isCanRead();
        dto.canEdit = entity.isCanEdit();
        dto.canDelete = entity.isCanDelete();
        dto.canViewAll = entity.isCanViewAll();
        dto.canModifyAll = entity.isCanModifyAll();
        return dto;
    }

    // Getters and setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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
