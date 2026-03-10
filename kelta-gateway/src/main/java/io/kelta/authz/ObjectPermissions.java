package io.kelta.gateway.authz;

/**
 * Object-level CRUD permissions for a specific collection.
 *
 * <p>Each boolean flag controls whether the user can perform the corresponding
 * operation on records in the collection.
 */
public record ObjectPermissions(
    boolean canCreate,
    boolean canRead,
    boolean canEdit,
    boolean canDelete
) {

    /** No permissions granted. */
    public static final ObjectPermissions NONE =
            new ObjectPermissions(false, false, false, false);

    /** All permissions granted. */
    public static final ObjectPermissions FULL =
            new ObjectPermissions(true, true, true, true);
}
