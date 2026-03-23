package io.kelta.gateway.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Collections;
import java.util.List;

/**
 * Determines whether a request targets a public (unauthenticated) path.
 *
 * <p>Two categories of public paths are supported:
 * <ul>
 *   <li>{@code public-paths} — accessible without authentication for GET and HEAD
 *       requests only. Used for UI bootstrap endpoints (ui-pages, ui-menus, etc.).</li>
 *   <li>{@code unauthenticated-paths} — accessible without authentication for
 *       <em>all</em> HTTP methods. Used for endpoints that receive data from
 *       unauthenticated sources (e.g., browser OTEL SDK posting trace data).</li>
 * </ul>
 *
 * <p>Both are configured as comma-separated lists of path prefixes in application.yml
 * under {@code kelta.gateway.security}.
 */
@Component
public class PublicPathMatcher {

    private final List<String> publicPaths;
    private final List<String> unauthenticatedPaths;

    public PublicPathMatcher(
            @Value("${kelta.gateway.security.public-paths:}") List<String> publicPaths,
            @Value("${kelta.gateway.security.unauthenticated-paths:}") List<String> unauthenticatedPaths) {
        this.publicPaths = publicPaths;
        this.unauthenticatedPaths = unauthenticatedPaths != null ? unauthenticatedPaths : Collections.emptyList();
    }

    /**
     * Returns true if this request targets a public path.
     *
     * <p>A request is public if:
     * <ul>
     *   <li>The path matches an {@code unauthenticated-paths} prefix (any HTTP method), or</li>
     *   <li>The path matches a {@code public-paths} prefix AND the method is GET or HEAD.</li>
     * </ul>
     *
     * @param exchange the server web exchange
     * @return true if the request should bypass authentication
     */
    public boolean isPublicRequest(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();

        // Unauthenticated paths allow all HTTP methods (e.g., OTEL trace export via POST)
        for (String prefix : unauthenticatedPaths) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        // Public paths only allow GET and HEAD (UI bootstrap endpoints)
        HttpMethod method = exchange.getRequest().getMethod();
        if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
            return false;
        }

        for (String prefix : publicPaths) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
