package io.kelta.worker.module;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class SandboxedModuleClassLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldAllowJdkClasses() throws Exception {
        URL jarUrl = createEmptyJar("empty.jar");
        try (SandboxedModuleClassLoader cl = new SandboxedModuleClassLoader("test", jarUrl, getClass().getClassLoader())) {
            // java.lang.String should be loadable
            Class<?> stringClass = cl.loadClass("java.lang.String");
            assertNotNull(stringClass);
            assertEquals(String.class, stringClass);
        }
    }

    @Test
    void shouldAllowRuntimeWorkflowClasses() throws Exception {
        URL jarUrl = createEmptyJar("empty.jar");
        try (SandboxedModuleClassLoader cl = new SandboxedModuleClassLoader("test", jarUrl, getClass().getClassLoader())) {
            // io.kelta.runtime.workflow.ActionHandler should be loadable
            Class<?> handlerClass = cl.loadClass("io.kelta.runtime.workflow.ActionHandler");
            assertNotNull(handlerClass);
        }
    }

    @Test
    void shouldAllowRuntimeModuleClasses() throws Exception {
        URL jarUrl = createEmptyJar("empty.jar");
        try (SandboxedModuleClassLoader cl = new SandboxedModuleClassLoader("test", jarUrl, getClass().getClassLoader())) {
            Class<?> moduleClass = cl.loadClass("io.kelta.runtime.workflow.module.KeltaModule");
            assertNotNull(moduleClass);
        }
    }

    @Test
    void shouldAllowSlf4jClasses() throws Exception {
        URL jarUrl = createEmptyJar("empty.jar");
        try (SandboxedModuleClassLoader cl = new SandboxedModuleClassLoader("test", jarUrl, getClass().getClassLoader())) {
            Class<?> loggerClass = cl.loadClass("org.slf4j.Logger");
            assertNotNull(loggerClass);
        }
    }

    @Test
    void shouldBlockPlatformInternalClasses() throws Exception {
        URL jarUrl = createEmptyJar("empty.jar");
        try (SandboxedModuleClassLoader cl = new SandboxedModuleClassLoader("test", jarUrl, getClass().getClassLoader())) {
            // Spring classes should NOT be accessible
            assertThrows(ClassNotFoundException.class, () ->
                cl.loadClass("org.springframework.context.ApplicationContext"));
        }
    }

    @Test
    void shouldBlockWorkerClasses() throws Exception {
        URL jarUrl = createEmptyJar("empty.jar");
        try (SandboxedModuleClassLoader cl = new SandboxedModuleClassLoader("test", jarUrl, getClass().getClassLoader())) {
            // Worker internal classes should NOT be accessible
            assertThrows(ClassNotFoundException.class, () ->
                cl.loadClass("io.kelta.worker.module.RuntimeModuleManager"));
        }
    }

    @Test
    void shouldReturnModuleId() throws Exception {
        URL jarUrl = createEmptyJar("empty.jar");
        try (SandboxedModuleClassLoader cl = new SandboxedModuleClassLoader("my-module", jarUrl, getClass().getClassLoader())) {
            assertEquals("my-module", cl.getModuleId());
        }
    }

    @Test
    void shouldBeCloseable() throws Exception {
        URL jarUrl = createEmptyJar("empty.jar");
        SandboxedModuleClassLoader cl = new SandboxedModuleClassLoader("test", jarUrl, getClass().getClassLoader());
        // Should not throw
        cl.close();
    }

    private URL createEmptyJar(String name) throws IOException {
        File jarFile = tempDir.resolve(name).toFile();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
            // Empty JAR with just manifest
        }
        return jarFile.toURI().toURL();
    }
}
