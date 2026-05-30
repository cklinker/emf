package io.kelta.worker.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantConcurrencyLimiter")
class TenantConcurrencyLimiterTest {

    private MeterRegistry registry;
    private TenantConcurrencyLimiter limiter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        limiter = new TenantConcurrencyLimiter(3, registry);
    }

    @Test
    @DisplayName("allows up to the default permit count per tenant")
    void allowsUpToLimit() {
        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isFalse();
    }

    @Test
    @DisplayName("release restores a permit for the same tenant")
    void releaseRestoresPermit() {
        limiter.tryAcquire("tenant-1");
        limiter.tryAcquire("tenant-1");
        limiter.tryAcquire("tenant-1");
        assertThat(limiter.tryAcquire("tenant-1")).isFalse();

        limiter.release("tenant-1");

        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
    }

    @Test
    @DisplayName("tenants are isolated — one tenant at-limit does not block another")
    void tenantsIsolated() {
        limiter.tryAcquire("tenant-1");
        limiter.tryAcquire("tenant-1");
        limiter.tryAcquire("tenant-1");

        assertThat(limiter.tryAcquire("tenant-2")).isTrue();
        assertThat(limiter.tryAcquire("tenant-2")).isTrue();
    }

    @Test
    @DisplayName("rejection increments the per-tenant counter")
    void rejectionCounted() {
        limiter.tryAcquire("tenant-1");
        limiter.tryAcquire("tenant-1");
        limiter.tryAcquire("tenant-1");
        limiter.tryAcquire("tenant-1");
        limiter.tryAcquire("tenant-1");

        assertThat(registry.find("kelta.worker.tenant.concurrency.rejected")
                .tag("tenant", "tenant-1")
                .counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("null/blank tenant always allowed and never tracked")
    void blankTenantAllowed() {
        assertThat(limiter.tryAcquire(null)).isTrue();
        assertThat(limiter.tryAcquire("")).isTrue();
        assertThat(limiter.tryAcquire("   ")).isTrue();

        assertThat(registry.find("kelta.worker.tenant.concurrency.rejected").counter()).isNull();
    }

    @Test
    @DisplayName("release on unknown tenant is a no-op")
    void releaseUnknownTenant() {
        limiter.release("never-seen");
        // No exception, no permit change for any other tenant
        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
    }

    @Test
    @DisplayName("inUsePermits reflects current acquires")
    void inUsePermitsReflection() {
        assertThat(limiter.inUsePermits("tenant-1")).isZero();
        limiter.tryAcquire("tenant-1");
        limiter.tryAcquire("tenant-1");
        assertThat(limiter.inUsePermits("tenant-1")).isEqualTo(2);
        limiter.release("tenant-1");
        assertThat(limiter.inUsePermits("tenant-1")).isEqualTo(1);
    }
}
