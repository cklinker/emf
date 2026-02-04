package com.emf.runtime.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a sort field with direction for query ordering.
 * 
 * <p>Sort fields are specified in the query using the format:
 * <ul>
 *   <li>{@code sort=field1} - Sort by field1 ascending</li>
 *   <li>{@code sort=-field1} - Sort by field1 descending (prefix with -)</li>
 *   <li>{@code sort=field1,-field2} - Sort by field1 ascending, then field2 descending</li>
 * </ul>
 * 
 * @param fieldName the name of the field to sort by
 * @param direction the sort direction (ASC or DESC)
 * 
 * @see SortDirection
 * @since 1.0.0
 */
public record SortField(
    String fieldName,
    SortDirection direction
) {
    /**
     * Compact constructor with validation.
     */
    public SortField {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        Objects.requireNonNull(direction, "direction cannot be null");
    }
    
    /**
     * Creates a sort field for ascending order.
     * 
     * @param fieldName the field name
     * @return a new SortField with ASC direction
     */
    public static SortField asc(String fieldName) {
        return new SortField(fieldName, SortDirection.ASC);
    }
    
    /**
     * Creates a sort field for descending order.
     * 
     * @param fieldName the field name
     * @return a new SortField with DESC direction
     */
    public static SortField desc(String fieldName) {
        return new SortField(fieldName, SortDirection.DESC);
    }
    
    /**
     * Parses sort fields from the sort query parameter.
     * 
     * <p>Format: {@code field1,-field2,field3} where - prefix indicates descending order.
     * 
     * @param sortParam the sort parameter value (may be null)
     * @return list of sort fields, or empty list if null/blank
     */
    public static List<SortField> fromParams(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return List.of();
        }
        
        List<SortField> sortFields = new ArrayList<>();
        String[] parts = sortParam.split(",");
        
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            if (trimmed.startsWith("-")) {
                String fieldName = trimmed.substring(1).trim();
                if (!fieldName.isEmpty()) {
                    sortFields.add(new SortField(fieldName, SortDirection.DESC));
                }
            } else {
                sortFields.add(new SortField(trimmed, SortDirection.ASC));
            }
        }
        
        return List.copyOf(sortFields);
    }
    
    /**
     * Converts this sort field to SQL ORDER BY clause fragment.
     * 
     * @return SQL fragment like "fieldName ASC" or "fieldName DESC"
     */
    public String toSql() {
        return fieldName + " " + direction.name();
    }
}
