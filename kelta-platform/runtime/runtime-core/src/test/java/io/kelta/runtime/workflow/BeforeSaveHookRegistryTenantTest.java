package io.kelta.runtime.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tenant-scoped hook registration — the seam a runtime-installed module registers through.
 *
 * <p>The load-bearing property is isolation: tenant A installing a module must never make its
 * hooks fire on tenant B's records, because a hook can veto a save.
 */
@DisplayName("BeforeSaveHookRegistry — tenant-scoped hooks")
class BeforeSaveHookRegistryTenantTest {

    @Test
    @DisplayName("A tenant hook fires for its own tenant and not for another")
    void tenantHookIsIsolatedToItsTenant() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveHook hook = vetoHook("orders");
        registry.registerTenantHook("t1", hook);

        assertFalse(registry.evaluateBeforeCreate("orders", new HashMap<>(), "t1").isSuccess());
        assertTrue(registry.evaluateBeforeCreate("orders", new HashMap<>(), "t2").isSuccess());
    }

    @Test
    @DisplayName("A tenant hook does not leak into the global registry")
    void tenantHookIsNotGlobal() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        registry.registerTenantHook("t1", vetoHook("orders"));

        assertFalse(registry.hasHooks("orders"));
        assertTrue(registry.getHooks("orders").isEmpty());
        assertEquals(0, registry.getHookCount());

        assertTrue(registry.hasHooks("t1", "orders"));
        assertFalse(registry.hasHooks("t2", "orders"));
    }

    @Test
    @DisplayName("Tenant hooks run before global hooks for the same collection")
    void tenantHooksRunBeforeGlobalHooks() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveHook global = stubHook("orders", 0);
        BeforeSaveHook tenant = stubHook("orders", 0);
        registry.register(global);
        registry.registerTenantHook("t1", tenant);

        List<BeforeSaveHook> hooks = registry.getHooks("t1", "orders");

        assertEquals(2, hooks.size());
        assertEquals(tenant, hooks.get(0));
        assertEquals(global, hooks.get(1));
    }

    @Test
    @DisplayName("A tenant wildcard hook fires on any collection for that tenant only")
    void tenantWildcardHookApplies() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        registry.registerTenantHook("t1", vetoHook(BeforeSaveHookRegistry.WILDCARD));

        assertTrue(registry.hasHooks("t1", "anything"));
        assertFalse(registry.evaluateBeforeCreate("anything", new HashMap<>(), "t1").isSuccess());
        assertTrue(registry.evaluateBeforeCreate("anything", new HashMap<>(), "t2").isSuccess());
    }

    @Test
    @DisplayName("Removing tenant hooks stops them firing")
    void removingTenantHooksStopsThemFiring() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveHook hook = vetoHook("orders");
        registry.registerTenantHook("t1", hook);
        assertFalse(registry.evaluateBeforeCreate("orders", new HashMap<>(), "t1").isSuccess());

        registry.removeTenantHooks("t1", List.of(hook));

        assertTrue(registry.evaluateBeforeCreate("orders", new HashMap<>(), "t1").isSuccess());
        assertFalse(registry.hasHooks("t1", "orders"));
    }

    @Test
    @DisplayName("Removing one tenant's hooks leaves another tenant's identical hook registered")
    void removalIsScopedToOneTenant() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveHook first = vetoHook("orders");
        BeforeSaveHook second = vetoHook("orders");
        registry.registerTenantHook("t1", first);
        registry.registerTenantHook("t2", second);

        registry.removeTenantHooks("t1", List.of(first));

        assertTrue(registry.evaluateBeforeCreate("orders", new HashMap<>(), "t1").isSuccess());
        assertFalse(registry.evaluateBeforeCreate("orders", new HashMap<>(), "t2").isSuccess());
    }

    @Test
    @DisplayName("A null tenant falls back to global hooks only")
    void nullTenantUsesGlobalHooksOnly() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveHook global = stubHook("orders", 0);
        registry.register(global);
        registry.registerTenantHook("t1", vetoHook("orders"));

        assertEquals(List.of(global), registry.getHooks(null, "orders"));
        assertTrue(registry.evaluateBeforeCreate("orders", new HashMap<>(), null).isSuccess());
    }

    private BeforeSaveHook vetoHook(String collectionName) {
        return new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return collectionName; }
            @Override
            public BeforeSaveResult beforeCreate(String name, Map<String, Object> record,
                                                 String tenantId) {
                return BeforeSaveResult.error("_record", "vetoed");
            }
        };
    }

    private BeforeSaveHook stubHook(String collectionName, int order) {
        return new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return collectionName; }
            @Override
            public int getOrder() { return order; }
        };
    }
}
