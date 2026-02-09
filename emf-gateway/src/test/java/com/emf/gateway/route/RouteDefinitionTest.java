package com.emf.gateway.route;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RouteDefinition class.
 */
class RouteDefinitionTest {

    @Test
    void testConstructorWithAllFields() {
        RateLimitConfig rateLimit = new RateLimitConfig(100, Duration.ofMinutes(1));
        RouteDefinition route = new RouteDefinition(
            "users-collection",
            "/api/users/**",
            "http://user-service:8080",
            "users",
            rateLimit
        );

        assertEquals("users-collection", route.getId());
        assertEquals("/api/users/**", route.getPath());
        assertEquals("http://user-service:8080", route.getBackendUrl());
        assertEquals("users", route.getCollectionName());
        assertEquals(rateLimit, route.getRateLimit());
        assertTrue(route.hasRateLimit());
    }

    @Test
    void testConstructorWithoutRateLimit() {
        RouteDefinition route = new RouteDefinition(
            "posts-collection",
            "/api/posts/**",
            "http://post-service:8080",
            "posts"
        );

        assertEquals("posts-collection", route.getId());
        assertEquals("/api/posts/**", route.getPath());
        assertEquals("http://post-service:8080", route.getBackendUrl());
        assertEquals("posts", route.getCollectionName());
        assertNull(route.getRateLimit());
        assertFalse(route.hasRateLimit());
    }

    @Test
    void testEquality() {
        RateLimitConfig rateLimit = new RateLimitConfig(100, Duration.ofMinutes(1));
        RouteDefinition route1 = new RouteDefinition(
            "users-collection",
            "/api/users/**",
            "http://user-service:8080",
            "users",
            rateLimit
        );

        RouteDefinition route2 = new RouteDefinition(
            "users-collection",
            "/api/users/**",
            "http://user-service:8080",
            "users",
            rateLimit
        );

        assertEquals(route1, route2);
        assertEquals(route1.hashCode(), route2.hashCode());
    }

    @Test
    void testInequality() {
        RouteDefinition route1 = new RouteDefinition(
            "users-collection",
            "/api/users/**",
            "http://user-service:8080",
            "users"
        );

        RouteDefinition route2 = new RouteDefinition(
            "posts-collection",
            "/api/posts/**",
            "http://post-service:8080",
            "posts"
        );

        assertNotEquals(route1, route2);
    }

    @Test
    void testToString() {
        RouteDefinition route = new RouteDefinition(
            "users-collection",
            "/api/users/**",
            "http://user-service:8080",
            "users"
        );

        String str = route.toString();
        assertTrue(str.contains("users-collection"));
        assertTrue(str.contains("/api/users/**"));
        assertTrue(str.contains("http://user-service:8080"));
        assertTrue(str.contains("users"));
    }
}
