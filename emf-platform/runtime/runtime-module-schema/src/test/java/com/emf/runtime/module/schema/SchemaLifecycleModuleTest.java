package com.emf.runtime.module.schema;

import com.emf.runtime.module.schema.hooks.TenantLifecycleHook;
import com.emf.runtime.workflow.BeforeSaveHook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SchemaLifecycleModule")
class SchemaLifecycleModuleTest {

    private final SchemaLifecycleModule module = new SchemaLifecycleModule();

    @Test
    @DisplayName("Should have correct module metadata")
    void shouldHaveCorrectMetadata() {
        assertEquals("emf-schema", module.getId());
        assertEquals("Schema Lifecycle Module", module.getName());
        assertEquals("1.0.0", module.getVersion());
    }

    @Test
    @DisplayName("Should provide 5 before-save hooks")
    void shouldProvide5Hooks() {
        List<BeforeSaveHook> hooks = module.getBeforeSaveHooks();
        assertEquals(5, hooks.size());
    }

    @Test
    @DisplayName("Should cover all expected system collections")
    void shouldCoverExpectedCollections() {
        Set<String> collections = module.getBeforeSaveHooks().stream()
                .map(BeforeSaveHook::getCollectionName)
                .collect(Collectors.toSet());

        assertTrue(collections.contains("collections"));
        assertTrue(collections.contains("fields"));
        assertTrue(collections.contains("tenants"));
        assertTrue(collections.contains("users"));
        assertTrue(collections.contains("profiles"));
    }

    @Test
    @DisplayName("Should have no action handlers")
    void shouldHaveNoActionHandlers() {
        assertTrue(module.getActionHandlers().isEmpty());
    }

    @Nested
    @DisplayName("tenant schema callback")
    class TenantSchemaCallback {

        @Test
        @DisplayName("Should wire callback to TenantLifecycleHook when provided")
        void shouldWireCallbackToTenantHook() {
            List<String> captured = new ArrayList<>();
            SchemaLifecycleModule moduleWithCallback = new SchemaLifecycleModule(captured::add);

            // Find the tenant hook and trigger afterCreate
            BeforeSaveHook tenantHook = moduleWithCallback.getBeforeSaveHooks().stream()
                    .filter(h -> "tenants".equals(h.getCollectionName()))
                    .findFirst()
                    .orElseThrow();

            tenantHook.afterCreate(new HashMap<>(Map.of("slug", "test-tenant")), "t1");

            assertEquals(1, captured.size());
            assertEquals("test-tenant", captured.get(0));
        }

        @Test
        @DisplayName("Should not wire callback when null")
        void shouldNotWireCallbackWhenNull() {
            SchemaLifecycleModule moduleNoCallback = new SchemaLifecycleModule(null);

            BeforeSaveHook tenantHook = moduleNoCallback.getBeforeSaveHooks().stream()
                    .filter(h -> "tenants".equals(h.getCollectionName()))
                    .findFirst()
                    .orElseThrow();

            // Should not throw — no callback set
            assertDoesNotThrow(() ->
                    tenantHook.afterCreate(new HashMap<>(Map.of("slug", "test")), "t1"));
        }
    }
}
