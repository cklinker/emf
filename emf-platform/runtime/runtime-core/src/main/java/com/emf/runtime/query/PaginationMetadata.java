package com.emf.runtime.query;

/**
 * Metadata about pagination in a query result.
 * 
 * <p>This record provides information about the current page position within
 * the full result set, enabling clients to implement pagination controls.
 * 
 * @param totalCount the total number of records matching the query (before pagination)
 * @param currentPage the current page number (1-indexed)
 * @param pageSize the number of records per page
 * @param totalPages the total number of pages
 * 
 * @since 1.0.0
 */
public record PaginationMetadata(
    long totalCount,
    int currentPage,
    int pageSize,
    int totalPages
) {
    /**
     * Compact constructor with validation.
     */
    public PaginationMetadata {
        if (totalCount < 0) {
            throw new IllegalArgumentException("Total count must be >= 0, got: " + totalCount);
        }
        if (currentPage < 1) {
            throw new IllegalArgumentException("Current page must be >= 1, got: " + currentPage);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be >= 1, got: " + pageSize);
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("Total pages must be >= 0, got: " + totalPages);
        }
    }
    
    /**
     * Checks if there is a next page available.
     * 
     * @return true if there are more pages after the current one
     */
    public boolean hasNextPage() {
        return currentPage < totalPages;
    }
    
    /**
     * Checks if there is a previous page available.
     * 
     * @return true if there are pages before the current one
     */
    public boolean hasPreviousPage() {
        return currentPage > 1;
    }
    
    /**
     * Checks if this is the first page.
     * 
     * @return true if this is page 1
     */
    public boolean isFirstPage() {
        return currentPage == 1;
    }
    
    /**
     * Checks if this is the last page.
     * 
     * @return true if this is the last page
     */
    public boolean isLastPage() {
        return currentPage >= totalPages;
    }
}
