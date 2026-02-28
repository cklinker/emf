package com.emf.runtime.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionHandlerRegistryTenantTest {

    private ActionHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ActionHandlerRegistry();
    }

    @Test
    void shouldRegisterAndRetrieveTenantHandler() {
        ActionHandler handler = mockHandler("stripe:charge");
        registry.registerTenantHandler("tenant-1", handler);

        Optional<ActionHandler> found = registry.getHandler("tenant-1", "stripe:charge");
        assertTrue(found.isPresent());
        assertEquals(handler, found.get());
    }

    @Test
    void shouldFallBackToGlobalHandler() {
        ActionHandler globalHandler = mockHandler("FIELD_UPDATE");
        registry.register(globalHandler);

        Optional<ActionHandler> found = registry.getHandler("tenant-1", "FIELD_UPDATE");
        assertTrue(found.isPresent());
        assertEquals(globalHandler, found.get());
    }

    @Test
    void shouldPreferTenantHandlerOverGlobal() {
        ActionHandler globalHandler = mockHandler("CUSTOM_ACTION");
        ActionHandler tenantHandler = mockHandler("CUSTOM_ACTION");
        registry.register(globalHandler);
        registry.registerTenantHandler("tenant-1", tenantHandler);

        Optional<ActionHandler> found = registry.getHandler("tenant-1", "CUSTOM_ACTION");
        assertTrue(found.isPresent());
        assertEquals(tenantHandler, found.get());

        // Different tenant should still get global
        Optional<ActionHandler> other = registry.getHandler("tenant-2", "CUSTOM_ACTION");
        assertTrue(other.isPresent());
        assertEquals(globalHandler, other.get());
    }

    @Test
    void shouldRemoveTenantHandlers() {
        ActionHandler handler1 = mockHandler("action1");
        ActionHandler handler2 = mockHandler("action2");
        registry.registerTenantHandler("tenant-1", handler1);
        registry.registerTenantHandler("tenant-1", handler2);

        registry.removeTenantHandlers("tenant-1", Set.of("action1"));

        assertFalse(registry.getHandler("tenant-1", "action1").isPresent());
        assertTrue(registry.getHandler("tenant-1", "action2").isPresent());
    }

    @Test
    void shouldCleanUpEmptyTenantMap() {
        ActionHandler handler = mockHandler("action1");
        registry.registerTenantHandler("tenant-1", handler);

        registry.removeTenantHandlers("tenant-1", Set.of("action1"));

        // Should not fail for removed tenant
        assertFalse(registry.getHandler("tenant-1", "action1").isPresent());
    }

    @Test
    void shouldReturnEmptyForUnknownHandler() {
        assertFalse(registry.getHandler("tenant-1", "unknown").isPresent());
    }

    @Test
    void shouldIncludeTenantKeysInRegisteredKeys() {
        ActionHandler global = mockHandler("GLOBAL");
        ActionHandler tenant = mockHandler("TENANT");
        registry.register(global);
        registry.registerTenantHandler("tenant-1", tenant);

        Set<String> keys = registry.getRegisteredKeys("tenant-1");
        assertTrue(keys.contains("GLOBAL"));
        assertTrue(keys.contains("TENANT"));

        // Different tenant should only have global
        Set<String> otherKeys = registry.getRegisteredKeys("tenant-2");
        assertTrue(otherKeys.contains("GLOBAL"));
        assertFalse(otherKeys.contains("TENANT"));
    }

    @Test
    void shouldHandleNullTenantInGetRegisteredKeys() {
        ActionHandler global = mockHandler("GLOBAL");
        registry.register(global);

        Set<String> keys = registry.getRegisteredKeys(null);
        assertTrue(keys.contains("GLOBAL"));
    }

    @Test
    void shouldHandleRemoveFromNonExistentTenant() {
        // Should not throw
        registry.removeTenantHandlers("nonexistent", Set.of("action1"));
    }

    private ActionHandler mockHandler(String key) {
        ActionHandler handler = mock(ActionHandler.class);
        when(handler.getActionTypeKey()).thenReturn(key);
        return handler;
    }
}
