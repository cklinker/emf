package io.kelta.gateway;

import io.kelta.gateway.auth.GatewayPrincipal;
import io.kelta.gateway.route.RateLimitConfig;
import io.kelta.gateway.route.RouteDefinition;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Pre-built domain objects for tests. Use these instead of constructing
 * objects manually so tests stay concise and don't break when constructors change.
 */
public final class TestFixtures {

    public static final String TENANT_ID = "tenant-1";
    public static final String USER_ID = "user-1";
    public static final String BACKEND_URL = "http://localhost:8080";

    private TestFixtures() {}

    public static RouteDefinition routeDefinition() {
        return new RouteDefinition("route-1", "/api/customers/**", BACKEND_URL, "customers");
    }

    public static RouteDefinition routeDefinition(String collectionName) {
        return new RouteDefinition("route-" + collectionName,
                "/api/" + collectionName + "/**", BACKEND_URL, collectionName);
    }

    public static RouteDefinition routeDefinitionWithRateLimit() {
        return new RouteDefinition("route-1", "/api/customers/**", BACKEND_URL, "customers",
                rateLimitConfig());
    }

    public static RateLimitConfig rateLimitConfig() {
        return new RateLimitConfig(100, Duration.ofMinutes(1));
    }

    public static RateLimitConfig rateLimitConfig(int requestsPerWindow, Duration window) {
        return new RateLimitConfig(requestsPerWindow, window);
    }

    public static GatewayPrincipal principal() {
        return new GatewayPrincipal("user@example.com", List.of("users"), Map.of(
                "sub", USER_ID,
                "tenant_id", TENANT_ID
        ));
    }

    public static GatewayPrincipal principal(String tenantId) {
        return new GatewayPrincipal("user@example.com", List.of("users"),
                Map.of("sub", USER_ID, "tenant_id", tenantId),
                "profile-1", "Test User", tenantId, null, null);
    }

    public static GatewayPrincipal connectedAppPrincipal(String appId, String scopes) {
        return new GatewayPrincipal("app-" + appId, Collections.emptyList(),
                Map.of("sub", appId, "tenant_id", TENANT_ID),
                null, null, TENANT_ID, appId, scopes);
    }
}
