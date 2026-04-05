package io.kelta.worker.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

/**
 * A sandboxed ClassLoader for runtime-loaded tenant modules.
 * <p>
 * Uses a child-first (parent-last) loading strategy so module classes
 * are loaded from the JAR first. Only a restricted set of platform
 * packages is delegated to the parent ClassLoader, limiting what
 * module code can access.
 * <p>
 * Allowed parent packages:
 * <ul>
 *   <li>{@code java.} — JDK core classes</li>
 *   <li>{@code javax.} — JDK extension classes</li>
 *   <li>{@code io.kelta.runtime.workflow.} — ActionHandler, ActionContext, ActionResult, etc.</li>
 *   <li>{@code io.kelta.runtime.workflow.module.} — KeltaModule, ModuleContext</li>
 *   <li>{@code io.kelta.runtime.flow.} — ActionHandlerDescriptor</li>
 *   <li>{@code io.kelta.runtime.module.} — ModuleManifest, TenantModuleData</li>
 *   <li>{@code io.kelta.runtime.query.} — QueryEngine for data access</li>
 *   <li>{@code io.kelta.runtime.registry.} — CollectionRegistry</li>
 *   <li>{@code io.kelta.runtime.formula.} — FormulaEvaluator</li>
 *   <li>{@code io.kelta.runtime.storage.} — StorageAdapter</li>
 *   <li>{@code tools.jackson.} — Jackson JSON library</li>
 *   <li>{@code org.slf4j.} — Logging API</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class SandboxedModuleClassLoader extends URLClassLoader implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(SandboxedModuleClassLoader.class);

    /**
     * Package prefixes that are allowed to load from the parent (platform) ClassLoader.
     * Everything else must come from the module JAR.
     */
    private static final Set<String> ALLOWED_PARENT_PREFIXES = Set.of(
        "java.",
        "javax.",
        "io.kelta.runtime.workflow.",
        "io.kelta.runtime.flow.",
        "io.kelta.runtime.module.",
        "io.kelta.runtime.query.",
        "io.kelta.runtime.registry.",
        "io.kelta.runtime.formula.",
        "io.kelta.runtime.storage.",
        "tools.jackson.",
        "org.slf4j."
    );

    private final String moduleId;

    /**
     * Creates a new sandboxed ClassLoader for a module JAR.
     *
     * @param moduleId the module identifier (for logging)
     * @param jarUrl   the URL pointing to the module JAR file
     * @param parent   the parent ClassLoader (platform ClassLoader)
     */
    public SandboxedModuleClassLoader(String moduleId, URL jarUrl, ClassLoader parent) {
        super(new URL[]{jarUrl}, parent);
        this.moduleId = moduleId;
        log.debug("Created SandboxedModuleClassLoader for module '{}' with JAR: {}", moduleId, jarUrl);
    }

    /**
     * Child-first class loading with restricted parent delegation.
     * <p>
     * 1. Check if already loaded.
     * 2. If the class belongs to an allowed parent package, delegate to parent.
     * 3. Otherwise, try to load from the module JAR.
     * 4. If not found in the JAR, throw ClassNotFoundException (do NOT fall back to parent).
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // Check if already loaded
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                return loaded;
            }

            // Delegate allowed packages to parent
            if (isAllowedParentPackage(name)) {
                return super.loadClass(name, resolve);
            }

            // Try loading from the module JAR first (child-first)
            try {
                Class<?> found = findClass(name);
                if (resolve) {
                    resolveClass(found);
                }
                return found;
            } catch (ClassNotFoundException e) {
                // Module tried to access a restricted platform class
                throw new ClassNotFoundException(
                    "Module '" + moduleId + "' cannot access class '" + name
                        + "': not in module JAR and not in allowed platform API", e);
            }
        }
    }

    private boolean isAllowedParentPackage(String className) {
        for (String prefix : ALLOWED_PARENT_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the module ID this ClassLoader is associated with.
     */
    public String getModuleId() {
        return moduleId;
    }

    @Override
    public void close() throws IOException {
        log.debug("Closing SandboxedModuleClassLoader for module '{}'", moduleId);
        super.close();
    }
}
