package io.kelta.runtime.query;

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
 *   <li>{@code page[size]} - The number of records per page (default: 20, max: 200)</li>
 * </ul>
 *
 * <p>{@link #fromParams(Map)} clamps an out-of-range {@code page[size]} value
 * (e.g. {@code page[size]=500}) down to {@link #MAX_HTTP_PAGE_SIZE} so that
 * paginated REST endpoints never silently fall back to the default page size.
 * The clamp is surfaced in the JSON:API response by
 * {@code DynamicCollectionRouter} as {@code metadata.pageSizeClamped=true} and
 * {@code metadata.requestedPageSize=<caller value>}, so callers can detect a
 * modified request rather than inferring it from {@code data.length} vs.
 * {@code metadata.pageSize}.
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
     * Absolute upper bound for the {@code pageSize} component of a Pagination
     * record. Enforced by the compact constructor — internal callers (report
     * execution, data export, include resolution) may construct values up to
     * this ceiling for batch fetches.
     *
     * <p>HTTP requests are clamped by {@link #fromParams(Map)} to the stricter
     * {@link #MAX_HTTP_PAGE_SIZE} so untrusted callers can't reach the full
     * range.
     */
    public static final int MAX_PAGE_SIZE = 1000;

    /**
     * Maximum allowed {@code page[size]} for paginated REST endpoints.
     *
     * <p>Requests with {@code page[size]} above this value are silently clamped
     * down by {@link #fromParams(Map)} so a runaway client can't exhaust memory
     * or hold a connection for an unbounded result set. Internal services with
     * a legitimate need for larger batches (report export, include resolution)
     * construct {@link Pagination} directly and so clamp against
     * {@link #MAX_PAGE_SIZE} instead.
     */
    public static final int MAX_HTTP_PAGE_SIZE = 200;
    
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

        // Clamp values to valid ranges. HTTP callers cap at MAX_HTTP_PAGE_SIZE
        // so a runaway client can't ask for an unbounded result set; internal
        // callers that construct Pagination directly may use the larger
        // MAX_PAGE_SIZE.
        pageNumber = Math.max(1, pageNumber);
        pageSize = Math.max(1, Math.min(MAX_HTTP_PAGE_SIZE, pageSize));

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
