package com.emf.runtime.query;

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
    IEQ
}
