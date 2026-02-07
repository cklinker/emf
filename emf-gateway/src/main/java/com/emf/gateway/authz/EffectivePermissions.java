package com.emf.gateway.authz;

import java.util.Map;
import java.util.Set;

/**
 * Represents the effective permissions for a user, fetched from the control plane.
 * Cached in the gateway for quick permission evaluation.
 */
public class EffectivePermissions {

    private String userId;
    private Map<String, ObjectPermissions> objectPermissions;
    private Map<String, String> fieldPermissions; // fieldId -> visibility
    private Set<String> systemPermissions;

    public EffectivePermissions() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Map<String, ObjectPermissions> getObjectPermissions() { return objectPermissions; }
    public void setObjectPermissions(Map<String, ObjectPermissions> objectPermissions) { this.objectPermissions = objectPermissions; }

    public Map<String, String> getFieldPermissions() { return fieldPermissions; }
    public void setFieldPermissions(Map<String, String> fieldPermissions) { this.fieldPermissions = fieldPermissions; }

    public Set<String> getSystemPermissions() { return systemPermissions; }
    public void setSystemPermissions(Set<String> systemPermissions) { this.systemPermissions = systemPermissions; }

    /**
     * Object-level permissions for a collection.
     */
    public static class ObjectPermissions {
        private String collectionId;
        private boolean canCreate;
        private boolean canRead;
        private boolean canEdit;
        private boolean canDelete;
        private boolean canViewAll;
        private boolean canModifyAll;

        public ObjectPermissions() {}

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
}
