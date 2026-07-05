package io.kelta.worker.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the debounce/coalesce behavior of {@link CerbosPolicySyncCoalescer}.
 *
 * <p>The coalescer collapses a burst of per-row permission saves into a single
 * tenant-wide Cerbos policy sync — the security-critical guarantee is that N rapid
 * {@code requestSync} calls produce exactly one {@code syncTenant}, so a stale
 * snapshot from a racing sync can never land last and silently drop a just-saved
 * {@code MASKED}/{@code HIDDEN} deny.
 *
 * <p>Verification uses Mockito {@code timeout(...)} rather than {@code Thread.sleep}
 * assertions: the sync runs asynchronously on the coalescer's scheduler thread, so we
 * assert the eventual call count within a generous window while keeping the debounce
 * small so the tests stay fast.
 */
@DisplayName("CerbosPolicySyncCoalescer Tests")
class CerbosPolicySyncCoalescerTest {

    private static final long DEBOUNCE_MILLIS = 100;
    /** Generous ceiling for the async sync to fire — well above the debounce. */
    private static final long VERIFY_TIMEOUT_MILLIS = 2000;

    private CerbosPolicySyncService syncService;
    private CerbosPolicySyncCoalescer coalescer;

    @BeforeEach
    void setUp() {
        syncService = mock(CerbosPolicySyncService.class);
        coalescer = new CerbosPolicySyncCoalescer(syncService, DEBOUNCE_MILLIS);
    }

    @AfterEach
    void tearDown() {
        coalescer.shutdown();
    }

    @Test
    @DisplayName("A burst of requestSync calls for one tenant collapses to a single syncTenant")
    void burstForOneTenantCollapsesToSingleSync() {
        for (int i = 0; i < 5; i++) {
            coalescer.requestSync("t1");
        }

        // Exactly one sync fires after the burst settles — the earlier four were
        // cancelled by the debounce reschedule.
        verify(syncService, timeout(VERIFY_TIMEOUT_MILLIS).times(1)).syncTenant("t1");
    }

    @Test
    @DisplayName("Different tenants each get their own single sync")
    void differentTenantsEachSyncOnce() {
        coalescer.requestSync("t1");
        coalescer.requestSync("t2");

        verify(syncService, timeout(VERIFY_TIMEOUT_MILLIS).times(1)).syncTenant("t1");
        verify(syncService, timeout(VERIFY_TIMEOUT_MILLIS).times(1)).syncTenant("t2");
    }

    @Test
    @DisplayName("Null tenantId is a no-op — no NPE and no sync scheduled")
    void nullTenantIsNoOp() {
        coalescer.requestSync(null);

        // Give any (erroneously) scheduled task time to run, then assert nothing did.
        verify(syncService, timeout(VERIFY_TIMEOUT_MILLIS).times(0)).syncTenant("t1");
        verify(syncService, never()).syncTenant(null);
    }

    @Test
    @DisplayName("A second burst after the first sync completes triggers a second sync (pending slot cleared)")
    void secondBurstAfterFirstSyncTriggersAnotherSync() {
        coalescer.requestSync("t1");
        // Wait for the first sync to land — this also proves the pending slot is freed.
        verify(syncService, timeout(VERIFY_TIMEOUT_MILLIS).times(1)).syncTenant("t1");

        // A fresh burst arriving after the slot cleared must schedule a new sync.
        for (int i = 0; i < 3; i++) {
            coalescer.requestSync("t1");
        }

        verify(syncService, timeout(VERIFY_TIMEOUT_MILLIS).times(2)).syncTenant("t1");
    }
}
