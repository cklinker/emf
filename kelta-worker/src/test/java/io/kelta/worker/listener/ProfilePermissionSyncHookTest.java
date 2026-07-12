package io.kelta.worker.listener;

import io.kelta.worker.service.CerbosPolicySyncCoalescer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("ProfilePermissionSyncHook")
class ProfilePermissionSyncHookTest {

    private CerbosPolicySyncCoalescer syncCoalescer;

    @BeforeEach
    void setUp() {
        syncCoalescer = mock(CerbosPolicySyncCoalescer.class);
    }

    @Test
    @DisplayName("Targets exactly the collection it was registered for")
    void targetsRegisteredCollection() {
        assertEquals("profile-object-permissions",
                new ProfilePermissionSyncHook("profile-object-permissions", syncCoalescer)
                        .getCollectionName());
        assertEquals("profile-system-permissions",
                new ProfilePermissionSyncHook("profile-system-permissions", syncCoalescer)
                        .getCollectionName());
    }

    @Test
    @DisplayName("Rejects collections it does not own")
    void rejectsUnknownCollection() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProfilePermissionSyncHook("profiles", syncCoalescer));
    }

    @Test
    @DisplayName("Runs after validation and audit hooks, like CerbosPolicySyncHook")
    void runsAtOrder100() {
        assertEquals(100,
                new ProfilePermissionSyncHook("profile-object-permissions", syncCoalescer)
                        .getOrder());
    }

    @Test
    @DisplayName("afterCreate requests a coalesced tenant sync (grant path)")
    void afterCreateRequestsSync() {
        var hook = new ProfilePermissionSyncHook("profile-object-permissions", syncCoalescer);

        hook.afterCreate(Map.of(
                "profileId", "profile-1", "collectionId", "col-1",
                "canRead", true, "canViewAll", true), "tenant-1");

        verify(syncCoalescer).requestSync("tenant-1");
    }

    @Test
    @DisplayName("afterUpdate requests a coalesced tenant sync")
    void afterUpdateRequestsSync() {
        var hook = new ProfilePermissionSyncHook("profile-system-permissions", syncCoalescer);

        hook.afterUpdate("perm-1",
                Map.of("permissionName", "API_ACCESS", "granted", true),
                Map.of("permissionName", "API_ACCESS", "granted", false), "tenant-2");

        verify(syncCoalescer).requestSync("tenant-2");
    }

    @Test
    @DisplayName("afterDelete requests a coalesced tenant sync (revocation path — 2026-07-12: deletes left grants live in Cerbos)")
    void afterDeleteRequestsSync() {
        var hook = new ProfilePermissionSyncHook("profile-object-permissions", syncCoalescer);

        hook.afterDelete("perm-1", "tenant-3");

        verify(syncCoalescer).requestSync("tenant-3");
    }
}
