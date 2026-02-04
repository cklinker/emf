package com.emf.runtime.model;

/**
 * API configuration for a collection.
 * 
 * <p>Controls which CRUD operations are enabled for the collection's REST API.
 * 
 * @param listEnabled Whether LIST operation is enabled
 * @param getEnabled Whether GET by ID operation is enabled
 * @param createEnabled Whether POST/CREATE operation is enabled
 * @param updateEnabled Whether PUT/UPDATE operation is enabled
 * @param deleteEnabled Whether DELETE operation is enabled
 * @param basePath Base path for the collection's API endpoints
 * 
 * @since 1.0.0
 */
public record ApiConfig(
    boolean listEnabled,
    boolean getEnabled,
    boolean createEnabled,
    boolean updateEnabled,
    boolean deleteEnabled,
    String basePath
) {
    /**
     * Creates an API configuration with all operations enabled.
     * 
     * @param basePath the base path for API endpoints
     * @return API configuration with all operations enabled
     */
    public static ApiConfig allEnabled(String basePath) {
        return new ApiConfig(true, true, true, true, true, basePath);
    }
    
    /**
     * Creates an API configuration with only read operations enabled.
     * 
     * @param basePath the base path for API endpoints
     * @return API configuration with only read operations enabled
     */
    public static ApiConfig readOnly(String basePath) {
        return new ApiConfig(true, true, false, false, false, basePath);
    }
    
    /**
     * Creates an API configuration with all operations disabled.
     * 
     * @param basePath the base path for API endpoints
     * @return API configuration with all operations disabled
     */
    public static ApiConfig disabled(String basePath) {
        return new ApiConfig(false, false, false, false, false, basePath);
    }
}
