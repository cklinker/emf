package com.emf.worker.module;

import com.emf.runtime.module.ModuleStore;
import com.emf.runtime.module.TenantModuleData;
import com.emf.runtime.workflow.ActionHandlerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuntimeModuleManagerTest {

    private ModuleStore moduleStore;
    private ActionHandlerRegistry actionHandlerRegistry;
    private RuntimeModuleManager manager;
    private ObjectMapper objectMapper;

    private static final String TENANT_ID = "tenant-1";
    private static final String MANIFEST_JSON = """
        {
          "id": "test-module",
          "name": "Test Module",
          "version": "1.0.0",
          "moduleClass": "com.test.TestModule",
          "actionHandlers": [
            { "key": "test:action1", "name": "Test Action 1", "category": "Test" },
            { "key": "test:action2", "name": "Test Action 2" }
          ]
        }
        """;

    @BeforeEach
    void setUp() {
        moduleStore = mock(ModuleStore.class);
        actionHandlerRegistry = new ActionHandlerRegistry();
        objectMapper = new ObjectMapper();
        manager = new RuntimeModuleManager(moduleStore, actionHandlerRegistry, objectMapper);
    }

    @Test
    void shouldInstallModule() {
        when(moduleStore.findByTenantAndModuleId(TENANT_ID, "test-module"))
            .thenReturn(Optional.empty());
        when(moduleStore.createModule(any())).thenReturn("mod-123");
        when(moduleStore.findById("mod-123")).thenReturn(Optional.of(createModuleData("mod-123")));

        TenantModuleData result = manager.installModule(
            TENANT_ID, MANIFEST_JSON, "https://example.com/module.jar",
            "sha256:abc", 1024L, "user-1");

        assertNotNull(result);
        verify(moduleStore).createModule(any());
        verify(moduleStore).createActions(argThat(actions -> actions.size() == 2));
    }

    @Test
    void shouldRejectDuplicateInstall() {
        when(moduleStore.findByTenantAndModuleId(TENANT_ID, "test-module"))
            .thenReturn(Optional.of(createModuleData("existing")));

        assertThrows(IllegalStateException.class, () ->
            manager.installModule(TENANT_ID, MANIFEST_JSON,
                "https://example.com/module.jar", "sha256:abc", 1024L, "user-1"));
    }

    @Test
    void shouldEnableModule() {
        TenantModuleData module = createModuleData("mod-123", TenantModuleData.STATUS_INSTALLED);
        when(moduleStore.findByTenantAndModuleId(TENANT_ID, "test-module"))
            .thenReturn(Optional.of(module));

        manager.enableModule(TENANT_ID, "test-module");

        verify(moduleStore).updateStatus("mod-123", TenantModuleData.STATUS_ACTIVE);
        assertTrue(manager.isLoaded(TENANT_ID, "test-module"));
        assertTrue(actionHandlerRegistry.getHandler(TENANT_ID, "test:action1").isPresent());
        assertTrue(actionHandlerRegistry.getHandler(TENANT_ID, "test:action2").isPresent());
    }

    @Test
    void shouldSkipEnableIfAlreadyActive() {
        TenantModuleData module = createModuleData("mod-123", TenantModuleData.STATUS_ACTIVE);
        when(moduleStore.findByTenantAndModuleId(TENANT_ID, "test-module"))
            .thenReturn(Optional.of(module));

        manager.enableModule(TENANT_ID, "test-module");

        verify(moduleStore, never()).updateStatus(any(), any());
    }

    @Test
    void shouldDisableModule() {
        TenantModuleData module = createModuleData("mod-123", TenantModuleData.STATUS_ACTIVE);
        when(moduleStore.findByTenantAndModuleId(TENANT_ID, "test-module"))
            .thenReturn(Optional.of(module));

        // First enable to load handlers
        manager.loadModule(TENANT_ID, module);
        assertTrue(manager.isLoaded(TENANT_ID, "test-module"));

        manager.disableModule(TENANT_ID, "test-module");

        verify(moduleStore).updateStatus("mod-123", TenantModuleData.STATUS_DISABLED);
        assertFalse(manager.isLoaded(TENANT_ID, "test-module"));
        assertFalse(actionHandlerRegistry.getHandler(TENANT_ID, "test:action1").isPresent());
    }

    @Test
    void shouldUninstallModule() {
        TenantModuleData module = createModuleData("mod-123");
        when(moduleStore.findByTenantAndModuleId(TENANT_ID, "test-module"))
            .thenReturn(Optional.of(module));

        manager.uninstallModule(TENANT_ID, "test-module");

        verify(moduleStore).deleteModule("mod-123");
    }

    @Test
    void shouldLoadAllActiveModulesOnStartup() {
        TenantModuleData module1 = createModuleData("mod-1", TenantModuleData.STATUS_ACTIVE);
        TenantModuleData module2 = new TenantModuleData(
            "mod-2", "tenant-2", "other-module", "Other Module", "1.0.0",
            null, "url", "checksum", null, "com.test.Other", MANIFEST_JSON,
            TenantModuleData.STATUS_ACTIVE, "system", Instant.now(), Instant.now(),
            List.of(new TenantModuleData.TenantModuleActionData(
                "a1", "mod-2", "other:action", "Other Action",
                null, null, null, null, null
            ))
        );

        when(moduleStore.findAllActive()).thenReturn(List.of(module1, module2));

        manager.loadAllActiveModules();

        assertTrue(manager.isLoaded(TENANT_ID, "test-module"));
        assertTrue(manager.isLoaded("tenant-2", "other-module"));
    }

    @Test
    void shouldHandleLoadFailureGracefully() {
        TenantModuleData badModule = new TenantModuleData(
            "mod-bad", TENANT_ID, "bad-module", "Bad Module", "1.0.0",
            null, "url", "checksum", null, "com.test.Bad", "invalid-json",
            TenantModuleData.STATUS_ACTIVE, "system", Instant.now(), Instant.now(),
            List.of() // No actions, but manifest JSON is broken — shouldn't matter for loading
        );

        when(moduleStore.findAllActive()).thenReturn(List.of(badModule));

        // Should not throw
        manager.loadAllActiveModules();

        // The module should still be considered loaded since it has no actions that would fail
        assertTrue(manager.isLoaded(TENANT_ID, "bad-module"));
    }

    @Test
    void shouldBeIdempotentOnLoad() {
        TenantModuleData module = createModuleData("mod-123");
        manager.loadModule(TENANT_ID, module);
        manager.loadModule(TENANT_ID, module); // Should not throw or duplicate

        assertTrue(manager.isLoaded(TENANT_ID, "test-module"));
    }

    @Test
    void shouldBeIdempotentOnUnload() {
        TenantModuleData module = createModuleData("mod-123");
        manager.unloadModule(TENANT_ID, module); // Not loaded — should not throw
        assertFalse(manager.isLoaded(TENANT_ID, "test-module"));
    }

    @Test
    void shouldThrowOnEnableNonexistentModule() {
        when(moduleStore.findByTenantAndModuleId(TENANT_ID, "nonexistent"))
            .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> manager.enableModule(TENANT_ID, "nonexistent"));
    }

    private TenantModuleData createModuleData(String id) {
        return createModuleData(id, TenantModuleData.STATUS_INSTALLED);
    }

    private TenantModuleData createModuleData(String id, String status) {
        return new TenantModuleData(
            id, TENANT_ID, "test-module", "Test Module", "1.0.0",
            "Test", "https://example.com/module.jar", "sha256:abc", 1024L,
            "com.test.TestModule", MANIFEST_JSON, status, "user-1",
            Instant.now(), Instant.now(),
            List.of(
                new TenantModuleData.TenantModuleActionData(
                    "a1", id, "test:action1", "Test Action 1",
                    "Test", null, null, null, null),
                new TenantModuleData.TenantModuleActionData(
                    "a2", id, "test:action2", "Test Action 2",
                    null, null, null, null, null)
            )
        );
    }
}
