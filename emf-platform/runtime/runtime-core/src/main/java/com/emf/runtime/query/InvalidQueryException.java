package com.emf.runtime.query;

/**
 * Exception thrown when a query contains invalid parameters.
 * 
 * <p>This exception is thrown when:
 * <ul>
 *   <li>Sort fields reference non-existent fields</li>
 *   <li>Filter fields reference non-existent fields</li>
 *   <li>Requested fields reference non-existent fields</li>
 *   <li>Filter operators are incompatible with field types</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class InvalidQueryException extends RuntimeException {
    
    private final String fieldName;
    private final String reason;
    
    /**
     * Creates a new InvalidQueryException.
     * 
     * @param message the error message
     */
    public InvalidQueryException(String message) {
        super(message);
        this.fieldName = null;
        this.reason = message;
    }
    
    /**
     * Creates a new InvalidQueryException for a specific field.
     * 
     * @param fieldName the field that caused the error
     * @param reason the reason for the error
     */
    public InvalidQueryException(String fieldName, String reason) {
        super("Invalid query parameter for field '" + fieldName + "': " + reason);
        this.fieldName = fieldName;
        this.reason = reason;
    }
    
    /**
     * Gets the field name that caused the error.
     * 
     * @return the field name, or null if not field-specific
     */
    public String getFieldName() {
        return fieldName;
    }
    
    /**
     * Gets the reason for the error.
     * 
     * @return the reason
     */
    public String getReason() {
        return reason;
    }
}
