package io.kelta.worker.module;

import io.kelta.runtime.module.ModuleStore;
import io.kelta.runtime.module.TenantModuleData;
import io.kelta.runtime.workflow.ModuleUnavailableException;
import io.kelta.runtime.workflow.ActionHandler;
import io.kelta.runtime.workflow.ActionHandlerRegistry;
import io.kelta.runtime.workflow.ActionResult;
import io.kelta.runtime.workflow.module.KeltaModule;
import io.kelta.runtime.workflow.module.ModuleContext;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

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
    void installWithJarRejectsBadSignatureBeforeUpload() {
        ModuleJarService jarService = mock(ModuleJarService.class);
        ModuleSignatureVerifier verifier = mock(ModuleSignatureVerifier.class);
        doThrow(new ModuleSignatureException("bad signature"))
                .when(verifier).verify(any(), any(), any());
        RuntimeModuleManager jarManager = new RuntimeModuleManager(
                moduleStore, actionHandlerRegistry, objectMapper, jarService, null, verifier);

        byte[] jar = "jar".getBytes();
        assertThrows(ModuleSignatureException.class, () ->
                jarManager.installModuleWithJar(TENANT_ID, MANIFEST_JSON, jar, "user", "sig"));

        // Rejected before any S3 upload or DB write.
        verify(jarService, never()).uploadJar(any(), any(), any(), any());
        verify(moduleStore, never()).createModule(any());
    }

    @Test
    void installWithJarProceedsWhenSignatureVerifies() {
        ModuleJarService jarService = mock(ModuleJarService.class);
        when(jarService.uploadJar(any(), any(), any(), any())).thenReturn("s3/key.jar");
        ModuleSignatureVerifier verifier = mock(ModuleSignatureVerifier.class);
        // The fingerprint of the key that verified — recorded against the module so a later key
        // rotation can tell what still depends on that key.
        when(verifier.verify(any(), any(), any())).thenReturn("fp-abc");
        when(moduleStore.findByTenantAndModuleId(any(), any())).thenReturn(Optional.empty());
        RuntimeModuleManager jarManager = new RuntimeModuleManager(
                moduleStore, actionHandlerRegistry, objectMapper, jarService, null, verifier);

        byte[] jar = "jar".getBytes();
        jarManager.installModuleWithJar(TENANT_ID, MANIFEST_JSON, jar, "user", "sig");

        // Verified against the INSTALLING TENANT's keys — a JAR signed for another tenant
        // must not be installable here.
        verify(verifier).verify(eq(TENANT_ID), eq(jar), eq("sig"));
        verify(jarService).uploadJar(any(), any(), any(), any());
        verify(moduleStore).createModule(any());
        // The verified signature is persisted so every load can re-verify the JAR, together with
        // the key that verified it.
        verify(moduleStore).saveJarSignature(any(), eq("sig"), eq("fp-abc"));
    }

    @Test
    void loadModuleChecksumMismatchRefusesRealCodeAndFallsBackToStubs() throws Exception {
        // Cached JAR bytes whose SHA-256 does NOT match the checksum persisted at install
        java.nio.file.Path jarFile = java.nio.file.Files.createTempFile("tampered-module", ".jar");
        java.nio.file.Files.write(jarFile, "tampered bytes".getBytes());
        ModuleJarService jarService = mock(ModuleJarService.class);
        when(jarService.downloadJarToCache("s3/key.jar")).thenReturn(jarFile.toUri().toURL());

        RuntimeModuleManager jarManager = new RuntimeModuleManager(
                moduleStore, actionHandlerRegistry, objectMapper, jarService, null, null);

        TenantModuleData module = createModuleDataWithS3Key("mod-1", "s3/key.jar");
        jarManager.loadModule(TENANT_ID, module); // checksum "sha256:abc" ≠ sha256(bytes)

        // No sandboxed classloader was created — only inert stubs registered
        assertTrue(actionHandlerRegistry.getHandler(TENANT_ID, "test:action1").isPresent(),
                "stub handlers must still register so the module surface stays visible");
        java.nio.file.Files.deleteIfExists(jarFile);
    }

    @Test
    void loadModuleReVerifiesStoredSignatureWhenVerifierEnabled() throws Exception {
        byte[] jarBytes = "real jar bytes".getBytes();
        java.nio.file.Path jarFile = java.nio.file.Files.createTempFile("signed-module", ".jar");
        java.nio.file.Files.write(jarFile, jarBytes);
        ModuleJarService jarService = mock(ModuleJarService.class);
        when(jarService.downloadJarToCache("s3/key.jar")).thenReturn(jarFile.toUri().toURL());
        ModuleSignatureVerifier verifier = mock(ModuleSignatureVerifier.class);
        when(verifier.isEnabledFor(TENANT_ID)).thenReturn(true);
        when(moduleStore.findJarSignature("mod-1")).thenReturn(Optional.of("stored-sig"));

        RuntimeModuleManager jarManager = new RuntimeModuleManager(
                moduleStore, actionHandlerRegistry, objectMapper, jarService, null, verifier);

        // Matching checksum so the signature step is reached
        TenantModuleData module = new TenantModuleData(
                "mod-1", TENANT_ID, "test-module", "Test Module", "1.0.0",
                "Test", "https://example.com/module.jar", ModuleJarService.sha256(jarBytes), 14L,
                "com.test.TestModule", MANIFEST_JSON, TenantModuleData.STATUS_ACTIVE, "user-1",
                Instant.now(), Instant.now(), "s3/key.jar", List.of());
        jarManager.loadModule(TENANT_ID, module);

        // The stored install-time signature was re-verified against the downloaded bytes
        verify(verifier).verify(eq(TENANT_ID), eq(jarBytes), eq("stored-sig"));
        java.nio.file.Files.deleteIfExists(jarFile);
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
            TenantModuleData.STATUS_ACTIVE, "system", Instant.now(), Instant.now(), null,
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
            TenantModuleData.STATUS_ACTIVE, "system", Instant.now(), Instant.now(), null,
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

    @Test
    void shouldReportJarLoadingDisabledWithoutJarService() {
        assertFalse(manager.isJarLoadingEnabled());
    }

    @Test
    void shouldQuarantineWhenModuleHasS3KeyButNoJarService() {
        // The module has a JAR but this pod cannot fetch it. That is a broken module, not a
        // working one: it used to register stubs that returned success, so the action reported
        // EXECUTED for work that never ran.
        TenantModuleData module = createModuleDataWithS3Key("mod-s3", "modules/t/m/v/checksum.jar");

        manager.loadModule(TENANT_ID, module);

        assertTrue(manager.isLoaded(TENANT_ID, "test-module"));
        // The action key stays registered, so the flow step names the module rather than failing
        // as ResourceNotFound, which reads as a mistyped key.
        assertTrue(actionHandlerRegistry.getHandler(TENANT_ID, "test:action1").isPresent());
        assertThrows(ModuleUnavailableException.class, () ->
            actionHandlerRegistry.getHandler(TENANT_ID, "test:action1").get().execute(null));
    }

    @Test
    void unloadByIdWorksAfterTheRowIsDeleted() {
        // The production sequence: uninstall deletes the row, THEN publishes UNINSTALLED, so every
        // pod except the one that served the request handles the event with no row to look up.
        // Unloading has to work from the id alone or the handlers stay registered forever.
        TenantModuleData module = createModuleDataWithS3Key("mod-s3", "modules/t/m/v/checksum.jar");
        manager.loadModule(TENANT_ID, module);
        assertTrue(actionHandlerRegistry.getHandler(TENANT_ID, "test:action1").isPresent());

        manager.unloadModule(TENANT_ID, "test-module");

        assertFalse(manager.isLoaded(TENANT_ID, "test-module"));
        assertTrue(actionHandlerRegistry.getHandler(TENANT_ID, "test:action1").isEmpty(),
                "handlers must be removed even though the module row is gone");
    }

    @Test
    void reinstallAfterUninstallRegistersHandlersAgain() {
        // The bug this pins. Uninstall-then-reinstall left loadedModules holding the id on every
        // pod that could not find the deleted row, so loadModule's "already loaded" early return
        // registered nothing — the module ran on one pod and was missing from the others, while
        // /api/modules reported ACTIVE and a flow step failed ResourceNotFound non-deterministically.
        TenantModuleData module = createModuleDataWithS3Key("mod-s3", "modules/t/m/v/checksum.jar");
        manager.loadModule(TENANT_ID, module);

        manager.unloadModule(TENANT_ID, "test-module");   // row already deleted upstream

        // The assertion that matters. If the unload silently no-ops, the OLD jar's handlers stay
        // registered and loadedModules keeps the id, so the reinstall below hits the "already
        // loaded" early return and the pod keeps serving the previous version forever — while
        // /api/modules reports ACTIVE and isLoaded() agrees.
        assertFalse(manager.isLoaded(TENANT_ID, "test-module"),
                "unload must clear the loaded marker or the reinstall is skipped");
        assertTrue(actionHandlerRegistry.getHandler(TENANT_ID, "test:action1").isEmpty(),
                "stale handlers from the previous version must not survive the unload");

        manager.loadModule(TENANT_ID, module);            // reinstall

        assertTrue(manager.isLoaded(TENANT_ID, "test-module"));
        assertTrue(actionHandlerRegistry.getHandler(TENANT_ID, "test:action1").isPresent(),
                "a reinstalled module must register its handlers again");
    }

    @Test
    void unloadByIdIsIdempotent() {
        assertDoesNotThrow(() -> manager.unloadModule(TENANT_ID, "never-loaded"));
        TenantModuleData module = createModuleDataWithS3Key("mod-s3", "modules/t/m/v/checksum.jar");
        manager.loadModule(TENANT_ID, module);
        manager.unloadModule(TENANT_ID, "test-module");
        assertDoesNotThrow(() -> manager.unloadModule(TENANT_ID, "test-module"));
    }

    private TenantModuleData createModuleData(String id) {
        return createModuleData(id, TenantModuleData.STATUS_INSTALLED);
    }

    private TenantModuleData createModuleData(String id, String status) {
        return new TenantModuleData(
            id, TENANT_ID, "test-module", "Test Module", "1.0.0",
            "Test", "https://example.com/module.jar", "sha256:abc", 1024L,
            "com.test.TestModule", MANIFEST_JSON, status, "user-1",
            Instant.now(), Instant.now(), null,
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

    private TenantModuleData createModuleDataWithS3Key(String id, String s3Key) {
        return new TenantModuleData(
            id, TENANT_ID, "test-module", "Test Module", "1.0.0",
            "Test", "https://example.com/module.jar", "sha256:abc", 1024L,
            "com.test.TestModule", MANIFEST_JSON, TenantModuleData.STATUS_ACTIVE, "user-1",
            Instant.now(), Instant.now(), s3Key,
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
