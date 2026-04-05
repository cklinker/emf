package io.kelta.worker.module;

import io.kelta.runtime.flow.ActionHandlerDescriptor;
import io.kelta.runtime.module.ModuleManifest;
import io.kelta.runtime.module.ModuleManifestParser;
import io.kelta.runtime.module.ModuleStore;
import io.kelta.runtime.module.TenantModuleData;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionHandler;
import io.kelta.runtime.workflow.ActionHandlerRegistry;
import io.kelta.runtime.workflow.ActionResult;
import io.kelta.runtime.workflow.module.KeltaModule;
import io.kelta.runtime.workflow.module.ModuleContext;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of runtime-loaded tenant modules.
 * <p>
 * Handles module installation, enabling, disabling, and uninstalling.
 * Coordinates with the {@link ActionHandlerRegistry} to register/unregister
 * tenant-scoped action handlers.
 * <p>
 * When a {@link ModuleJarService} is available, modules are loaded from their
 * JAR files via a sandboxed {@link SandboxedModuleClassLoader}. If no JAR service
 * is configured (e.g., S3 is disabled), stub handlers are used instead.
 *
 * @since 1.0.0
 */
public class RuntimeModuleManager {

    private static final Logger log = LoggerFactory.getLogger(RuntimeModuleManager.class);

    private final ModuleStore moduleStore;
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final ModuleManifestParser manifestParser;
    private final ObjectMapper objectMapper;
    private final ModuleJarService jarService;
    private final ModuleContext moduleContext;

    /** Tracks which modules are loaded per tenant: tenantId -> Set<moduleId> */
    private final Map<String, Set<String>> loadedModules = new ConcurrentHashMap<>();

    /** Tracks active ClassLoaders for cleanup: "tenantId:moduleId" -> ClassLoader */
    private final Map<String, SandboxedModuleClassLoader> activeClassLoaders = new ConcurrentHashMap<>();

    /** Tracks loaded KeltaModule instances for lifecycle management */
    private final Map<String, KeltaModule> activeModuleInstances = new ConcurrentHashMap<>();

    /**
     * Creates a RuntimeModuleManager with JAR loading support.
     */
    public RuntimeModuleManager(ModuleStore moduleStore,
                                 ActionHandlerRegistry actionHandlerRegistry,
                                 ObjectMapper objectMapper,
                                 ModuleJarService jarService,
                                 ModuleContext moduleContext) {
        this.moduleStore = Objects.requireNonNull(moduleStore);
        this.actionHandlerRegistry = Objects.requireNonNull(actionHandlerRegistry);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.manifestParser = new ModuleManifestParser(objectMapper);
        this.jarService = jarService;
        this.moduleContext = moduleContext;
    }

    /**
     * Creates a RuntimeModuleManager without JAR loading support (stub-only mode).
     */
    public RuntimeModuleManager(ModuleStore moduleStore,
                                 ActionHandlerRegistry actionHandlerRegistry,
                                 ObjectMapper objectMapper) {
        this(moduleStore, actionHandlerRegistry, objectMapper, null, null);
    }

    /**
     * Installs a module for a tenant.
     * Parses the manifest, persists metadata, and creates action records.
     *
     * @param tenantId    the tenant ID
     * @param manifestJson the module manifest JSON
     * @param sourceUrl   the original download URL
     * @param checksum    SHA-256 checksum
     * @param jarSizeBytes JAR file size
     * @param installedBy the user who installed the module
     * @return the persisted module data
     */
    public TenantModuleData installModule(String tenantId, String manifestJson,
                                           String sourceUrl, String checksum,
                                           Long jarSizeBytes, String installedBy) {
        ModuleManifest manifest = manifestParser.parse(manifestJson);

        // Check for existing installation
        Optional<TenantModuleData> existing = moduleStore.findByTenantAndModuleId(
            tenantId, manifest.id());
        if (existing.isPresent()) {
            throw new IllegalStateException(
                "Module '" + manifest.id() + "' is already installed for tenant " + tenantId);
        }

        String id = UUID.randomUUID().toString();
        TenantModuleData data = new TenantModuleData(
            id, tenantId, manifest.id(), manifest.name(), manifest.version(),
            manifest.description(), sourceUrl, checksum, jarSizeBytes,
            manifest.moduleClass(), manifestJson,
            TenantModuleData.STATUS_INSTALLED, installedBy,
            null, null, null, List.of()
        );

        moduleStore.createModule(data);

        // Create action records from manifest
        List<TenantModuleData.TenantModuleActionData> actions = new ArrayList<>();
        for (ModuleManifest.ActionHandlerManifest handler : manifest.actionHandlers()) {
            actions.add(new TenantModuleData.TenantModuleActionData(
                UUID.randomUUID().toString(), id, handler.key(), handler.name(),
                handler.category(), handler.description(), handler.configSchema(),
                handler.inputSchema(), handler.outputSchema()
            ));
        }
        if (!actions.isEmpty()) {
            moduleStore.createActions(actions);
        }

        log.info("Installed module '{}' v{} for tenant {} with {} action handlers",
            manifest.name(), manifest.version(), tenantId, actions.size());

        return moduleStore.findById(id).orElse(data);
    }

    /**
     * Installs a module with its JAR file.
     * Uploads the JAR to S3 and persists the S3 key.
     *
     * @param tenantId     the tenant ID
     * @param manifestJson the module manifest JSON
     * @param jarBytes     the module JAR file bytes
     * @param installedBy  the user who installed the module
     * @return the persisted module data
     */
    public TenantModuleData installModuleWithJar(String tenantId, String manifestJson,
                                                   byte[] jarBytes, String installedBy) {
        if (jarService == null) {
            throw new IllegalStateException("JAR upload requires S3 storage to be enabled");
        }

        ModuleManifest manifest = manifestParser.parse(manifestJson);

        String checksum = ModuleJarService.sha256(jarBytes);
        String s3Key = jarService.uploadJar(tenantId, manifest.id(), manifest.version(), jarBytes);

        // Check for existing installation
        Optional<TenantModuleData> existing = moduleStore.findByTenantAndModuleId(
            tenantId, manifest.id());
        if (existing.isPresent()) {
            throw new IllegalStateException(
                "Module '" + manifest.id() + "' is already installed for tenant " + tenantId);
        }

        String id = UUID.randomUUID().toString();
        TenantModuleData data = new TenantModuleData(
            id, tenantId, manifest.id(), manifest.name(), manifest.version(),
            manifest.description(), s3Key, checksum, (long) jarBytes.length,
            manifest.moduleClass(), manifestJson,
            TenantModuleData.STATUS_INSTALLED, installedBy,
            null, null, s3Key, List.of()
        );

        moduleStore.createModule(data);

        // Create action records from manifest
        List<TenantModuleData.TenantModuleActionData> actions = new ArrayList<>();
        for (ModuleManifest.ActionHandlerManifest handler : manifest.actionHandlers()) {
            actions.add(new TenantModuleData.TenantModuleActionData(
                UUID.randomUUID().toString(), id, handler.key(), handler.name(),
                handler.category(), handler.description(), handler.configSchema(),
                handler.inputSchema(), handler.outputSchema()
            ));
        }
        if (!actions.isEmpty()) {
            moduleStore.createActions(actions);
        }

        log.info("Installed module '{}' v{} for tenant {} with JAR (s3Key={}, {} bytes)",
            manifest.name(), manifest.version(), tenantId, s3Key, jarBytes.length);

        return moduleStore.findById(id).orElse(data);
    }

    /**
     * Enables a module, registering its action handlers.
     *
     * @param tenantId the tenant ID
     * @param moduleId the module identifier
     */
    public void enableModule(String tenantId, String moduleId) {
        TenantModuleData module = moduleStore.findByTenantAndModuleId(tenantId, moduleId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Module '" + moduleId + "' not found for tenant " + tenantId));

        if (TenantModuleData.STATUS_ACTIVE.equals(module.status())) {
            log.debug("Module '{}' already active for tenant {}", moduleId, tenantId);
            return;
        }

        moduleStore.updateStatus(module.id(), TenantModuleData.STATUS_ACTIVE);
        loadModule(tenantId, module);
        log.info("Enabled module '{}' for tenant {}", moduleId, tenantId);
    }

    /**
     * Disables a module, unregistering its action handlers.
     *
     * @param tenantId the tenant ID
     * @param moduleId the module identifier
     */
    public void disableModule(String tenantId, String moduleId) {
        TenantModuleData module = moduleStore.findByTenantAndModuleId(tenantId, moduleId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Module '" + moduleId + "' not found for tenant " + tenantId));

        moduleStore.updateStatus(module.id(), TenantModuleData.STATUS_DISABLED);
        unloadModule(tenantId, module);
        log.info("Disabled module '{}' for tenant {}", moduleId, tenantId);
    }

    /**
     * Uninstalls a module entirely.
     *
     * @param tenantId the tenant ID
     * @param moduleId the module identifier
     */
    public void uninstallModule(String tenantId, String moduleId) {
        TenantModuleData module = moduleStore.findByTenantAndModuleId(tenantId, moduleId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Module '" + moduleId + "' not found for tenant " + tenantId));

        unloadModule(tenantId, module);

        // Clean up S3 JAR if present
        if (module.s3Key() != null && jarService != null) {
            try {
                jarService.deleteJar(module.s3Key());
            } catch (Exception e) {
                log.warn("Failed to delete JAR from S3 for module '{}': {}",
                    moduleId, e.getMessage());
            }
        }

        moduleStore.deleteModule(module.id());
        log.info("Uninstalled module '{}' from tenant {}", moduleId, tenantId);
    }

    /**
     * Loads a module's handlers into the registry (called on enable or pod startup).
     * <p>
     * If the module has an S3 JAR key and JAR service is available, loads real handlers
     * from the JAR using a sandboxed ClassLoader. Otherwise, falls back to stub handlers.
     * <p>
     * Idempotent — no-op if already loaded.
     *
     * @param tenantId the tenant ID
     * @param module the module data
     */
    public void loadModule(String tenantId, TenantModuleData module) {
        Set<String> loaded = loadedModules.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet());
        if (loaded.contains(module.moduleId())) {
            log.debug("Module '{}' already loaded for tenant {}", module.moduleId(), tenantId);
            return;
        }

        if (module.s3Key() != null && jarService != null) {
            loadFromJar(tenantId, module);
        } else {
            loadWithStubs(tenantId, module);
        }

        loaded.add(module.moduleId());
        log.info("Loaded module '{}' v{} with {} handlers for tenant {}",
            module.name(), module.version(), module.actions().size(), tenantId);
    }

    /**
     * Loads module handlers from the JAR via a sandboxed ClassLoader.
     */
    private void loadFromJar(String tenantId, TenantModuleData module) {
        String classLoaderKey = tenantId + ":" + module.moduleId();
        try {
            URL jarUrl = jarService.downloadJarToCache(module.s3Key());
            SandboxedModuleClassLoader classLoader = new SandboxedModuleClassLoader(
                module.moduleId(), jarUrl, getClass().getClassLoader());

            // Load the KeltaModule implementation class
            @SuppressWarnings("unchecked")
            Class<? extends KeltaModule> moduleClass = (Class<? extends KeltaModule>)
                classLoader.loadClass(module.moduleClass());

            KeltaModule keltaModule = moduleClass.getDeclaredConstructor().newInstance();

            // Provide restricted module context and initialize
            if (moduleContext != null) {
                keltaModule.onStartup(moduleContext);
            }

            // Register real action handlers from the module
            List<ActionHandler> handlers = keltaModule.getActionHandlers();
            for (ActionHandler handler : handlers) {
                actionHandlerRegistry.registerTenantHandler(tenantId, handler);
            }

            activeClassLoaders.put(classLoaderKey, classLoader);
            activeModuleInstances.put(classLoaderKey, keltaModule);

            log.info("Loaded module '{}' from JAR with {} real handlers for tenant {}",
                module.moduleId(), handlers.size(), tenantId);

        } catch (Exception e) {
            log.warn("Failed to load module '{}' from JAR for tenant {}: {}. Falling back to stubs.",
                module.moduleId(), tenantId, e.getMessage(), e);

            // Clean up partial ClassLoader on failure
            SandboxedModuleClassLoader cl = activeClassLoaders.remove(classLoaderKey);
            if (cl != null) {
                try { cl.close(); } catch (IOException ignored) {}
            }
            activeModuleInstances.remove(classLoaderKey);

            // Fall back to stubs
            loadWithStubs(tenantId, module);
        }
    }

    /**
     * Loads stub handlers from manifest metadata (no JAR available).
     */
    private void loadWithStubs(String tenantId, TenantModuleData module) {
        for (var action : module.actions()) {
            ActionHandler stubHandler = createStubHandler(action, module);
            actionHandlerRegistry.registerTenantHandler(tenantId, stubHandler);
        }
        log.debug("Loaded module '{}' with stub handlers for tenant {}", module.moduleId(), tenantId);
    }

    /**
     * Unloads a module's handlers from the registry.
     * Idempotent — no-op if not loaded.
     *
     * @param tenantId the tenant ID
     * @param module the module data
     */
    public void unloadModule(String tenantId, TenantModuleData module) {
        Set<String> loaded = loadedModules.get(tenantId);
        if (loaded == null || !loaded.contains(module.moduleId())) {
            log.debug("Module '{}' not loaded for tenant {}", module.moduleId(), tenantId);
            return;
        }

        Set<String> actionKeys = new HashSet<>();
        for (var action : module.actions()) {
            actionKeys.add(action.actionKey());
        }

        // Also remove any real handlers registered by the KeltaModule
        String classLoaderKey = tenantId + ":" + module.moduleId();
        KeltaModule keltaModule = activeModuleInstances.remove(classLoaderKey);
        if (keltaModule != null) {
            for (ActionHandler handler : keltaModule.getActionHandlers()) {
                actionKeys.add(handler.getActionTypeKey());
            }
        }

        actionHandlerRegistry.removeTenantHandlers(tenantId, actionKeys);

        // Close the ClassLoader
        SandboxedModuleClassLoader classLoader = activeClassLoaders.remove(classLoaderKey);
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException e) {
                log.warn("Failed to close ClassLoader for module '{}': {}", module.moduleId(), e.getMessage());
            }
        }

        // Evict JAR from local cache
        if (module.s3Key() != null && jarService != null) {
            jarService.evictFromCache(module.s3Key());
        }

        loaded.remove(module.moduleId());
        if (loaded.isEmpty()) {
            loadedModules.remove(tenantId);
        }
        log.info("Unloaded module '{}' for tenant {}", module.moduleId(), tenantId);
    }

    /**
     * Loads all active modules on pod startup.
     */
    public void loadAllActiveModules() {
        List<TenantModuleData> activeModules = moduleStore.findAllActive();
        log.info("Loading {} active runtime modules on startup", activeModules.size());
        for (TenantModuleData module : activeModules) {
            try {
                loadModule(module.tenantId(), module);
            } catch (Exception e) {
                log.error("Failed to load module '{}' for tenant {}: {}",
                    module.moduleId(), module.tenantId(), e.getMessage(), e);
                moduleStore.updateStatus(module.id(), TenantModuleData.STATUS_FAILED);
            }
        }
    }

    /**
     * Lists all modules for a tenant.
     */
    public List<TenantModuleData> listModules(String tenantId) {
        return moduleStore.findByTenant(tenantId);
    }

    /**
     * Checks if a module is loaded for a tenant.
     */
    public boolean isLoaded(String tenantId, String moduleId) {
        Set<String> loaded = loadedModules.get(tenantId);
        return loaded != null && loaded.contains(moduleId);
    }

    /**
     * Checks if JAR-based loading is available.
     */
    public boolean isJarLoadingEnabled() {
        return jarService != null;
    }

    /**
     * Creates a stub action handler from manifest metadata.
     * Used when no JAR is available for real ClassLoader-based loading.
     */
    private ActionHandler createStubHandler(TenantModuleData.TenantModuleActionData action,
                                             TenantModuleData module) {
        return new ActionHandler() {
            @Override
            public String getActionTypeKey() {
                return action.actionKey();
            }

            @Override
            public ActionResult execute(ActionContext context) {
                log.info("Executing runtime module handler '{}' from module '{}' v{} (stub mode)",
                    action.actionKey(), module.name(), module.version());
                return ActionResult.success(Map.of(
                    "handler", action.actionKey(),
                    "module", module.moduleId(),
                    "status", "EXECUTED",
                    "mode", "stub"
                ));
            }

            @Override
            public ActionHandlerDescriptor getDescriptor() {
                return new ActionHandlerDescriptor() {
                    @Override public String getConfigSchema() { return action.configSchema(); }
                    @Override public String getInputSchema() { return action.inputSchema(); }
                    @Override public String getOutputSchema() { return action.outputSchema(); }
                    @Override public String getDisplayName() { return action.name(); }
                    @Override public String getCategory() { return action.category(); }
                    @Override public String getDescription() { return action.description(); }
                };
            }
        };
    }
}
