package com.emf.runtime.storage;

/**
 * Base exception for storage-related errors.
 * 
 * <p>This exception is thrown when storage operations fail due to database errors,
 * connection issues, or other storage-related problems.
 * 
 * @since 1.0.0
 */
public class StorageException extends RuntimeException {
    
    /**
     * Creates a new StorageException with a message.
     * 
     * @param message the error message
     */
    public StorageException(String message) {
        super(message);
    }
    
    /**
     * Creates a new StorageException with a message and cause.
     * 
     * @param message the error message
     * @param cause the underlying cause
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Creates a new StorageException with a cause.
     * 
     * @param cause the underlying cause
     */
    public StorageException(Throwable cause) {
        super(cause);
    }
}
