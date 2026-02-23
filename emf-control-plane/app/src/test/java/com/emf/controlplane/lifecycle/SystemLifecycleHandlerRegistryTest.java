package com.emf.controlplane.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SystemLifecycleHandlerRegistry Tests")
class SystemLifecycleHandlerRegistryTest {

    private SystemCollectionLifecycleHandler createHandler(String collectionName) {
        return new SystemCollectionLifecycleHandler() {
            @Override
            public String getCollectionName() {
                return collectionName;
            }
        };
    }

    @Test
    @DisplayName("Should register discovered handlers")
    void shouldRegisterHandlers() {
        SystemCollectionLifecycleHandler usersHandler = createHandler("users");
        SystemCollectionLifecycleHandler collectionsHandler = createHandler("collections");

        SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(
                List.of(usersHandler, collectionsHandler));

        assertEquals(2, registry.getHandlerCount());
        assertTrue(registry.hasHandler("users"));
        assertTrue(registry.hasHandler("collections"));
    }

    @Test
    @DisplayName("Should return handler by collection name")
    void shouldReturnHandlerByName() {
        SystemCollectionLifecycleHandler usersHandler = createHandler("users");
        SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(
                List.of(usersHandler));

        Optional<SystemCollectionLifecycleHandler> result = registry.getHandler("users");

        assertTrue(result.isPresent());
        assertSame(usersHandler, result.get());
    }

    @Test
    @DisplayName("Should return empty for unregistered collection")
    void shouldReturnEmptyForUnregistered() {
        SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(List.of());

        assertFalse(registry.hasHandler("nonexistent"));
        assertTrue(registry.getHandler("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("Should handle null handler list")
    void shouldHandleNullList() {
        SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(null);

        assertEquals(0, registry.getHandlerCount());
        assertFalse(registry.hasHandler("users"));
    }

    @Test
    @DisplayName("Should handle empty handler list")
    void shouldHandleEmptyList() {
        SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(List.of());

        assertEquals(0, registry.getHandlerCount());
        assertTrue(registry.getRegisteredCollections().isEmpty());
    }

    @Test
    @DisplayName("Should return all registered collection names")
    void shouldReturnRegisteredCollections() {
        SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(
                List.of(createHandler("users"), createHandler("profiles"), createHandler("fields")));

        var collections = registry.getRegisteredCollections();
        assertEquals(3, collections.size());
        assertTrue(collections.contains("users"));
        assertTrue(collections.contains("profiles"));
        assertTrue(collections.contains("fields"));
    }

    @Test
    @DisplayName("Should handle duplicate handler for same collection (last wins)")
    void shouldHandleDuplicateHandlers() {
        SystemCollectionLifecycleHandler first = createHandler("users");
        SystemCollectionLifecycleHandler second = createHandler("users");

        SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(
                List.of(first, second));

        // Last registered handler wins
        assertEquals(1, registry.getHandlerCount());
        assertTrue(registry.hasHandler("users"));
        assertSame(second, registry.getHandler("users").get());
    }

    @Test
    @DisplayName("getRegisteredCollections should return unmodifiable set")
    void registeredCollectionsShouldBeUnmodifiable() {
        SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(
                List.of(createHandler("users")));

        assertThrows(UnsupportedOperationException.class,
                () -> registry.getRegisteredCollections().add("extra"));
    }

    @Test
    @DisplayName("Default interface methods should return ok/no-op")
    void defaultMethodsShouldBeNoOp() {
        SystemCollectionLifecycleHandler handler = createHandler("test");

        // beforeCreate default returns ok
        BeforeSaveResult createResult = handler.beforeCreate(Map.of(), "tenant-1");
        assertTrue(createResult.isSuccess());
        assertFalse(createResult.hasFieldUpdates());

        // beforeUpdate default returns ok
        BeforeSaveResult updateResult = handler.beforeUpdate("id", Map.of(), Map.of(), "tenant-1");
        assertTrue(updateResult.isSuccess());

        // after methods should not throw
        assertDoesNotThrow(() -> handler.afterCreate(Map.of(), "tenant-1"));
        assertDoesNotThrow(() -> handler.afterUpdate("id", Map.of(), Map.of(), "tenant-1"));
        assertDoesNotThrow(() -> handler.afterDelete("id", "tenant-1"));
    }
}
