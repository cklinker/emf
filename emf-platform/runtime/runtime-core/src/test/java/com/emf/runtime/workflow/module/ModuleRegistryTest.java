package com.emf.runtime.workflow.module;

import com.emf.runtime.workflow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModuleRegistry")
class ModuleRegistryTest {

    private ActionHandlerRegistry actionHandlerRegistry;
    private BeforeSaveHookRegistry beforeSaveHookRegistry;
    private ModuleRegistry moduleRegistry;

    @BeforeEach
    void setUp() {
        actionHandlerRegistry = new ActionHandlerRegistry();
        beforeSaveHookRegistry = new BeforeSaveHookRegistry();
        moduleRegistry = new ModuleRegistry(actionHandlerRegistry, beforeSaveHookRegistry);
    }

    @Test
    @DisplayName("Should register a module and propagate action handlers")
    void shouldRegisterModuleWithActionHandlers() {
        EmfModule module = createModule("test-module", "Test Module", "1.0.0",
            List.of(stubActionHandler("FIELD_UPDATE")), List.of());

        moduleRegistry.registerModule(module);

        assertEquals(1, moduleRegistry.size());
        assertTrue(moduleRegistry.getModule("test-module").isPresent());
        assertTrue(actionHandlerRegistry.hasHandler("FIELD_UPDATE"));
    }

    @Test
    @DisplayName("Should register a module and propagate before-save hooks")
    void shouldRegisterModuleWithBeforeSaveHooks() {
        EmfModule module = createModule("test-module", "Test Module", "1.0.0",
            List.of(), List.of(stubBeforeSaveHook("users", 0)));

        moduleRegistry.registerModule(module);

        assertEquals(1, moduleRegistry.size());
        assertTrue(beforeSaveHookRegistry.hasHooks("users"));
    }

    @Test
    @DisplayName("Should initialize multiple modules and call onStartup")
    void shouldInitializeMultipleModules() {
        AtomicBoolean startupCalled1 = new AtomicBoolean(false);
        AtomicBoolean startupCalled2 = new AtomicBoolean(false);

        EmfModule m1 = new EmfModule() {
            @Override public String getId() { return "mod-1"; }
            @Override public String getName() { return "Module 1"; }
            @Override public String getVersion() { return "1.0"; }
            @Override public List<ActionHandler> getActionHandlers() {
                return List.of(stubActionHandler("A1"));
            }
            @Override public void onStartup(ModuleContext context) { startupCalled1.set(true); }
        };

        EmfModule m2 = new EmfModule() {
            @Override public String getId() { return "mod-2"; }
            @Override public String getName() { return "Module 2"; }
            @Override public String getVersion() { return "2.0"; }
            @Override public List<BeforeSaveHook> getBeforeSaveHooks() {
                return List.of(stubBeforeSaveHook("collections", 0));
            }
            @Override public void onStartup(ModuleContext context) { startupCalled2.set(true); }
        };

        ModuleContext context = new ModuleContext(null, null, null, null);
        moduleRegistry.initialize(List.of(m1, m2), context);

        assertEquals(2, moduleRegistry.size());
        assertTrue(startupCalled1.get());
        assertTrue(startupCalled2.get());
        assertTrue(actionHandlerRegistry.hasHandler("A1"));
        assertTrue(beforeSaveHookRegistry.hasHooks("collections"));
    }

    @Test
    @DisplayName("Should call onStartup BEFORE registering handlers (lazy init support)")
    void shouldCallOnStartupBeforeRegisteringHandlers() {
        // This test verifies that modules can construct handlers lazily in onStartup()
        // using services from ModuleContext, and those handlers will be registered afterward.
        List<ActionHandler> lazyHandlers = new ArrayList<>();

        EmfModule lazyModule = new EmfModule() {
            @Override public String getId() { return "lazy-mod"; }
            @Override public String getName() { return "Lazy Module"; }
            @Override public String getVersion() { return "1.0"; }

            @Override public List<ActionHandler> getActionHandlers() {
                return lazyHandlers;
            }

            @Override public void onStartup(ModuleContext context) {
                // Construct handlers lazily during onStartup, using context services
                assertNotNull(context, "Context should be provided to onStartup");
                lazyHandlers.add(stubActionHandler("LAZY_ACTION"));
            }
        };

        ModuleContext context = new ModuleContext(null, null, null, null);
        moduleRegistry.initialize(List.of(lazyModule), context);

        assertEquals(1, moduleRegistry.size());
        assertTrue(actionHandlerRegistry.hasHandler("LAZY_ACTION"),
            "Handler constructed in onStartup() should be registered");
    }

    @Test
    @DisplayName("Should call onStartup BEFORE registering hooks (lazy init support)")
    void shouldCallOnStartupBeforeRegisteringHooks() {
        List<BeforeSaveHook> lazyHooks = new ArrayList<>();

        EmfModule lazyModule = new EmfModule() {
            @Override public String getId() { return "lazy-hook-mod"; }
            @Override public String getName() { return "Lazy Hook Module"; }
            @Override public String getVersion() { return "1.0"; }

            @Override public List<BeforeSaveHook> getBeforeSaveHooks() {
                return lazyHooks;
            }

            @Override public void onStartup(ModuleContext context) {
                lazyHooks.add(stubBeforeSaveHook("tenants", 10));
            }
        };

        ModuleContext context = new ModuleContext(null, null, null, null);
        moduleRegistry.initialize(List.of(lazyModule), context);

        assertTrue(beforeSaveHookRegistry.hasHooks("tenants"),
            "Hook constructed in onStartup() should be registered");
    }

    @Test
    @DisplayName("Should pass context with actionHandlerRegistry to onStartup")
    void shouldPassContextWithActionHandlerRegistryToOnStartup() {
        AtomicReference<ModuleContext> capturedContext = new AtomicReference<>();

        EmfModule module = new EmfModule() {
            @Override public String getId() { return "ctx-mod"; }
            @Override public String getName() { return "Context Module"; }
            @Override public String getVersion() { return "1.0"; }
            @Override public void onStartup(ModuleContext context) {
                capturedContext.set(context);
            }
        };

        ActionHandlerRegistry registry = new ActionHandlerRegistry();
        ModuleContext context = new ModuleContext(null, null, null, null, registry, null);
        moduleRegistry.initialize(List.of(module), context);

        assertNotNull(capturedContext.get());
        assertSame(registry, capturedContext.get().actionHandlerRegistry());
    }

    @Test
    @DisplayName("Should handle onStartup exceptions gracefully")
    void shouldHandleStartupExceptions() {
        EmfModule failingModule = new EmfModule() {
            @Override public String getId() { return "failing"; }
            @Override public String getName() { return "Failing Module"; }
            @Override public String getVersion() { return "0.1"; }
            @Override public void onStartup(ModuleContext context) {
                throw new RuntimeException("Startup failure");
            }
        };

        ModuleContext context = new ModuleContext(null, null, null, null);

        // Should not throw even though onStartup throws
        assertDoesNotThrow(() -> moduleRegistry.initialize(List.of(failingModule), context));
        assertEquals(1, moduleRegistry.size());
    }

    @Test
    @DisplayName("Should still register handlers from failing module")
    void shouldStillRegisterHandlersFromFailingModule() {
        EmfModule failingModule = new EmfModule() {
            @Override public String getId() { return "failing-with-handlers"; }
            @Override public String getName() { return "Failing Module"; }
            @Override public String getVersion() { return "0.1"; }
            @Override public List<ActionHandler> getActionHandlers() {
                return List.of(stubActionHandler("FAIL_ACTION"));
            }
            @Override public void onStartup(ModuleContext context) {
                throw new RuntimeException("Startup failure");
            }
        };

        ModuleContext context = new ModuleContext(null, null, null, null);
        moduleRegistry.initialize(List.of(failingModule), context);

        // Even though onStartup failed, the module's pre-built handlers should still be registered
        assertTrue(actionHandlerRegistry.hasHandler("FAIL_ACTION"));
    }

    @Test
    @DisplayName("Should replace module with duplicate ID")
    void shouldReplaceDuplicateModule() {
        EmfModule m1 = createModule("dup", "Module V1", "1.0", List.of(), List.of());
        EmfModule m2 = createModule("dup", "Module V2", "2.0", List.of(), List.of());

        moduleRegistry.registerModule(m1);
        moduleRegistry.registerModule(m2);

        assertEquals(1, moduleRegistry.size());
        assertEquals("Module V2", moduleRegistry.getModule("dup").orElseThrow().getName());
    }

    @Test
    @DisplayName("Should return empty for unknown module ID")
    void shouldReturnEmptyForUnknownModule() {
        assertTrue(moduleRegistry.getModule("unknown").isEmpty());
    }

    @Test
    @DisplayName("Should return all registered module IDs")
    void shouldReturnRegisteredModuleIds() {
        moduleRegistry.registerModule(createModule("a", "A", "1.0", List.of(), List.of()));
        moduleRegistry.registerModule(createModule("b", "B", "1.0", List.of(), List.of()));

        assertEquals(2, moduleRegistry.getRegisteredModuleIds().size());
        assertTrue(moduleRegistry.getRegisteredModuleIds().contains("a"));
        assertTrue(moduleRegistry.getRegisteredModuleIds().contains("b"));
    }

    @Test
    @DisplayName("Should require non-null registries in constructor")
    void shouldRequireNonNullRegistries() {
        assertThrows(NullPointerException.class, () ->
            new ModuleRegistry(null, beforeSaveHookRegistry));
        assertThrows(NullPointerException.class, () ->
            new ModuleRegistry(actionHandlerRegistry, null));
    }

    private EmfModule createModule(String id, String name, String version,
                                    List<ActionHandler> handlers,
                                    List<BeforeSaveHook> hooks) {
        return new EmfModule() {
            @Override public String getId() { return id; }
            @Override public String getName() { return name; }
            @Override public String getVersion() { return version; }
            @Override public List<ActionHandler> getActionHandlers() { return handlers; }
            @Override public List<BeforeSaveHook> getBeforeSaveHooks() { return hooks; }
        };
    }

    private ActionHandler stubActionHandler(String key) {
        return new ActionHandler() {
            @Override public String getActionTypeKey() { return key; }
            @Override public ActionResult execute(ActionContext context) { return ActionResult.success(); }
        };
    }

    private BeforeSaveHook stubBeforeSaveHook(String collectionName, int order) {
        return new BeforeSaveHook() {
            @Override public String getCollectionName() { return collectionName; }
            @Override public int getOrder() { return order; }
        };
    }
}
