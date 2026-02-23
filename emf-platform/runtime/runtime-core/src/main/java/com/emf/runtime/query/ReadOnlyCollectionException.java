package com.emf.runtime.query;

/**
 * Exception thrown when a write operation (create, update, delete) is attempted
 * on a read-only collection.
 *
 * <p>Read-only collections are typically system collections that contain
 * historical or audit data (e.g., security audit logs, login history) and
 * should not be modified through the API.
 *
 * @since 1.0.0
 */
public class ReadOnlyCollectionException extends RuntimeException {

    private final String collectionName;

    /**
     * Creates a new ReadOnlyCollectionException.
     *
     * @param collectionName the name of the read-only collection
     */
    public ReadOnlyCollectionException(String collectionName) {
        super("Collection '" + collectionName + "' is read-only and does not support write operations");
        this.collectionName = collectionName;
    }

    /**
     * Gets the name of the read-only collection.
     *
     * @return the collection name
     */
    public String getCollectionName() {
        return collectionName;
    }
}
