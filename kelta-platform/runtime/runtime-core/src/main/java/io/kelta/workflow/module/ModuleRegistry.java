package io.kelta.runtime.workflow.module;

import io.kelta.runtime.workflow.ActionHandler;
import io.kelta.runtime.workflow.ActionHandlerRegistry;
import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveHookRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Registry that discovers and initializes all {@link KeltaModule} implementations.
 *
 * <p>On initialization, the registry:
 * <ol>
 *   <li>Collects all discovered KeltaModule beans</li>
 *   <li>Calls {@link KeltaModule#onStartup(ModuleContext)} on each module (allows lazy handler construction)</li>
 *   <li>Registers each module's action handlers with the {@link ActionHandlerRegistry}</li>
 *   <li>Registers each module's before-save hooks with the {@link BeforeSaveHookRegistry}</li>
 * </ol>
 *
 * <p><strong>Important:</strong> {@code onStartup()} is called <em>before</em> handler/hook registration.
 * This allows modules to construct handlers lazily in {@code onStartup()} using services from
 * {@link ModuleContext} (e.g., FormulaEvaluator, CollectionRegistry).
 *
 * @since 1.0.0
 */
public class ModuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModuleRegistry.class);

    private final ActionHandlerRegistry actionHandlerRegistry;
    private final BeforeSaveHookRegistry beforeSaveHookRegistry;
    private final Map<String, KeltaModule> modules = new LinkedHashMap<>();

    /**
     * Creates a new ModuleRegistry.
     *
     * @param actionHandlerRegistry the action handler registry
     * @param beforeSaveHookRegistry the before-save hook registry
     */
    public ModuleRegistry(ActionHandlerRegistry actionHandlerRegistry,
                          BeforeSaveHookRegistry beforeSaveHookRegistry) {
        this.actionHandlerRegistry = Objects.requireNonNull(actionHandlerRegistry);
        this.beforeSaveHookRegistry = Objects.requireNonNull(beforeSaveHookRegistry);
    }

    /**
     * Initializes all modules: calls onStartup first, then registers their handlers/hooks.
     *
     * <p>The initialization order is:
     * <ol>
     *   <li>Add all modules to the internal map</li>
     *   <li>Call {@code onStartup(context)} on each module (allows lazy handler construction)</li>
     *   <li>Register action handlers and before-save hooks from each module</li>
     * </ol>
     *
     * @param discoveredModules the list of modules discovered by Spring
     * @param context the module context for startup callbacks
     */
    public void initialize(List<KeltaModule> discoveredModules, ModuleContext context) {
        log.info("Initializing ModuleRegistry with {} discovered modules", discoveredModules.size());

        // Phase 1: Add all modules to the map (handles duplicates)
        for (KeltaModule module : discoveredModules) {
            String id = module.getId();
            if (modules.containsKey(id)) {
                log.warn("Duplicate module ID '{}': {} replaced by {}",
                    id, modules.get(id).getName(), module.getName());
            }
            modules.put(id, module);
        }

        // Phase 2: Call onStartup on each module (allows lazy handler construction)
        for (KeltaModule module : modules.values()) {
            try {
                module.onStartup(context);
                log.info("Module '{}' v{} started successfully", module.getName(), module.getVersion());
            } catch (Exception e) {
                log.error("Failed to start module '{}': {}", module.getName(), e.getMessage(), e);
            }
        }

        // Phase 3: Register action handlers and before-save hooks
        for (KeltaModule module : modules.values()) {
            registerHandlersAndHooks(module);
        }

        log.info("ModuleRegistry initialized: {} modules, {} action handlers, {} before-save hooks",
            modules.size(), actionHandlerRegistry.size(), beforeSaveHookRegistry.getHookCount());
    }

    /**
     * Registers a single module, adding its handlers and hooks to the registries.
     *
     * <p>Note: When using {@link #initialize(List, ModuleContext)}, prefer that method
     * since it calls {@code onStartup()} before registering handlers. This method is
     * useful for registering modules that have pre-built handlers.
     *
     * @param module the module to register
     */
    public void registerModule(KeltaModule module) {
        String id = module.getId();
        if (modules.containsKey(id)) {
            log.warn("Duplicate module ID '{}': {} replaced by {}",
                id, modules.get(id).getName(), module.getName());
        }
        modules.put(id, module);
        registerHandlersAndHooks(module);
    }

    /**
     * Gets a registered module by ID.
     *
     * @param moduleId the module ID
     * @return the module, or empty if not found
     */
    public Optional<KeltaModule> getModule(String moduleId) {
        return Optional.ofNullable(modules.get(moduleId));
    }

    /**
     * Gets all registered module IDs.
     *
     * @return set of module IDs
     */
    public Set<String> getRegisteredModuleIds() {
        return Collections.unmodifiableSet(modules.keySet());
    }

    /**
     * Returns the number of registered modules.
     */
    public int size() {
        return modules.size();
    }

    private void registerHandlersAndHooks(KeltaModule module) {
        // Register action handlers
        for (ActionHandler handler : module.getActionHandlers()) {
            actionHandlerRegistry.register(handler);
        }

        // Register before-save hooks
        for (BeforeSaveHook hook : module.getBeforeSaveHooks()) {
            beforeSaveHookRegistry.register(hook);
        }

        log.info("Registered module '{}' v{} with {} action handlers and {} before-save hooks",
            module.getName(), module.getVersion(),
            module.getActionHandlers().size(), module.getBeforeSaveHooks().size());
    }
}
