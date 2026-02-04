package com.emf.runtime.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the result of a query operation including data and pagination metadata.
 * 
 * <p>This record contains:
 * <ul>
 *   <li><b>data</b> - The list of records matching the query</li>
 *   <li><b>metadata</b> - Pagination information including total count, current page, etc.</li>
 * </ul>
 * 
 * <h2>Example Response Structure</h2>
 * <pre>{@code
 * {
 *   "data": [
 *     {"id": "123", "name": "Product A", "price": 99.99},
 *     {"id": "456", "name": "Product B", "price": 149.99}
 *   ],
 *   "metadata": {
 *     "totalCount": 100,
 *     "currentPage": 1,
 *     "pageSize": 20,
 *     "totalPages": 5
 *   }
 * }
 * }</pre>
 * 
 * @param data list of records matching the query
 * @param metadata pagination metadata
 * 
 * @see PaginationMetadata
 * @since 1.0.0
 */
public record QueryResult(
    List<Map<String, Object>> data,
    PaginationMetadata metadata
) {
    /**
     * Compact constructor with validation and defensive copying.
     */
    public QueryResult {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(metadata, "metadata cannot be null");
        data = List.copyOf(data);
    }
    
    /**
     * Creates an empty query result with zero total count.
     * 
     * @param pagination the pagination settings used for the query
     * @return an empty query result
     */
    public static QueryResult empty(Pagination pagination) {
        return new QueryResult(
            List.of(),
            new PaginationMetadata(0, pagination.pageNumber(), pagination.pageSize(), 0)
        );
    }
    
    /**
     * Creates a query result from data and total count.
     * 
     * @param data the list of records
     * @param totalCount the total number of records matching the query (before pagination)
     * @param pagination the pagination settings used for the query
     * @return a new query result
     */
    public static QueryResult of(List<Map<String, Object>> data, long totalCount, Pagination pagination) {
        int totalPages = (int) Math.ceil((double) totalCount / pagination.pageSize());
        PaginationMetadata metadata = new PaginationMetadata(
            totalCount,
            pagination.pageNumber(),
            pagination.pageSize(),
            totalPages
        );
        return new QueryResult(data, metadata);
    }
    
    /**
     * Checks if this result is empty.
     * 
     * @return true if no records were returned
     */
    public boolean isEmpty() {
        return data.isEmpty();
    }
    
    /**
     * Gets the number of records in this result page.
     * 
     * @return the number of records
     */
    public int size() {
        return data.size();
    }
}
