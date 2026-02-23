package com.emf.runtime.workflow.module;

import com.emf.runtime.workflow.ActionHandlerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModuleContext")
class ModuleContextTest {

    @Test
    @DisplayName("Should support backward-compatible 4-arg constructor")
    void shouldSupportBackwardCompatibleConstructor() {
        ModuleContext context = new ModuleContext(null, null, null, null);

        assertNull(context.queryEngine());
        assertNull(context.collectionRegistry());
        assertNull(context.formulaEvaluator());
        assertNull(context.objectMapper());
        assertNull(context.actionHandlerRegistry());
        assertNotNull(context.extensions());
        assertTrue(context.extensions().isEmpty());
    }

    @Test
    @DisplayName("Should support full 6-arg constructor with actionHandlerRegistry")
    void shouldSupportFullConstructorWithActionHandlerRegistry() {
        ActionHandlerRegistry registry = new ActionHandlerRegistry();
        ModuleContext context = new ModuleContext(null, null, null, null, registry, Map.of());

        assertSame(registry, context.actionHandlerRegistry());
    }

    @Test
    @DisplayName("Should store and retrieve extensions by type")
    void shouldStoreAndRetrieveExtensionsByType() {
        String testService = "test-service-value";
        Map<Class<?>, Object> extensions = Map.of(String.class, testService);

        ModuleContext context = new ModuleContext(null, null, null, null, null, extensions);

        assertEquals("test-service-value", context.getExtension(String.class));
    }

    @Test
    @DisplayName("Should return null for missing extension")
    void shouldReturnNullForMissingExtension() {
        ModuleContext context = new ModuleContext(null, null, null, null);

        assertNull(context.getExtension(String.class));
    }

    @Test
    @DisplayName("Should handle null extensions map gracefully")
    void shouldHandleNullExtensionsMap() {
        ModuleContext context = new ModuleContext(null, null, null, null, null, null);

        assertNotNull(context.extensions());
        assertTrue(context.extensions().isEmpty());
        assertNull(context.getExtension(String.class));
    }

    @Test
    @DisplayName("Should return unmodifiable extensions map")
    void shouldReturnUnmodifiableExtensionsMap() {
        ModuleContext context = new ModuleContext(null, null, null, null, null, Map.of(String.class, "value"));

        assertThrows(UnsupportedOperationException.class, () ->
            context.extensions().put(Integer.class, 42));
    }

    @Test
    @DisplayName("Should support multiple extension types")
    void shouldSupportMultipleExtensionTypes() {
        Map<Class<?>, Object> extensions = Map.of(
            String.class, "string-service",
            Integer.class, 42
        );
        ModuleContext context = new ModuleContext(null, null, null, null, null, extensions);

        assertEquals("string-service", context.getExtension(String.class));
        assertEquals(42, context.getExtension(Integer.class));
        assertNull(context.getExtension(Double.class));
    }
}
