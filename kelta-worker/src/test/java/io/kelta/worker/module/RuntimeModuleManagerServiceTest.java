package io.kelta.worker.module;

import io.kelta.runtime.module.ModuleStore;
import io.kelta.runtime.module.TenantModuleData;
import io.kelta.runtime.module.service.CountingPort;
import io.kelta.runtime.module.service.GreetingPort;
import io.kelta.runtime.module.service.ModuleServiceRegistry;
import io.kelta.runtime.workflow.ActionHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A runtime-loaded module can publish a service that platform code calls inline, and it disappears
 * again on unload.
 *
 * <p>This is the direction {@code ModuleContext} does not cover. Handlers and hooks only let a
 * module react to a dispatch; a plain Spring bean that needs an answer from a module — the case
 * that blocks moving entitlement resolution out of the worker — has no way to reach one, because
 * module classes live behind {@code SandboxedModuleClassLoader}.
 *
 * <p>Loads a real JAR through the real sandboxed classloader rather than mocking the module: the
 * registration path only exists inside {@code loadFromJar}, and the failure that matters here —
 * the platform resolving a service whose ClassLoader has since been closed — cannot happen in a
 * mocked load.
 */
@DisplayName("RuntimeModuleManager — module-published services")
class RuntimeModuleManagerServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String OTHER_TENANT = "tenant-2";
    private static final String MODULE_CLASS =
            "io.kelta.worker.module.testmodule.ServiceProvidingTestModule";

    private static final String TWO_SERVICE_MODULE_CLASS =
            "io.kelta.worker.module.testmodule.TwoServiceTestModule";

    private static final String TWO_SERVICE_MANIFEST_JSON = """
        {
          "id": "two-service-module",
          "name": "Two Service Test Module",
          "version": "1.0.0",
          "moduleClass": "io.kelta.worker.module.testmodule.TwoServiceTestModule"
        }
        """;

    private static final String MANIFEST_JSON = """
        {
          "id": "test-module",
          "name": "Service Providing Test Module",
          "version": "1.0.0",
          "moduleClass": "io.kelta.worker.module.testmodule.ServiceProvidingTestModule"
        }
        """;

    @TempDir Path tempDir;

    private ModuleStore moduleStore;
    private ModuleServiceRegistry serviceRegistry;
    private RuntimeModuleManager manager;
    private byte[] jarBytes;

    @BeforeEach
    void setUp() throws Exception {
        moduleStore = mock(ModuleStore.class);
        serviceRegistry = new ModuleServiceRegistry();

        Path jarFile = buildModuleJar();
        jarBytes = java.nio.file.Files.readAllBytes(jarFile);

        ModuleJarService jarService = mock(ModuleJarService.class);
        when(jarService.downloadJarToCache("s3/key.jar")).thenReturn(jarFile.toUri().toURL());

        manager = new RuntimeModuleManager(moduleStore, new ActionHandlerRegistry(),
                new ObjectMapper(), jarService, null, null, null, null, null, serviceRegistry);
    }

    @Test
    @DisplayName("platform code can call a service the module published, for that tenant only")
    void loadPublishesServiceScopedToTheTenant() {
        manager.loadModule(TENANT_ID, moduleData());

        // Resolved the way a platform bean would: by port, per call, with no compile-time
        // knowledge of the module.
        assertThat(serviceRegistry.find(TENANT_ID, GreetingPort.class))
                .map(port -> port.greet("craig"))
                .contains("hello craig, from the module");

        // A module installed by one tenant must never answer for another.
        assertThat(serviceRegistry.find(OTHER_TENANT, GreetingPort.class)).isEmpty();
    }

    @Test
    @DisplayName("the port crosses the sandbox boundary as the platform's own class")
    void portIsThePlatformClass() {
        manager.loadModule(TENANT_ID, moduleData());

        Object published = serviceRegistry.find(TENANT_ID, GreetingPort.class).orElseThrow();

        // The implementation comes from the JAR's ClassLoader, but satisfies the platform's
        // interface — if the module had bundled its own copy of GreetingPort, registration would
        // have been rejected rather than failing later as a ClassCastException.
        assertThat(published).isInstanceOf(GreetingPort.class);
        assertThat(published.getClass().getClassLoader())
                .isInstanceOf(SandboxedModuleClassLoader.class);
        assertThat(GreetingPort.class.getClassLoader())
                .isNotInstanceOf(SandboxedModuleClassLoader.class);
    }

    @Test
    @DisplayName("unload withdraws the service, so nothing calls into a closed ClassLoader")
    void unloadWithdrawsTheService() {
        manager.loadModule(TENANT_ID, moduleData());
        manager.unloadModule(TENANT_ID, "test-module");

        // The module builds a fresh implementation per getServices() call, so this only passes
        // because unload removes the instance that was actually registered.
        assertThat(serviceRegistry.find(TENANT_ID, GreetingPort.class)).isEmpty();
        assertThat(serviceRegistry.serviceCount(TENANT_ID)).isZero();
    }

    @Test
    @DisplayName("a reload republishes cleanly rather than colliding with the previous instance")
    void reloadRepublishes() {
        manager.loadModule(TENANT_ID, moduleData());
        manager.unloadModule(TENANT_ID, "test-module");
        manager.loadModule(TENANT_ID, moduleData());

        // Disable/enable is the ordinary lifecycle; if unload left the old instance behind, this
        // second load would be rejected as a duplicate port and fall back to inert stubs.
        assertThat(serviceRegistry.find(TENANT_ID, GreetingPort.class))
                .map(port -> port.greet("craig"))
                .contains("hello craig, from the module");
    }

    @Test
    @DisplayName("with no service registry wired, a publishing module still loads")
    void withoutRegistryModuleStillLoads() throws Exception {
        ModuleJarService jarService = mock(ModuleJarService.class);
        when(jarService.downloadJarToCache("s3/key.jar"))
                .thenReturn(tempDir.resolve("test-module.jar").toUri().toURL());

        RuntimeModuleManager noRegistry = new RuntimeModuleManager(moduleStore,
                new ActionHandlerRegistry(), new ObjectMapper(), jarService, null, null, null, null);

        // Nothing to publish into is not an error -- the module's handlers and hooks still work and
        // platform callers keep their compiled-in behaviour. Not throwing IS the assertion here;
        // asserting on `serviceRegistry` would prove nothing, since that instance was never wired
        // into this manager.
        assertThatCode(() -> noRegistry.loadModule(TENANT_ID, moduleData()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a port another module already owns is refused, and the refusal unwinds cleanly")
    void conflictingPortIsRefusedWithoutStranding() {
        GreetingPort incumbent = name -> "hello " + name + ", from somewhere else";
        serviceRegistry.register(TENANT_ID, GreetingPort.class, incumbent);

        // The load fails internally and falls back to inert stubs rather than propagating.
        manager.loadModule(TENANT_ID, moduleData());

        // The incumbent must survive: losing it would silently change behaviour for a tenant
        // because of an unrelated module's install.
        assertThat(serviceRegistry.find(TENANT_ID, GreetingPort.class))
                .map(port -> port.greet("craig"))
                .contains("hello craig, from somewhere else");
        assertThat(serviceRegistry.serviceCount(TENANT_ID)).isEqualTo(1);

        // And unloading the refused module must not strip the incumbent either.
        manager.unloadModule(TENANT_ID, "test-module");
        assertThat(serviceRegistry.find(TENANT_ID, GreetingPort.class)).isPresent();
    }

    @Test
    @DisplayName("one refused port withdraws the ports the same module already published")
    void refusedPortUnwindsTheModulesEarlierPorts() throws Exception {
        // The incumbent makes the module's SECOND port collide, after its first has been accepted.
        serviceRegistry.register(TENANT_ID, GreetingPort.class,
                (GreetingPort) name -> "hello " + name + ", from somewhere else");

        Path jarFile = buildJar(TWO_SERVICE_MODULE_CLASS, "two-service-module.jar");
        byte[] bytes = java.nio.file.Files.readAllBytes(jarFile);
        ModuleJarService jarService = mock(ModuleJarService.class);
        when(jarService.downloadJarToCache("s3/two.jar")).thenReturn(jarFile.toUri().toURL());

        RuntimeModuleManager twoServiceManager = new RuntimeModuleManager(moduleStore,
                new ActionHandlerRegistry(), new ObjectMapper(), jarService, null, null, null,
                null, null, serviceRegistry);

        twoServiceManager.loadModule(TENANT_ID, new TenantModuleData(
                "mod-2", TENANT_ID, "two-service-module", "Two Service Test Module", "1.0.0",
                "Test", "https://example.com/two.jar", ModuleJarService.sha256(bytes),
                (long) bytes.length, TWO_SERVICE_MODULE_CLASS, TWO_SERVICE_MANIFEST_JSON,
                TenantModuleData.STATUS_ACTIVE, "user-1",
                Instant.now(), Instant.now(), "s3/two.jar", List.of()));

        // CountingPort was accepted before GreetingPort was refused. Leaving it behind would mean a
        // module that failed to load is still answering platform calls -- from a ClassLoader the
        // failure path is about to discard.
        assertThat(serviceRegistry.find(TENANT_ID, CountingPort.class)).isEmpty();

        // ...and the refusal must not have disturbed the incumbent.
        assertThat(serviceRegistry.find(TENANT_ID, GreetingPort.class))
                .map(port -> port.greet("craig"))
                .contains("hello craig, from somewhere else");
        assertThat(serviceRegistry.serviceCount(TENANT_ID)).isEqualTo(1);
    }

    private TenantModuleData moduleData() {
        return new TenantModuleData(
                "mod-1", TENANT_ID, "test-module", "Service Providing Test Module", "1.0.0",
                "Test", "https://example.com/module.jar", ModuleJarService.sha256(jarBytes),
                (long) jarBytes.length, MODULE_CLASS, MANIFEST_JSON,
                TenantModuleData.STATUS_ACTIVE, "user-1",
                Instant.now(), Instant.now(), "s3/key.jar", List.of());
    }

    /**
     * Packages the test module's compiled class into a JAR. Its package is outside the sandboxed
     * classloader's parent allowlist, so it must resolve from these bytes; {@code GreetingPort}
     * is deliberately NOT packaged, so it must resolve from the platform.
     */
    private Path buildModuleJar() throws IOException {
        return buildJar(MODULE_CLASS, "test-module.jar");
    }

    private Path buildJar(String moduleClass, String fileName) throws IOException {
        Path jarFile = tempDir.resolve(fileName);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarFile.toFile()),
                manifest)) {
            addClass(jar, moduleClass.replace('.', '/') + ".class");
            // The published services are lambdas, compiled into the module class itself, so no
            // extra entry is needed -- unlike the anonymous-inner-class hook fixture.
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
