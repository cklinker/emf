package com.emf.controlplane.dto;

public class ObjectPermissionRequest {

    private boolean canCreate;
    private boolean canRead;
    private boolean canEdit;
    private boolean canDelete;
    private boolean canViewAll;
    private boolean canModifyAll;

    public ObjectPermissionRequest() {}

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
