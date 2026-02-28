package com.emf.worker.module;

import com.emf.runtime.flow.ActionHandlerDescriptor;
import com.emf.runtime.module.ModuleManifest;
import com.emf.runtime.module.ModuleManifestParser;
import com.emf.runtime.module.ModuleStore;
import com.emf.runtime.module.TenantModuleData;
import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionHandlerRegistry;
import com.emf.runtime.workflow.ActionResult;
import com.emf.runtime.workflow.module.EmfModule;
import com.emf.runtime.workflow.module.ModuleContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Note: In this phase, modules are registered from their manifest metadata
 * (stub handlers). Full JAR-based ClassLoader loading is deferred to a future
 * phase when S3 storage and sandboxed ClassLoaders are implemented.
 *
 * @since 1.0.0
 */
public class RuntimeModuleManager {

    private static final Logger log = LoggerFactory.getLogger(RuntimeModuleManager.class);

    private final ModuleStore moduleStore;
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final ModuleManifestParser manifestParser;
    private final ObjectMapper objectMapper;

    /** Tracks which modules are loaded per tenant: tenantId -> Set<moduleId> */
    private final Map<String, Set<String>> loadedModules = new ConcurrentHashMap<>();

    public RuntimeModuleManager(ModuleStore moduleStore,
                                 ActionHandlerRegistry actionHandlerRegistry,
                                 ObjectMapper objectMapper) {
        this.moduleStore = Objects.requireNonNull(moduleStore);
        this.actionHandlerRegistry = Objects.requireNonNull(actionHandlerRegistry);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.manifestParser = new ModuleManifestParser(objectMapper);
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
            null, null, List.of()
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
        moduleStore.deleteModule(module.id());
        log.info("Uninstalled module '{}' from tenant {}", moduleId, tenantId);
    }

    /**
     * Loads a module's handlers into the registry (called on enable or pod startup).
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

        // Register stub handlers from manifest actions
        for (var action : module.actions()) {
            ActionHandler stubHandler = createStubHandler(action, module);
            actionHandlerRegistry.registerTenantHandler(tenantId, stubHandler);
        }

        loaded.add(module.moduleId());
        log.info("Loaded module '{}' v{} with {} handlers for tenant {}",
            module.name(), module.version(), module.actions().size(), tenantId);
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
        actionHandlerRegistry.removeTenantHandlers(tenantId, actionKeys);

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
     * Creates a stub action handler from manifest metadata.
     * The stub logs execution and returns a placeholder result.
     * Full implementation via ClassLoader loading will come in a future phase.
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
                log.info("Executing runtime module handler '{}' from module '{}' v{}",
                    action.actionKey(), module.name(), module.version());
                // Stub implementation — full JAR-loaded execution in future phase
                return ActionResult.success(Map.of(
                    "handler", action.actionKey(),
                    "module", module.moduleId(),
                    "status", "EXECUTED"
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
