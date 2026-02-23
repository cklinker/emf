package com.emf.runtime.workflow.module;

import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionHandlerRegistry;
import com.emf.runtime.workflow.BeforeSaveHook;
import com.emf.runtime.workflow.BeforeSaveHookRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Registry that discovers and initializes all {@link EmfModule} implementations.
 *
 * <p>On initialization, the registry:
 * <ol>
 *   <li>Collects all discovered EmfModule beans</li>
 *   <li>Registers each module's action handlers with the {@link ActionHandlerRegistry}</li>
 *   <li>Registers each module's before-save hooks with the {@link BeforeSaveHookRegistry}</li>
 *   <li>Calls {@link EmfModule#onStartup(ModuleContext)} on each module</li>
 * </ol>
 *
 * @since 1.0.0
 */
public class ModuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModuleRegistry.class);

    private final ActionHandlerRegistry actionHandlerRegistry;
    private final BeforeSaveHookRegistry beforeSaveHookRegistry;
    private final Map<String, EmfModule> modules = new LinkedHashMap<>();

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
     * Initializes all modules: registers their handlers/hooks and calls onStartup.
     *
     * @param discoveredModules the list of modules discovered by Spring
     * @param context the module context for startup callbacks
     */
    public void initialize(List<EmfModule> discoveredModules, ModuleContext context) {
        log.info("Initializing ModuleRegistry with {} discovered modules", discoveredModules.size());

        for (EmfModule module : discoveredModules) {
            registerModule(module);
        }

        // Call onStartup after all modules are registered
        for (EmfModule module : modules.values()) {
            try {
                module.onStartup(context);
                log.info("Module '{}' v{} started successfully", module.getName(), module.getVersion());
            } catch (Exception e) {
                log.error("Failed to start module '{}': {}", module.getName(), e.getMessage(), e);
            }
        }

        log.info("ModuleRegistry initialized: {} modules, {} action handlers, {} before-save hooks",
            modules.size(), actionHandlerRegistry.size(), beforeSaveHookRegistry.getHookCount());
    }

    /**
     * Registers a single module, adding its handlers and hooks to the registries.
     *
     * @param module the module to register
     */
    public void registerModule(EmfModule module) {
        String id = module.getId();
        if (modules.containsKey(id)) {
            log.warn("Duplicate module ID '{}': {} replaced by {}",
                id, modules.get(id).getName(), module.getName());
        }
        modules.put(id, module);

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

    /**
     * Gets a registered module by ID.
     *
     * @param moduleId the module ID
     * @return the module, or empty if not found
     */
    public Optional<EmfModule> getModule(String moduleId) {
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
}
