package com.emf.runtime.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActionHandlerRegistry")
class ActionHandlerRegistryTest {

    @Test
    @DisplayName("Should register and retrieve handlers by key")
    void shouldRegisterAndRetrieveHandlers() {
        ActionHandlerRegistry registry = new ActionHandlerRegistry();
        ActionHandler handler = stubHandler("FIELD_UPDATE");
        registry.register(handler);

        assertTrue(registry.hasHandler("FIELD_UPDATE"));
        assertEquals(handler, registry.getHandler("FIELD_UPDATE").orElseThrow());
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("Should initialize with a list of handlers")
    void shouldInitializeWithList() {
        ActionHandler h1 = stubHandler("FIELD_UPDATE");
        ActionHandler h2 = stubHandler("EMAIL_ALERT");
        ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(h1, h2));

        assertEquals(2, registry.size());
        assertTrue(registry.hasHandler("FIELD_UPDATE"));
        assertTrue(registry.hasHandler("EMAIL_ALERT"));
    }

    @Test
    @DisplayName("Should return empty for unknown handler key")
    void shouldReturnEmptyForUnknownKey() {
        ActionHandlerRegistry registry = new ActionHandlerRegistry();
        assertTrue(registry.getHandler("UNKNOWN").isEmpty());
        assertFalse(registry.hasHandler("UNKNOWN"));
    }

    @Test
    @DisplayName("Should replace handler with duplicate key")
    void shouldReplaceDuplicateKey() {
        ActionHandlerRegistry registry = new ActionHandlerRegistry();
        ActionHandler h1 = stubHandler("FIELD_UPDATE");
        ActionHandler h2 = stubHandler("FIELD_UPDATE");
        registry.register(h1);
        registry.register(h2);

        assertEquals(1, registry.size());
        assertEquals(h2, registry.getHandler("FIELD_UPDATE").orElseThrow());
    }

    @Test
    @DisplayName("Should return all registered keys")
    void shouldReturnRegisteredKeys() {
        ActionHandler h1 = stubHandler("A");
        ActionHandler h2 = stubHandler("B");
        ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(h1, h2));

        assertEquals(2, registry.getRegisteredKeys().size());
        assertTrue(registry.getRegisteredKeys().contains("A"));
        assertTrue(registry.getRegisteredKeys().contains("B"));
    }

    @Test
    @DisplayName("Should handle null list in constructor")
    void shouldHandleNullList() {
        ActionHandlerRegistry registry = new ActionHandlerRegistry(null);
        assertEquals(0, registry.size());
    }

    private ActionHandler stubHandler(String key) {
        return new ActionHandler() {
            @Override
            public String getActionTypeKey() { return key; }
            @Override
            public ActionResult execute(ActionContext context) { return ActionResult.success(); }
        };
    }
}
