package com.emf.gateway.ratelimit;

import com.emf.gateway.config.GovernorLimitConfig;
import com.emf.gateway.route.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TenantGovernorLimitCache.
 */
class TenantGovernorLimitCacheTest {

    private TenantGovernorLimitCache cache;

    @BeforeEach
    void setUp() {
        cache = new TenantGovernorLimitCache();
    }

    @Test
    void testLoadFromBootstrap() {
        // Given
        Map<String, GovernorLimitConfig> limits = new HashMap<>();
        limits.put("tenant-1", new GovernorLimitConfig(100_000));
        limits.put("tenant-2", new GovernorLimitConfig(50_000));

        // When
        cache.loadFromBootstrap(limits);

        // Then
        assertEquals(2, cache.size());
        assertEquals(Optional.of(100_000), cache.getApiCallsPerDay("tenant-1"));
        assertEquals(Optional.of(50_000), cache.getApiCallsPerDay("tenant-2"));
    }

    @Test
    void testLoadFromBootstrap_NullMap() {
        // When
        cache.loadFromBootstrap(null);

        // Then
        assertEquals(0, cache.size());
    }

    @Test
    void testLoadFromBootstrap_EmptyMap() {
        // When
        cache.loadFromBootstrap(new HashMap<>());

        // Then
        assertEquals(0, cache.size());
    }

    @Test
    void testUpdateTenantLimit() {
        // When
        cache.updateTenantLimit("tenant-1", 200_000);

        // Then
        assertEquals(Optional.of(200_000), cache.getApiCallsPerDay("tenant-1"));
    }

    @Test
    void testGetRateLimitForTenant_Known() {
        // Given
        cache.updateTenantLimit("tenant-1", 144_000); // 144,000 / 1440 = exactly 100 per minute

        // When
        RateLimitConfig config = cache.getRateLimitForTenant("tenant-1");

        // Then
        assertEquals(100, config.getRequestsPerWindow());
        assertEquals(Duration.ofMinutes(1), config.getWindowDuration());
    }

    @Test
    void testGetRateLimitForTenant_Unknown_UsesDefault() {
        // When - tenant not in cache, should use default (100,000/day)
        RateLimitConfig config = cache.getRateLimitForTenant("unknown-tenant");

        // Then - 100,000 / 1440 = 69 per minute
        assertEquals(69, config.getRequestsPerWindow());
        assertEquals(Duration.ofMinutes(1), config.getWindowDuration());
    }

    @Test
    void testGetRateLimitForTenant_MinimumOnePerWindow() {
        // Given - very low limit: 1 per day
        cache.updateTenantLimit("low-tenant", 1);

        // When
        RateLimitConfig config = cache.getRateLimitForTenant("low-tenant");

        // Then - minimum 1 request per window
        assertEquals(1, config.getRequestsPerWindow());
    }

    @Test
    void testGetApiCallsPerDay_NotFound() {
        // When
        Optional<Integer> result = cache.getApiCallsPerDay("nonexistent");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void testLoadFromBootstrap_ClearsExisting() {
        // Given - pre-populate cache
        cache.updateTenantLimit("old-tenant", 50_000);
        assertEquals(1, cache.size());

        // When - load new data
        Map<String, GovernorLimitConfig> limits = new HashMap<>();
        limits.put("new-tenant", new GovernorLimitConfig(100_000));
        cache.loadFromBootstrap(limits);

        // Then - old data cleared, new data loaded
        assertEquals(1, cache.size());
        assertTrue(cache.getApiCallsPerDay("old-tenant").isEmpty());
        assertEquals(Optional.of(100_000), cache.getApiCallsPerDay("new-tenant"));
    }
}
