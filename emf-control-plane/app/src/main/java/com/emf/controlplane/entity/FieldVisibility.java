package com.emf.controlplane.entity;

/**
 * Visibility level for field-level permissions.
 */
public enum FieldVisibility {

    /** Field is fully visible and editable (subject to object-level edit permission). */
    VISIBLE,

    /** Field is visible but cannot be edited by the user. */
    READ_ONLY,

    /** Field is completely hidden from the user. */
    HIDDEN
}
