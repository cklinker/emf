package io.kelta.runtime.query;

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
 *   <li>{@code eq} - equals (single value only; a comma-separated value is
 *       treated as one literal — use {@code in} for multi-value matching)</li>
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
 *   <li>{@code in} (alias {@code any}) - matches any value in a
 *       comma-separated list, e.g. {@code filter[id][in]=u1,u2,u3}.
 *       Capped at {@value #MAX_IN_LIST_SIZE} values per condition.</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <ul>
 *   <li>{@code filter[status][eq]=active} - status equals "active"</li>
 *   <li>{@code filter[price][gte]=100} - price >= 100</li>
 *   <li>{@code filter[name][icontains]=john} - name contains "john" (case-insensitive)</li>
 *   <li>{@code filter[id][in]=a,b,c} - id is any of "a", "b", "c"</li>
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
     * Maximum number of values accepted in a single {@code IN}/{@code ANY}
     * filter list. Exceeding this raises {@link InvalidQueryException} (400)
     * so a runaway client cannot build an unbounded SQL parameter list.
     */
    public static final int MAX_IN_LIST_SIZE = 200;
    
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
     * Creates a filter condition for "in a set of values" (IN / ANY).
     *
     * @param fieldName the field name
     * @param values the collection of values to match against
     * @return a new FilterCondition with IN operator
     */
    public static FilterCondition in(String fieldName, java.util.Collection<?> values) {
        return new FilterCondition(fieldName, FilterOperator.IN, List.copyOf(values));
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

                FilterOperator operator = resolveOperator(operatorStr);
                if (operator == null) {
                    // Unknown operator, skip this filter
                    continue;
                }
                Object parsedValue = parseValue(fieldName, value, operator);
                filters.add(new FilterCondition(fieldName, operator, parsedValue));
            }
        }

        return List.copyOf(filters);
    }

    /**
     * Resolves a raw HTTP operator token to a {@link FilterOperator}, including
     * the {@code ANY} alias for {@link FilterOperator#IN}. Returns {@code null}
     * for unknown tokens so the caller can skip the filter silently.
     */
    private static FilterOperator resolveOperator(String operatorStr) {
        if ("ANY".equals(operatorStr)) {
            return FilterOperator.IN;
        }
        try {
            return FilterOperator.valueOf(operatorStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses the filter value based on the operator.
     *
     * @param fieldName the field name (used in error messages)
     * @param value the string value
     * @param operator the filter operator
     * @return the parsed value
     * @throws InvalidQueryException when an IN/ANY list exceeds the per-condition cap
     */
    private static Object parseValue(String fieldName, String value, FilterOperator operator) {
        if (operator == FilterOperator.ISNULL) {
            return Boolean.parseBoolean(value);
        }
        if (operator == FilterOperator.IN) {
            return parseInList(fieldName, value);
        }
        return value;
    }

    /**
     * Splits a comma-separated IN value into a list of trimmed, non-empty
     * strings and enforces {@link #MAX_IN_LIST_SIZE}.
     */
    private static List<String> parseInList(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.split(",", -1);
        List<String> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        if (values.size() > MAX_IN_LIST_SIZE) {
            throw new InvalidQueryException(fieldName,
                "IN/ANY list has " + values.size() + " values; maximum is " + MAX_IN_LIST_SIZE);
        }
        return List.copyOf(values);
    }
}
