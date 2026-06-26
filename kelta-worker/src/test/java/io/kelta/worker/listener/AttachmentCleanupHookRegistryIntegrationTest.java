package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveHookRegistry;
import io.kelta.worker.service.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Integration test verifying {@link AttachmentCleanupHook} is dispatched by a real
 * {@link BeforeSaveHookRegistry} as a wildcard after-delete hook.
 *
 * <p>This exercises the wiring that makes attachment cascade-cleanup fire whenever
 * any record is deleted: the worker delete path calls
 * {@link BeforeSaveHookRegistry#invokeAfterDelete}, which must route to the wildcard
 * hook. Unit-level behavior is covered separately in {@code AttachmentCleanupHookTest};
 * this test covers the registration + dispatch contract.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentCleanupHookRegistryIntegrationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private S3StorageService storageService;

    private BeforeSaveHookRegistry registry;

    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        registry = new BeforeSaveHookRegistry();
        registry.register(new AttachmentCleanupHook(jdbcTemplate, storageService));
    }

    @Test
    void registeredAsWildcardHookForEveryCollection() {
        assertTrue(registry.hasHooks("orders"));
        assertTrue(registry.hasHooks("any-other-collection"));
    }

    @Test
    void recordDelete_dispatchesCascadeCleanupThroughRegistry() {
        when(jdbcTemplate.queryForList(anyString(), eq("rec-1"), eq(TENANT_ID)))
                .thenReturn(List.of(Map.of("id", "att-1", "storage_key", "k1")));
        when(storageService.isEnabled()).thenReturn(true);
        when(jdbcTemplate.update(anyString(), eq("rec-1"), eq(TENANT_ID))).thenReturn(1);

        // Simulate the worker delete path firing after-delete hooks for a user collection.
        registry.invokeAfterDelete("orders", "rec-1", TENANT_ID);

        verify(storageService).deleteObject("k1");
        verify(jdbcTemplate).update(contains("DELETE FROM file_attachment"), eq("rec-1"), eq(TENANT_ID));
    }

    @Test
    void attachmentSelfDelete_isNotCascadedByHook() {
        registry.invokeAfterDelete("attachments", "att-1", TENANT_ID);

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(storageService);
    }
}
