package com.emf.controlplane.dto;

/**
 * DTO representing resolved object-level permissions for a user on a collection.
 */
public record ObjectPermissions(
        boolean canCreate,
        boolean canRead,
        boolean canEdit,
        boolean canDelete,
        boolean canViewAll,
        boolean canModifyAll
) {

    /** No access at all. */
    public static ObjectPermissions none() {
        return new ObjectPermissions(false, false, false, false, false, false);
    }

    /** Full access. */
    public static ObjectPermissions full() {
        return new ObjectPermissions(true, true, true, true, true, true);
    }

    /**
     * Merges two ObjectPermissions using most-permissive-wins logic.
     */
    public ObjectPermissions merge(ObjectPermissions other) {
        return new ObjectPermissions(
                this.canCreate || other.canCreate,
                this.canRead || other.canRead,
                this.canEdit || other.canEdit,
                this.canDelete || other.canDelete,
                this.canViewAll || other.canViewAll,
                this.canModifyAll || other.canModifyAll
        );
    }
}
