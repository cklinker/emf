package com.emf.runtime.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a query request with pagination, sorting, field selection, and filtering.
 * 
 * <p>This record encapsulates all query parameters that can be specified when
 * querying a collection. It supports:
 * <ul>
 *   <li><b>Pagination</b> - Page number and page size</li>
 *   <li><b>Sorting</b> - Multiple sort fields with ascending/descending order</li>
 *   <li><b>Field Selection</b> - Specific fields to return in the response</li>
 *   <li><b>Filtering</b> - Multiple filter conditions combined with AND logic</li>
 * </ul>
 * 
 * <h2>Query Parameter Formats</h2>
 * <ul>
 *   <li>Pagination: {@code page[number]=1&page[size]=20}</li>
 *   <li>Sorting: {@code sort=field1,-field2} (prefix with - for descending)</li>
 *   <li>Fields: {@code fields=fieldA,fieldB}</li>
 *   <li>Filtering: {@code filter[field][op]=value}</li>
 * </ul>
 * 
 * @param pagination pagination settings (page number and size)
 * @param sorting list of sort fields with direction
 * @param fields list of field names to return (empty means all fields)
 * @param filters list of filter conditions
 * 
 * @see Pagination
 * @see SortField
 * @see FilterCondition
 * @since 1.0.0
 */
public record QueryRequest(
    Pagination pagination,
    List<SortField> sorting,
    List<String> fields,
    List<FilterCondition> filters
) {
    /**
     * Compact constructor with validation and defensive copying.
     */
    public QueryRequest {
        Objects.requireNonNull(pagination, "pagination cannot be null");
        sorting = sorting != null ? List.copyOf(sorting) : List.of();
        fields = fields != null ? List.copyOf(fields) : List.of();
        filters = filters != null ? List.copyOf(filters) : List.of();
    }
    
    /**
     * Creates a QueryRequest from HTTP query parameters.
     * 
     * <p>Parses the following parameter formats:
     * <ul>
     *   <li>{@code page[number]} - Page number (default: 1)</li>
     *   <li>{@code page[size]} - Page size (default: 20)</li>
     *   <li>{@code sort} - Comma-separated sort fields (prefix with - for descending)</li>
     *   <li>{@code fields} - Comma-separated field names</li>
     *   <li>{@code filter[field][op]} - Filter conditions</li>
     * </ul>
     * 
     * @param params the HTTP query parameters
     * @return a new QueryRequest parsed from the parameters
     */
    public static QueryRequest fromParams(Map<String, String> params) {
        Pagination pagination = Pagination.fromParams(params);
        List<SortField> sorting = SortField.fromParams(params.get("sort"));
        List<String> fields = parseFields(params.get("fields"));
        List<FilterCondition> filters = FilterCondition.fromParams(params);
        
        return new QueryRequest(pagination, sorting, fields, filters);
    }
    
    /**
     * Creates a default QueryRequest with default pagination and no sorting, filtering, or field selection.
     * 
     * @return a new QueryRequest with defaults
     */
    public static QueryRequest defaults() {
        return new QueryRequest(
            Pagination.defaults(),
            List.of(),
            List.of(),
            List.of()
        );
    }
    
    /**
     * Parses the fields parameter into a list of field names.
     * 
     * @param fieldsParam the fields parameter value (comma-separated)
     * @return list of field names, or empty list if null/blank
     */
    private static List<String> parseFields(String fieldsParam) {
        if (fieldsParam == null || fieldsParam.isBlank()) {
            return List.of();
        }
        return List.of(fieldsParam.split(","))
            .stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
    
    /**
     * Checks if this request has any sorting specified.
     * 
     * @return true if sorting is specified
     */
    public boolean hasSorting() {
        return !sorting.isEmpty();
    }
    
    /**
     * Checks if this request has field selection specified.
     * 
     * @return true if specific fields are requested
     */
    public boolean hasFieldSelection() {
        return !fields.isEmpty();
    }
    
    /**
     * Checks if this request has any filters specified.
     * 
     * @return true if filters are specified
     */
    public boolean hasFilters() {
        return !filters.isEmpty();
    }
}
