package io.kelta.worker.listener;

import io.kelta.worker.service.CerbosPolicySyncCoalescer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("FieldPermissionSyncHook")
class FieldPermissionSyncHookTest {

    private CerbosPolicySyncCoalescer syncCoalescer;
    private FieldPermissionSyncHook hook;

    @BeforeEach
    void setUp() {
        syncCoalescer = mock(CerbosPolicySyncCoalescer.class);
        hook = new FieldPermissionSyncHook(syncCoalescer);
    }

    @Test
    @DisplayName("Targets the profile-field-permissions collection")
    void targetsProfileFieldPermissions() {
        assertEquals("profile-field-permissions", hook.getCollectionName());
    }

    @Test
    @DisplayName("Runs after validation and audit hooks, like CerbosPolicySyncHook")
    void runsAtOrder100() {
        assertEquals(100, hook.getOrder());
    }

    @Test
    @DisplayName("afterCreate requests a coalesced tenant sync")
    void afterCreateRequestsSync() {
        hook.afterCreate(Map.of("fieldId", "field-ssn", "visibility", "MASKED"), "tenant-1");

        verify(syncCoalescer).requestSync("tenant-1");
    }

    @Test
    @DisplayName("afterUpdate requests a coalesced tenant sync")
    void afterUpdateRequestsSync() {
        hook.afterUpdate("perm-1",
                Map.of("visibility", "MASKED"), Map.of("visibility", "VISIBLE"), "tenant-2");

        verify(syncCoalescer).requestSync("tenant-2");
    }

    @Test
    @DisplayName("afterDelete requests a coalesced tenant sync")
    void afterDeleteRequestsSync() {
        hook.afterDelete("perm-1", "tenant-3");

        verify(syncCoalescer).requestSync("tenant-3");
    }
}
