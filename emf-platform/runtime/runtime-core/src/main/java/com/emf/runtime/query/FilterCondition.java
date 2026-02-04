package com.emf.runtime.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a filter condition for query filtering.
 * 
 * <p>Filter conditions are specified in the query using the format:
 * {@code filter[field][operator]=value}
 * 
 * <h2>Supported Operators</h2>
 * <ul>
 *   <li>{@code eq} - equals</li>
 *   <li>{@code neq} - not equals</li>
 *   <li>{@code gt} - greater than</li>
 *   <li>{@code lt} - less than</li>
 *   <li>{@code gte} - greater than or equal</li>
 *   <li>{@code lte} - less than or equal</li>
 *   <li>{@code isnull} - is null (value should be "true" or "false")</li>
 *   <li>{@code contains} - contains substring (case-sensitive)</li>
 *   <li>{@code starts} - starts with (case-sensitive)</li>
 *   <li>{@code ends} - ends with (case-sensitive)</li>
 *   <li>{@code icontains} - contains substring (case-insensitive)</li>
 *   <li>{@code istarts} - starts with (case-insensitive)</li>
 *   <li>{@code iends} - ends with (case-insensitive)</li>
 *   <li>{@code ieq} - equals (case-insensitive)</li>
 * </ul>
 * 
 * <h2>Examples</h2>
 * <ul>
 *   <li>{@code filter[status][eq]=active} - status equals "active"</li>
 *   <li>{@code filter[price][gte]=100} - price >= 100</li>
 *   <li>{@code filter[name][icontains]=john} - name contains "john" (case-insensitive)</li>
 * </ul>
 * 
 * @param fieldName the name of the field to filter on
 * @param operator the filter operator
 * @param value the value to compare against
 * 
 * @see FilterOperator
 * @since 1.0.0
 */
public record FilterCondition(
    String fieldName,
    FilterOperator operator,
    Object value
) {
    /**
     * Pattern to match filter parameters: filter[fieldName][operator]
     */
    private static final Pattern FILTER_PATTERN = Pattern.compile("filter\\[([^\\]]+)\\]\\[([^\\]]+)\\]");
    
    /**
     * Compact constructor with validation.
     */
    public FilterCondition {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        Objects.requireNonNull(operator, "operator cannot be null");
        // value can be null for ISNULL operator
    }
    
    /**
     * Creates a filter condition for equality.
     * 
     * @param fieldName the field name
     * @param value the value to match
     * @return a new FilterCondition with EQ operator
     */
    public static FilterCondition eq(String fieldName, Object value) {
        return new FilterCondition(fieldName, FilterOperator.EQ, value);
    }
    
    /**
     * Creates a filter condition for not equals.
     * 
     * @param fieldName the field name
     * @param value the value to not match
     * @return a new FilterCondition with NEQ operator
     */
    public static FilterCondition neq(String fieldName, Object value) {
        return new FilterCondition(fieldName, FilterOperator.NEQ, value);
    }
    
    /**
     * Creates a filter condition for greater than.
     * 
     * @param fieldName the field name
     * @param value the value to compare against
     * @return a new FilterCondition with GT operator
     */
    public static FilterCondition gt(String fieldName, Object value) {
        return new FilterCondition(fieldName, FilterOperator.GT, value);
    }
    
    /**
     * Creates a filter condition for less than.
     * 
     * @param fieldName the field name
     * @param value the value to compare against
     * @return a new FilterCondition with LT operator
     */
    public static FilterCondition lt(String fieldName, Object value) {
        return new FilterCondition(fieldName, FilterOperator.LT, value);
    }
    
    /**
     * Creates a filter condition for is null check.
     * 
     * @param fieldName the field name
     * @param isNull true to check for null, false to check for not null
     * @return a new FilterCondition with ISNULL operator
     */
    public static FilterCondition isNull(String fieldName, boolean isNull) {
        return new FilterCondition(fieldName, FilterOperator.ISNULL, isNull);
    }
    
    /**
     * Creates a filter condition for contains (case-sensitive).
     * 
     * @param fieldName the field name
     * @param value the substring to search for
     * @return a new FilterCondition with CONTAINS operator
     */
    public static FilterCondition contains(String fieldName, String value) {
        return new FilterCondition(fieldName, FilterOperator.CONTAINS, value);
    }
    
    /**
     * Creates a filter condition for contains (case-insensitive).
     * 
     * @param fieldName the field name
     * @param value the substring to search for
     * @return a new FilterCondition with ICONTAINS operator
     */
    public static FilterCondition icontains(String fieldName, String value) {
        return new FilterCondition(fieldName, FilterOperator.ICONTAINS, value);
    }
    
    /**
     * Parses filter conditions from HTTP query parameters.
     * 
     * <p>Looks for parameters matching the pattern {@code filter[field][operator]=value}.
     * 
     * @param params the HTTP query parameters
     * @return list of filter conditions, or empty list if none found
     */
    public static List<FilterCondition> fromParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return List.of();
        }
        
        List<FilterCondition> filters = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : params.entrySet()) {
            Matcher matcher = FILTER_PATTERN.matcher(entry.getKey());
            if (matcher.matches()) {
                String fieldName = matcher.group(1);
                String operatorStr = matcher.group(2).toUpperCase();
                String value = entry.getValue();
                
                try {
                    FilterOperator operator = FilterOperator.valueOf(operatorStr);
                    Object parsedValue = parseValue(value, operator);
                    filters.add(new FilterCondition(fieldName, operator, parsedValue));
                } catch (IllegalArgumentException e) {
                    // Unknown operator, skip this filter
                }
            }
        }
        
        return List.copyOf(filters);
    }
    
    /**
     * Parses the filter value based on the operator.
     * 
     * @param value the string value
     * @param operator the filter operator
     * @return the parsed value
     */
    private static Object parseValue(String value, FilterOperator operator) {
        if (operator == FilterOperator.ISNULL) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }
}
