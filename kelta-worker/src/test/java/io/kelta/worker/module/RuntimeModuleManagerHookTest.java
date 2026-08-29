package io.kelta.worker.module;

import io.kelta.runtime.module.ModuleStore;
import io.kelta.runtime.module.TenantModuleData;
import io.kelta.runtime.workflow.ActionHandlerRegistry;
import io.kelta.runtime.workflow.BeforeSaveHookRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A runtime-loaded module's before-save hooks must reach the platform, fire only for the
 * installing tenant, and disappear again on unload.
 *
 * <p>Loads a real JAR through the real {@code SandboxedModuleClassLoader} rather than mocking the
 * module instance — the registration path only exists inside {@code loadFromJar}, and the failure
 * this guards against (a hook that outlives its module and keeps vetoing saves) is invisible to a
 * mocked load.
 */
@DisplayName("RuntimeModuleManager — module-provided before-save hooks")
class RuntimeModuleManagerHookTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String OTHER_TENANT = "tenant-2";
    private static final String MODULE_CLASS =
            "io.kelta.worker.module.testmodule.HookProvidingTestModule";

    private static final String MANIFEST_JSON = """
        {
          "id": "test-module",
          "name": "Hook Providing Test Module",
          "version": "1.0.0",
          "moduleClass": "io.kelta.worker.module.testmodule.HookProvidingTestModule"
        }
        """;

    @TempDir Path tempDir;

    private ModuleStore moduleStore;
    private BeforeSaveHookRegistry hookRegistry;
    private RuntimeModuleManager manager;
    private byte[] jarBytes;

    @BeforeEach
    void setUp() throws Exception {
        moduleStore = mock(ModuleStore.class);
        hookRegistry = new BeforeSaveHookRegistry();

        Path jarFile = buildModuleJar();
        jarBytes = java.nio.file.Files.readAllBytes(jarFile);

        ModuleJarService jarService = mock(ModuleJarService.class);
        when(jarService.downloadJarToCache("s3/key.jar")).thenReturn(jarFile.toUri().toURL());

        manager = new RuntimeModuleManager(moduleStore, new ActionHandlerRegistry(),
                new ObjectMapper(), jarService, null, null, hookRegistry, null);
    }

    @Test
    @DisplayName("Loading a module registers its hooks for the installing tenant only")
    void loadRegistersHooksScopedToTheTenant() {
        manager.loadModule(TENANT_ID, moduleData());

        assertThat(hookRegistry.hasHooks(TENANT_ID, "orders")).isTrue();
        assertThat(hookRegistry.evaluateBeforeCreate("orders", new HashMap<>(), TENANT_ID)
                .isSuccess()).isFalse();

        assertThat(hookRegistry.hasHooks(OTHER_TENANT, "orders")).isFalse();
        assertThat(hookRegistry.evaluateBeforeCreate("orders", new HashMap<>(), OTHER_TENANT)
                .isSuccess()).isTrue();
        // Nothing leaked into the platform-wide registry.
        assertThat(hookRegistry.hasHooks("orders")).isFalse();
    }

    @Test
    @DisplayName("Unloading removes the hooks the load registered")
    void unloadRemovesRegisteredHooks() {
        manager.loadModule(TENANT_ID, moduleData());
        manager.unloadModule(TENANT_ID, "test-module");

        // The module returns a fresh hook instance per call, so this only passes because unload
        // removes the instances that were actually registered.
        assertThat(hookRegistry.hasHooks(TENANT_ID, "orders")).isFalse();
        assertThat(hookRegistry.evaluateBeforeCreate("orders", new HashMap<>(), TENANT_ID)
                .isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Installing provisions the manifest's collections")
    void installProvisionsDeclaredCollections() {
        ModuleCollectionProvisioner provisioner = mock(ModuleCollectionProvisioner.class);
        ModuleJarService jarService = mock(ModuleJarService.class);
        when(jarService.uploadJar(any(), any(), any(), any())).thenReturn("s3/key.jar");
        when(moduleStore.findByTenantAndModuleId(any(), any())).thenReturn(Optional.empty());

        RuntimeModuleManager provisioningManager = new RuntimeModuleManager(
                moduleStore, new ActionHandlerRegistry(), new ObjectMapper(), jarService,
                null, null, hookRegistry, provisioner);

        String manifestWithCollections = """
            {
              "id": "test-module",
              "name": "Hook Providing Test Module",
              "version": "1.0.0",
              "moduleClass": "%s",
              "collections": [
                { "name": "invoices", "fields": [ { "name": "reference", "type": "STRING" } ] }
              ]
            }
            """.formatted(MODULE_CLASS);

        provisioningManager.installModuleWithJar(
                TENANT_ID, manifestWithCollections, jarBytes, "user-1");

        verify(provisioner).provision(eq(TENANT_ID), argThat(collections ->
                collections.size() == 1 && "invoices".equals(collections.get(0).name())));
    }

    private TenantModuleData moduleData() {
        return new TenantModuleData(
                "mod-1", TENANT_ID, "test-module", "Hook Providing Test Module", "1.0.0",
                "Test", "https://example.com/module.jar", ModuleJarService.sha256(jarBytes),
                (long) jarBytes.length, MODULE_CLASS, MANIFEST_JSON,
                TenantModuleData.STATUS_ACTIVE, "user-1",
                Instant.now(), Instant.now(), "s3/key.jar", List.of());
    }

    /**
     * Packages the test module's compiled class into a JAR. Its package is outside the sandboxed
     * classloader's parent allowlist, so it must resolve from these bytes.
     */
    private Path buildModuleJar() throws IOException {
        Path jarFile = tempDir.resolve("test-module.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        String rootResource = MODULE_CLASS.replace('.', '/') + ".class";
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarFile.toFile()),
                manifest)) {
            addClass(jar, rootResource);
            // The module's hook is an anonymous inner class; without it the load fails at
            // getBeforeSaveHooks() rather than at class resolution, which would be a confusing
            // way for this test to break.
            addClass(jar, MODULE_CLASS.replace('.', '/') + "$1.class");
        }
        return jarFile;
    }

    private void addClass(JarOutputStream jar, String resource) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Test module class not on the test classpath: " + resource);
            }
            jar.putNextEntry(new JarEntry(resource));
            in.transferTo(jar);
            jar.closeEntry();
        }
    }
}
