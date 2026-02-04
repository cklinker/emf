package com.emf.runtime.query;

import java.util.Map;

/**
 * Represents pagination settings for a query.
 * 
 * <p>Pagination allows clients to retrieve large result sets in manageable chunks.
 * The page number is 1-indexed (first page is 1, not 0).
 * 
 * <h2>Query Parameter Format</h2>
 * <ul>
 *   <li>{@code page[number]} - The page number (1-indexed, default: 1)</li>
 *   <li>{@code page[size]} - The number of records per page (default: 20, max: 1000)</li>
 * </ul>
 * 
 * @param pageNumber the page number (1-indexed, must be >= 1)
 * @param pageSize the number of records per page (must be between 1 and 1000)
 * 
 * @since 1.0.0
 */
public record Pagination(
    int pageNumber,
    int pageSize
) {
    /**
     * Default page size when not specified.
     */
    public static final int DEFAULT_PAGE_SIZE = 20;
    
    /**
     * Maximum allowed page size.
     */
    public static final int MAX_PAGE_SIZE = 1000;
    
    /**
     * Compact constructor with validation.
     */
    public Pagination {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Page number must be >= 1, got: " + pageNumber);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be >= 1, got: " + pageSize);
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be <= " + MAX_PAGE_SIZE + ", got: " + pageSize);
        }
    }
    
    /**
     * Creates pagination settings from HTTP query parameters.
     * 
     * @param params the HTTP query parameters
     * @return pagination settings parsed from parameters, or defaults if not specified
     */
    public static Pagination fromParams(Map<String, String> params) {
        int pageNumber = parseIntParam(params.get("page[number]"), 1);
        int pageSize = parseIntParam(params.get("page[size]"), DEFAULT_PAGE_SIZE);
        
        // Clamp values to valid ranges
        pageNumber = Math.max(1, pageNumber);
        pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        
        return new Pagination(pageNumber, pageSize);
    }
    
    /**
     * Creates default pagination settings (page 1, size 20).
     * 
     * @return default pagination settings
     */
    public static Pagination defaults() {
        return new Pagination(1, DEFAULT_PAGE_SIZE);
    }
    
    /**
     * Calculates the offset for SQL OFFSET clause.
     * 
     * @return the offset (0-indexed)
     */
    public int offset() {
        return (pageNumber - 1) * pageSize;
    }
    
    /**
     * Creates a new Pagination for the next page.
     * 
     * @return pagination for the next page
     */
    public Pagination nextPage() {
        return new Pagination(pageNumber + 1, pageSize);
    }
    
    /**
     * Creates a new Pagination for the previous page.
     * 
     * @return pagination for the previous page, or this if already on page 1
     */
    public Pagination previousPage() {
        if (pageNumber <= 1) {
            return this;
        }
        return new Pagination(pageNumber - 1, pageSize);
    }
    
    /**
     * Parses an integer parameter with a default value.
     * 
     * @param value the parameter value (may be null)
     * @param defaultValue the default value if parsing fails
     * @return the parsed integer or default value
     */
    private static int parseIntParam(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
