package com.emf.gateway.route;

import java.util.Objects;

/**
 * Data model representing a single route configuration in the API Gateway.
 *
 * A route defines how incoming HTTP requests are mapped to backend services,
 * including path patterns, service endpoints, and optional rate limiting.
 *
 * This class is immutable and thread-safe.
 */
public class RouteDefinition {

    private final String id;
    private final String path;
    private final String backendUrl;
    private final String collectionName;
    private final RateLimitConfig rateLimit;

    /**
     * Creates a new RouteDefinition.
     *
     * @param id Unique route identifier (typically the collection ID)
     * @param path Path pattern for matching requests (e.g., "/api/users/**")
     * @param backendUrl Backend service URL
     * @param collectionName Collection name for authorization lookup
     * @param rateLimit Optional rate limit configuration (can be null)
     */
    public RouteDefinition(String id, String path,
                          String backendUrl, String collectionName,
                          RateLimitConfig rateLimit) {
        this.id = id;
        this.path = path;
        this.backendUrl = backendUrl;
        this.collectionName = collectionName;
        this.rateLimit = rateLimit;
    }

    /**
     * Creates a new RouteDefinition without rate limiting.
     */
    public RouteDefinition(String id, String path,
                          String backendUrl, String collectionName) {
        this(id, path, backendUrl, collectionName, null);
    }

    public String getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public String getBackendUrl() {
        return backendUrl;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public RateLimitConfig getRateLimit() {
        return rateLimit;
    }

    /**
     * Checks if this route has rate limiting configured.
     */
    public boolean hasRateLimit() {
        return rateLimit != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteDefinition that = (RouteDefinition) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(path, that.path) &&
               Objects.equals(backendUrl, that.backendUrl) &&
               Objects.equals(collectionName, that.collectionName) &&
               Objects.equals(rateLimit, that.rateLimit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, path, backendUrl, collectionName, rateLimit);
    }

    @Override
    public String toString() {
        return "RouteDefinition{" +
               "id='" + id + '\'' +
               ", path='" + path + '\'' +
               ", backendUrl='" + backendUrl + '\'' +
               ", collectionName='" + collectionName + '\'' +
               ", rateLimit=" + rateLimit +
               '}';
    }
}
