package com.emf.runtime.event;

/**
 * Enum representing the type of change that occurred to a configuration entity.
 * 
 * This is a shared enum used across all EMF services.
 */
public enum ChangeType {
    /**
     * Entity was created.
     */
    CREATED,

    /**
     * Entity was updated.
     */
    UPDATED,

    /**
     * Entity was deleted (soft-deleted).
     */
    DELETED
}
