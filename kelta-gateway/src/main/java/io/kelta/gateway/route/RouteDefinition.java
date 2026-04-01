package io.kelta.gateway.route;

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
    private final int stripPrefix;

    /**
     * Creates a new RouteDefinition.
     *
     * @param id Unique route identifier (typically the collection ID)
     * @param path Path pattern for matching requests (e.g., "/api/users/**")
     * @param backendUrl Backend service URL
     * @param collectionName Collection name for authorization lookup
     * @param rateLimit Optional rate limit configuration (can be null)
     * @param stripPrefix Number of path segments to strip before forwarding (0 = none)
     */
    public RouteDefinition(String id, String path,
                          String backendUrl, String collectionName,
                          RateLimitConfig rateLimit, int stripPrefix) {
        this.id = id;
        this.path = path;
        this.backendUrl = backendUrl;
        this.collectionName = collectionName;
        this.rateLimit = rateLimit;
        this.stripPrefix = stripPrefix;
    }

    /**
     * Creates a new RouteDefinition with rate limiting but no prefix stripping.
     */
    public RouteDefinition(String id, String path,
                          String backendUrl, String collectionName,
                          RateLimitConfig rateLimit) {
        this(id, path, backendUrl, collectionName, rateLimit, 0);
    }

    /**
     * Creates a new RouteDefinition without rate limiting or prefix stripping.
     */
    public RouteDefinition(String id, String path,
                          String backendUrl, String collectionName) {
        this(id, path, backendUrl, collectionName, null, 0);
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

    public int getStripPrefix() {
        return stripPrefix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteDefinition that = (RouteDefinition) o;
        return stripPrefix == that.stripPrefix &&
               Objects.equals(id, that.id) &&
               Objects.equals(path, that.path) &&
               Objects.equals(backendUrl, that.backendUrl) &&
               Objects.equals(collectionName, that.collectionName) &&
               Objects.equals(rateLimit, that.rateLimit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, path, backendUrl, collectionName, rateLimit, stripPrefix);
    }

    @Override
    public String toString() {
        return "RouteDefinition{" +
               "id='" + id + '\'' +
               ", path='" + path + '\'' +
               ", backendUrl='" + backendUrl + '\'' +
               ", collectionName='" + collectionName + '\'' +
               ", rateLimit=" + rateLimit +
               ", stripPrefix=" + stripPrefix +
               '}';
    }
}
