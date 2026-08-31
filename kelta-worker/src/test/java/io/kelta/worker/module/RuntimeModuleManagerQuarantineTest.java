package io.kelta.worker.module;

import io.kelta.runtime.module.ModuleStore;
import io.kelta.runtime.module.TenantModuleData;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionHandler;
import io.kelta.runtime.workflow.ActionHandlerRegistry;
import io.kelta.runtime.workflow.ModuleUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A module that cannot run must not look like one that can.
 *
 * <p>Before this, a JAR that failed signature, checksum, or classloading fell back to handlers
 * returning {@code ActionResult.success({"status":"EXECUTED","mode":"stub"})} while the module
 * reported {@code ACTIVE}. A flow calling that action passed, and nothing anywhere said the work
 * had not happened — the worst available failure for a module doing something like taking payment.
 */
@DisplayName("RuntimeModuleManager — fail closed")
class RuntimeModuleManagerQuarantineTest {

    private static final String TENANT = "tenant-1";
    private static final String ROW_ID = "mod-row-1";

    @TempDir Path tempDir;

    private ModuleStore moduleStore;
    private ActionHandlerRegistry handlerRegistry;

    @BeforeEach
    void setUp() {
        moduleStore = mock(ModuleStore.class);
        handlerRegistry = new ActionHandlerRegistry();
    }

    private TenantModuleData moduleData(String s3Key, String checksum) {
        return new TenantModuleData(
            ROW_ID, TENANT, "test-module", "Test Module", "1.0.0", "desc",
            "https://example.com/m.jar", checksum, 10L,
            "io.kelta.worker.module.testmodule.ServiceProvidingTestModule", "{}",
            TenantModuleData.STATUS_ACTIVE, "user-1", Instant.now(), Instant.now(), s3Key,
            List.of(new TenantModuleData.TenantModuleActionData(
                "act-1", ROW_ID, "test:do-thing", "Do Thing", "Test", "does a thing",
                null, null, null)));
    }

    private RuntimeModuleManager manager(ModuleJarService jarService) {
        return new RuntimeModuleManager(moduleStore, handlerRegistry, new ObjectMapper(),
            jarService, null, null, null, null);
    }

    @Test
    @DisplayName("a JAR that fails to load quarantines: its action refuses instead of succeeding")
    void failedJarQuarantinesRatherThanStubbing() throws Exception {
        // A file that is not a JAR at all — stands in for any load failure (bad signature,
        // checksum mismatch, missing class).
        Path notAJar = tempDir.resolve("broken.jar");
        Files.writeString(notAJar, "this is not a jar");
        ModuleJarService jarService = mock(ModuleJarService.class);
        when(jarService.downloadJarToCache("s3/key.jar")).thenReturn(notAJar.toUri().toURL());

        manager(jarService).loadModule(TENANT, moduleData("s3/key.jar", "wrong-checksum"));

        ActionHandler handler = handlerRegistry.getHandler(TENANT, "test:do-thing").orElse(null);
        assertThat(handler).as("the action key stays registered, so the flow step is attributable")
                .isNotNull();

        // The whole point: this used to return success.
        assertThatThrownBy(() -> handler.execute(ActionContext.builder().tenantId(TENANT).build()))
                .isInstanceOf(ModuleUnavailableException.class)
                .hasMessageContaining("test-module")
                .hasMessageContaining("is not running");

        verify(moduleStore).recordLoadOutcome(eq(ROW_ID),
                eq(TenantModuleData.STATUS_QUARANTINED), any());
    }

    @Test
    @DisplayName("a manifest-only module quarantines rather than silently doing nothing")
    void manifestOnlyModuleQuarantines() {
        // No JAR means no implementation. Its declared actions previously returned success.
        manager(null).loadModule(TENANT, moduleData(null, null));

        ActionHandler handler = handlerRegistry.getHandler(TENANT, "test:do-thing").orElse(null);
        assertThatThrownBy(() -> handler.execute(ActionContext.builder().tenantId(TENANT).build()))
                .isInstanceOf(ModuleUnavailableException.class)
                .hasMessageContaining("no implementation uploaded");
    }

    @Test
    @DisplayName("stub mode is reachable only by explicit opt-in, and says so in the status")
    void stubModeIsOptInOnly() {
        RuntimeModuleManager manager = manager(null);
        manager.setStubModeEnabled(true);

        manager.loadModule(TENANT, moduleData(null, null));

        ActionHandler handler = handlerRegistry.getHandler(TENANT, "test:do-thing").orElse(null);
        var result = handler.execute(ActionContext.builder().tenantId(TENANT).build());
        assertThat(result.successful()).isTrue();
        assertThat(result.outputData()).containsEntry("mode", "stub");

        // Reported as STUB, never as ACTIVE — the status must not claim the module is working.
        verify(moduleStore).recordLoadOutcome(ROW_ID, TenantModuleData.STATUS_STUB, null);
        verify(moduleStore, never()).recordLoadOutcome(eq(ROW_ID),
                eq(TenantModuleData.STATUS_ACTIVE), isNull());
    }
}
