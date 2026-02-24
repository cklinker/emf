package com.emf.gateway.authz;

/**
 * Object-level CRUD permissions for a specific collection.
 *
 * <p>Each boolean flag controls whether the user can perform the corresponding
 * operation on records in the collection. {@code canViewAll} and {@code canModifyAll}
 * override sharing rules for the collection.
 */
public record ObjectPermissions(
    boolean canCreate,
    boolean canRead,
    boolean canEdit,
    boolean canDelete,
    boolean canViewAll,
    boolean canModifyAll
) {

    /** No permissions granted. */
    public static final ObjectPermissions NONE =
            new ObjectPermissions(false, false, false, false, false, false);

    /** All permissions granted. */
    public static final ObjectPermissions FULL =
            new ObjectPermissions(true, true, true, true, true, true);
}
