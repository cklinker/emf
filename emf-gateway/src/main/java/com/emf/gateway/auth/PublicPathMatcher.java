package com.emf.gateway.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

/**
 * Determines whether a request targets a public (unauthenticated) path.
 * Only GET and HEAD methods are considered public; mutating operations
 * always require authentication.
 *
 * <p>Public paths are configured via {@code emf.gateway.security.public-paths}
 * in application.yml as a comma-separated list of path prefixes.
 */
@Component
public class PublicPathMatcher {

    private final List<String> publicPaths;

    public PublicPathMatcher(
            @Value("${emf.gateway.security.public-paths:}") List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    /**
     * Returns true if this request targets a public path with a read-only method.
     *
     * @param exchange the server web exchange
     * @return true if the request is to a public path with GET or HEAD method
     */
    public boolean isPublicRequest(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
            return false;
        }

        String path = exchange.getRequest().getPath().value();
        for (String prefix : publicPaths) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
