package com.emf.runtime.module.schema;

import com.emf.runtime.workflow.BeforeSaveHook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
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
}
