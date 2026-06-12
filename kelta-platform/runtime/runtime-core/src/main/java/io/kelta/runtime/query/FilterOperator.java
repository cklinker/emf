package io.kelta.runtime.query;

/**
 * Filter operators for query filtering.
 * 
 * <p>These operators are used in filter conditions to specify how field values
 * should be compared against filter values.
 * 
 * @since 1.0.0
 */
public enum FilterOperator {
    /**
     * Equals (case-sensitive for strings).
     * <p>Usage: {@code filter[field][eq]=value}
     */
    EQ,
    
    /**
     * Not equals (case-sensitive for strings).
     * <p>Usage: {@code filter[field][neq]=value}
     */
    NEQ,
    
    /**
     * Greater than.
     * <p>Usage: {@code filter[field][gt]=value}
     */
    GT,
    
    /**
     * Less than.
     * <p>Usage: {@code filter[field][lt]=value}
     */
    LT,
    
    /**
     * Greater than or equal.
     * <p>Usage: {@code filter[field][gte]=value}
     */
    GTE,
    
    /**
     * Less than or equal.
     * <p>Usage: {@code filter[field][lte]=value}
     */
    LTE,
    
    /**
     * Is null check.
     * <p>Usage: {@code filter[field][isnull]=true} or {@code filter[field][isnull]=false}
     */
    ISNULL,
    
    /**
     * Contains substring (case-sensitive).
     * <p>Usage: {@code filter[field][contains]=value}
     */
    CONTAINS,
    
    /**
     * Starts with (case-sensitive).
     * <p>Usage: {@code filter[field][starts]=value}
     */
    STARTS,
    
    /**
     * Ends with (case-sensitive).
     * <p>Usage: {@code filter[field][ends]=value}
     */
    ENDS,
    
    /**
     * Contains substring (case-insensitive).
     * <p>Usage: {@code filter[field][icontains]=value}
     */
    ICONTAINS,
    
    /**
     * Starts with (case-insensitive).
     * <p>Usage: {@code filter[field][istarts]=value}
     */
    ISTARTS,
    
    /**
     * Ends with (case-insensitive).
     * <p>Usage: {@code filter[field][iends]=value}
     */
    IENDS,
    
    /**
     * Equals (case-insensitive).
     * <p>Usage: {@code filter[field][ieq]=value}
     */
    IEQ,

    /**
     * In a set of values.
     * <p>The filter value should be a {@link java.util.Collection} of values, or a
     * comma-separated string when parsed from URL query parameters.
     * <p>Usage (URL): {@code filter[field][in]=a,b,c} (alias: {@code filter[field][any]=a,b,c}).
     * <p>The list is capped at {@link FilterCondition#MAX_IN_LIST_SIZE}; over-cap requests
     * raise {@link InvalidQueryException} (HTTP 400).
     * <p>Note: comma-separated values are only multi-value for {@code IN}/{@code ANY}.
     * {@code EQ} remains a literal-string match — {@code filter[field][eq]=a,b,c} looks
     * for the literal value {@code "a,b,c"}, not three values.
     * <p>Usage (internal): created programmatically via
     * {@code new FilterCondition("field", FilterOperator.IN, listOfValues)}
     */
    IN
}
