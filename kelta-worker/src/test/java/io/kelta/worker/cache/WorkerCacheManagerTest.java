package io.kelta.worker.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerCacheManagerTest {

    private WorkerCacheManager cacheManager;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cacheManager = new WorkerCacheManager(meterRegistry);
    }

    // ── Custom Domain Cache ──────────────────────────────────────────────

    @Test
    void getCustomDomain_returnsEmptyWhenNotCached() {
        assertThat(cacheManager.getCustomDomain("app.acme.com")).isEmpty();
    }

    @Test
    void putAndGetCustomDomain_returnsCachedValue() {
        cacheManager.putCustomDomain("app.acme.com", "acme");

        Optional<String> result = cacheManager.getCustomDomain("app.acme.com");
        assertThat(result).isPresent().contains("acme");
    }

    @Test
    void evictCustomDomain_removesCachedEntry() {
        cacheManager.putCustomDomain("app.acme.com", "acme");
        cacheManager.evictCustomDomain("app.acme.com");

        assertThat(cacheManager.getCustomDomain("app.acme.com")).isEmpty();
    }

    @Test
    void evictAllCustomDomains_clearsAllEntries() {
        cacheManager.putCustomDomain("app.acme.com", "acme");
        cacheManager.putCustomDomain("app.beta.com", "beta");
        cacheManager.evictAllCustomDomains();

        assertThat(cacheManager.getCustomDomain("app.acme.com")).isEmpty();
        assertThat(cacheManager.getCustomDomain("app.beta.com")).isEmpty();
    }

    // ── User Permissions Cache ───────────────────────────────────────────

    @Test
    void getPermissions_returnsEmptyWhenNotCached() {
        assertThat(cacheManager.getPermissions("profile-123")).isEmpty();
    }

    @Test
    void putAndGetPermissions_returnsCachedValue() {
        Map<String, Object> permissions = Map.of(
                "systemPermissions", Map.of("manage_users", true),
                "objectPermissions", Map.of(),
                "fieldPermissions", Map.of()
        );

        cacheManager.putPermissions("profile-123", permissions);

        Optional<Map<String, Object>> result = cacheManager.getPermissions("profile-123");
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey("systemPermissions");
    }

    @Test
    void evictPermissions_removesCachedEntry() {
        cacheManager.putPermissions("profile-123", Map.of("systemPermissions", Map.of()));
        cacheManager.evictPermissions("profile-123");

        assertThat(cacheManager.getPermissions("profile-123")).isEmpty();
    }

    @Test
    void evictAllPermissions_clearsAllEntries() {
        cacheManager.putPermissions("profile-1", Map.of("systemPermissions", Map.of()));
        cacheManager.putPermissions("profile-2", Map.of("systemPermissions", Map.of()));
        cacheManager.evictAllPermissions();

        assertThat(cacheManager.getPermissions("profile-1")).isEmpty();
        assertThat(cacheManager.getPermissions("profile-2")).isEmpty();
    }

    // ── Tenant Limits Cache ──────────────────────────────────────────────

    @Test
    void getTenantLimits_returnsEmptyWhenNotCached() {
        assertThat(cacheManager.getTenantLimits("tenant-abc")).isEmpty();
    }

    @Test
    void putAndGetTenantLimits_returnsCachedValue() {
        Map<String, Object> limits = Map.of(
                "apiCallsPerDay", 50000,
                "maxUsers", 200
        );

        cacheManager.putTenantLimits("tenant-abc", limits);

        Optional<Map<String, Object>> result = cacheManager.getTenantLimits("tenant-abc");
        assertThat(result).isPresent();
        assertThat(result.get()).containsEntry("apiCallsPerDay", 50000);
    }

    @Test
    void evictTenantLimits_removesCachedEntry() {
        cacheManager.putTenantLimits("tenant-abc", Map.of("apiCallsPerDay", 50000));
        cacheManager.evictTenantLimits("tenant-abc");

        assertThat(cacheManager.getTenantLimits("tenant-abc")).isEmpty();
    }

    // ── Metrics ──────────────────────────────────────────────────────────

    @Test
    void metricsAreRegistered() {
        assertThat(meterRegistry.find("worker.cache.size.custom-domain").gauge()).isNotNull();
        assertThat(meterRegistry.find("worker.cache.size.permissions").gauge()).isNotNull();
        assertThat(meterRegistry.find("worker.cache.size.tenant-limits").gauge()).isNotNull();
    }

    // ── Diagnostics ──────────────────────────────────────────────────────

    @Test
    void getCacheSummary_returnsFormattedString() {
        String summary = cacheManager.getCacheSummary();
        assertThat(summary).startsWith("WorkerCaches[");
    }
}
