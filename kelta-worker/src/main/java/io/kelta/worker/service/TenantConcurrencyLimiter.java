package io.kelta.worker.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Per-tenant request concurrency limiter for the worker. Bounds the number of
 * concurrent in-flight requests per tenant per pod so a single noisy tenant
 * cannot saturate the HikariCP pool and starve every other tenant.
 *
 * <p>Default cap of 10 per tenant per pod is intentionally generous for SMB
 * tenants; tune via {@code kelta.worker.tenant-concurrency-limit}. Semaphores
 * are created lazily on first request for a tenant and never expire — the
 * memory footprint per tenant is on the order of bytes, so this is fine even
 * for thousands of active tenants per pod.
 *
 * <p>{@link #tryAcquire(String)} returns {@code true} immediately if a permit
 * is available; {@code false} otherwise. Callers must call {@link #release(String)}
 * exactly once for each successful acquire, regardless of how the request ends.
 */
@Service
public class TenantConcurrencyLimiter {

    private final ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();
    private final int defaultPermits;
    private final MeterRegistry meterRegistry;

    public TenantConcurrencyLimiter(
            @Value("${kelta.worker.tenant-concurrency-limit:10}") int defaultPermits,
            MeterRegistry meterRegistry) {
        this.defaultPermits = Math.max(1, defaultPermits);
        this.meterRegistry = meterRegistry;
    }

    public boolean tryAcquire(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return true;
        }
        Semaphore sem = semaphoreFor(tenantId);
        boolean acquired = sem.tryAcquire();
        if (!acquired) {
            counter("kelta.worker.tenant.concurrency.rejected", tenantId).increment();
        }
        return acquired;
    }

    public void release(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        Semaphore sem = semaphores.get(tenantId);
        if (sem != null) {
            sem.release();
        }
    }

    /** Returns currently in-use permits for a tenant; for tests + diagnostics. */
    public int inUsePermits(String tenantId) {
        Semaphore sem = semaphores.get(tenantId);
        return sem == null ? 0 : defaultPermits - sem.availablePermits();
    }

    private Semaphore semaphoreFor(String tenantId) {
        return semaphores.computeIfAbsent(tenantId, k -> new Semaphore(defaultPermits, true));
    }

    private Counter counter(String name, String tenantId) {
        // Tenant tag is bounded only by tenant count; capped via a single-counter
        // sample per tenant. For very high tenant counts, switch to a global
        // counter with no tag and rely on rejection logs for per-tenant context.
        Tags tags = Tags.of("tenant", tenantId);
        return Counter.builder(name)
                .description("Worker requests rejected because the per-tenant concurrency limit was reached")
                .tags(tags)
                .register(meterRegistry);
    }

    /** Per-pod default permits, exposed for tests + config diagnostics. */
    public int defaultPermits() {
        return defaultPermits;
    }

    public Map<String, Semaphore> semaphoreSnapshot() {
        return Map.copyOf(semaphores);
    }
}
