package io.kelta.worker.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Debounces and serializes Cerbos policy syncs per tenant.
 *
 * <p>A per-field permission matrix is saved one row at a time — the admin UI fires
 * one PATCH per changed {@code profile-field-permissions} row in parallel, and the
 * bulk path loops per record. Calling {@link CerbosPolicySyncService#syncTenant}
 * directly from each hook invocation would (a) run N full tenant policy regenerations
 * per save — a self-inflicted DoS on a wide collection — and (b) let those N syncs
 * race: each reads DB state at its own moment and PUTs the entire policy to Cerbos
 * with no ordering, so a stale snapshot landing last silently drops a just-saved
 * {@code MASKED}/{@code HIDDEN} deny, leaving a field readable that should not be.
 *
 * <p>This coalescer collapses a burst into a single sync. Each {@link #requestSync}
 * (re)schedules the tenant's sync a short delay out on one shared single-threaded
 * scheduler; a new request cancels the pending one (debounce), so the sync runs once
 * after the burst settles and reads the final committed state. The single scheduler
 * thread also guarantees a tenant's syncs never overlap — the last read wins and the
 * PUT order matches read order.
 */
@Component
public class CerbosPolicySyncCoalescer {

    private static final Logger log = LoggerFactory.getLogger(CerbosPolicySyncCoalescer.class);

    private final CerbosPolicySyncService syncService;
    private final long debounceMillis;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public CerbosPolicySyncCoalescer(
            CerbosPolicySyncService syncService,
            @Value("${kelta.worker.cerbos.sync-debounce-millis:750}") long debounceMillis) {
        this.syncService = syncService;
        this.debounceMillis = debounceMillis;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cerbos-policy-sync-coalescer");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Requests a policy sync for {@code tenantId}, coalescing a burst of requests into
     * a single deferred sync. Returns immediately — the sync runs asynchronously on the
     * scheduler thread.
     */
    public void requestSync(String tenantId) {
        if (tenantId == null) {
            return;
        }
        // compute() serializes scheduling per key, so the cancel-and-reschedule is
        // atomic against concurrent requests for the same tenant.
        pending.compute(tenantId, (key, existing) -> {
            if (existing != null) {
                existing.cancel(false);
            }
            return scheduler.schedule(() -> runSync(key), debounceMillis, TimeUnit.MILLISECONDS);
        });
    }

    private void runSync(String tenantId) {
        // Clear our slot before syncing so a request arriving during the sync schedules
        // a fresh follow-up rather than being dropped.
        pending.remove(tenantId);
        try {
            syncService.syncTenant(tenantId);
        } catch (Exception e) {
            // syncTenant already logs + swallows internally; guard the scheduler thread
            // against anything that escapes so it survives for later tenants.
            log.error("Coalesced Cerbos policy sync failed for tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
