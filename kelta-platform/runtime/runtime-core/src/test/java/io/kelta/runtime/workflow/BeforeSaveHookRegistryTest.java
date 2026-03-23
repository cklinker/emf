package io.kelta.runtime.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BeforeSaveHookRegistry")
class BeforeSaveHookRegistryTest {

    @Test
    @DisplayName("Should register and retrieve hooks by collection name")
    void shouldRegisterAndRetrieveHooks() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveHook hook = stubHook("users", 0);
        registry.register(hook);

        assertTrue(registry.hasHooks("users"));
        assertEquals(1, registry.getHooks("users").size());
        assertEquals(hook, registry.getHooks("users").get(0));
    }

    @Test
    @DisplayName("Should support multiple hooks per collection")
    void shouldSupportMultipleHooksPerCollection() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveHook h1 = stubHook("users", 10);
        BeforeSaveHook h2 = stubHook("users", 20);
        registry.register(h1);
        registry.register(h2);

        assertTrue(registry.hasHooks("users"));
        assertEquals(2, registry.getHooks("users").size());
        assertEquals(2, registry.getHookCount());
    }

    @Test
    @DisplayName("Should order hooks by getOrder()")
    void shouldOrderHooksByOrder() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveHook high = stubHook("users", 100);
        BeforeSaveHook low = stubHook("users", -10);
        BeforeSaveHook mid = stubHook("users", 50);
        registry.register(high);
        registry.register(low);
        registry.register(mid);

        List<BeforeSaveHook> hooks = registry.getHooks("users");
        assertEquals(-10, hooks.get(0).getOrder());
        assertEquals(50, hooks.get(1).getOrder());
        assertEquals(100, hooks.get(2).getOrder());
    }

    @Test
    @DisplayName("Should initialize with a list of hooks")
    void shouldInitializeWithList() {
        BeforeSaveHook h1 = stubHook("users", 0);
        BeforeSaveHook h2 = stubHook("collections", 0);
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry(List.of(h1, h2));

        assertEquals(2, registry.getHookCount());
        assertTrue(registry.hasHooks("users"));
        assertTrue(registry.hasHooks("collections"));
    }

    @Test
    @DisplayName("Should handle null list in constructor")
    void shouldHandleNullList() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry(null);
        assertEquals(0, registry.getHookCount());
    }

    @Test
    @DisplayName("Should return empty list for unknown collection")
    void shouldReturnEmptyForUnknownCollection() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        assertFalse(registry.hasHooks("unknown"));
        assertTrue(registry.getHooks("unknown").isEmpty());
    }

    @Test
    @DisplayName("Should return registered collection names")
    void shouldReturnRegisteredCollections() {
        BeforeSaveHook h1 = stubHook("users", 0);
        BeforeSaveHook h2 = stubHook("collections", 0);
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry(List.of(h1, h2));

        assertEquals(2, registry.getRegisteredCollections().size());
        assertTrue(registry.getRegisteredCollections().contains("users"));
        assertTrue(registry.getRegisteredCollections().contains("collections"));
    }

    @Test
    @DisplayName("Should evaluate before-create hooks and return OK")
    void shouldEvaluateBeforeCreateOk() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        registry.register(stubHook("users", 0)); // default returns ok()

        Map<String, Object> record = new HashMap<>(Map.of("name", "Test"));
        BeforeSaveResult result = registry.evaluateBeforeCreate("users", record, "t1");

        assertTrue(result.isSuccess());
        assertFalse(result.hasFieldUpdates());
    }

    @Test
    @DisplayName("Should return OK for collection with no hooks")
    void shouldReturnOkForNoHooks() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveResult result = registry.evaluateBeforeCreate("users", new HashMap<>(), "t1");
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Should merge field updates from before-create hooks")
    void shouldMergeFieldUpdatesFromBeforeCreate() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
                return BeforeSaveResult.withFieldUpdates(Map.of("status", "ACTIVE"));
            }
        });

        Map<String, Object> record = new HashMap<>(Map.of("name", "Test"));
        BeforeSaveResult result = registry.evaluateBeforeCreate("users", record, "t1");

        assertTrue(result.isSuccess());
        assertTrue(result.hasFieldUpdates());
        assertEquals("ACTIVE", result.getFieldUpdates().get("status"));
        // Verify record was also updated in place
        assertEquals("ACTIVE", record.get("status"));
    }

    @Test
    @DisplayName("Should short-circuit on first error in before-create")
    void shouldShortCircuitOnError() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        AtomicBoolean secondHookCalled = new AtomicBoolean(false);

        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public int getOrder() { return 1; }
            @Override
            public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
                return BeforeSaveResult.error("name", "Name is required");
            }
        });
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public int getOrder() { return 2; }
            @Override
            public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
                secondHookCalled.set(true);
                return BeforeSaveResult.ok();
            }
        });

        Map<String, Object> record = new HashMap<>();
        BeforeSaveResult result = registry.evaluateBeforeCreate("users", record, "t1");

        assertFalse(result.isSuccess());
        assertTrue(result.hasErrors());
        assertFalse(secondHookCalled.get());
    }

    @Test
    @DisplayName("Should evaluate before-update hooks with error")
    void shouldEvaluateBeforeUpdateWithError() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public BeforeSaveResult beforeUpdate(String id, Map<String, Object> record,
                                                  Map<String, Object> previous, String tenantId) {
                return BeforeSaveResult.error("status", "Cannot deactivate admin user");
            }
        });

        Map<String, Object> record = new HashMap<>(Map.of("status", "INACTIVE"));
        Map<String, Object> previous = Map.of("status", "ACTIVE");
        BeforeSaveResult result = registry.evaluateBeforeUpdate("users", "id1", record, previous, "t1");

        assertFalse(result.isSuccess());
        assertEquals("status", result.getErrors().get(0).field());
    }

    @Test
    @DisplayName("Should merge field updates from multiple before-update hooks")
    void shouldMergeFieldUpdatesFromMultipleHooks() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public int getOrder() { return 1; }
            @Override
            public BeforeSaveResult beforeUpdate(String id, Map<String, Object> record,
                                                  Map<String, Object> previous, String tenantId) {
                return BeforeSaveResult.withFieldUpdates(Map.of("version", 2));
            }
        });
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public int getOrder() { return 2; }
            @Override
            public BeforeSaveResult beforeUpdate(String id, Map<String, Object> record,
                                                  Map<String, Object> previous, String tenantId) {
                return BeforeSaveResult.withFieldUpdates(Map.of("modifiedBy", "system"));
            }
        });

        Map<String, Object> record = new HashMap<>(Map.of("name", "Updated"));
        Map<String, Object> previous = Map.of("name", "Original");
        BeforeSaveResult result = registry.evaluateBeforeUpdate("users", "id1", record, previous, "t1");

        assertTrue(result.isSuccess());
        assertTrue(result.hasFieldUpdates());
        assertEquals(2, result.getFieldUpdates().get("version"));
        assertEquals("system", result.getFieldUpdates().get("modifiedBy"));
    }

    @Test
    @DisplayName("Should invoke after-create hooks without throwing on error")
    void shouldInvokeAfterCreateHooksWithoutThrowing() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        AtomicBoolean hookCalled = new AtomicBoolean(false);

        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public void afterCreate(Map<String, Object> record, String tenantId) {
                hookCalled.set(true);
                throw new RuntimeException("Hook failure");
            }
        });

        // Should not throw even though the hook throws
        assertDoesNotThrow(() -> registry.invokeAfterCreate("users", Map.of("id", "1"), "t1"));
        assertTrue(hookCalled.get());
    }

    @Test
    @DisplayName("Should invoke after-update hooks without throwing on error")
    void shouldInvokeAfterUpdateHooksWithoutThrowing() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        AtomicBoolean hookCalled = new AtomicBoolean(false);

        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public void afterUpdate(String id, Map<String, Object> record,
                                     Map<String, Object> previous, String tenantId) {
                hookCalled.set(true);
            }
        });

        registry.invokeAfterUpdate("users", "id1", Map.of("name", "New"), Map.of("name", "Old"), "t1");
        assertTrue(hookCalled.get());
    }

    @Test
    @DisplayName("Should invoke after-delete hooks without throwing on error")
    void shouldInvokeAfterDeleteHooksWithoutThrowing() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        AtomicBoolean hookCalled = new AtomicBoolean(false);

        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public void afterDelete(String id, String tenantId) {
                hookCalled.set(true);
                throw new RuntimeException("Delete hook failure");
            }
        });

        assertDoesNotThrow(() -> registry.invokeAfterDelete("users", "id1", "t1"));
        assertTrue(hookCalled.get());
    }

    // ==================== Wildcard Hook Tests ====================

    @Test
    @DisplayName("Should include wildcard hooks for any collection")
    void shouldIncludeWildcardHooksForAnyCollection() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveHook wildcardHook = stubHook("*", 1000);
        registry.register(wildcardHook);

        assertTrue(registry.hasHooks("users"));
        assertTrue(registry.hasHooks("collections"));
        assertTrue(registry.hasHooks("any-collection"));

        assertEquals(1, registry.getHooks("users").size());
        assertEquals(wildcardHook, registry.getHooks("users").get(0));
    }

    @Test
    @DisplayName("Should return collection-specific hooks before wildcard hooks")
    void shouldReturnSpecificHooksBeforeWildcardHooks() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveHook specificHook = stubHook("users", 0);
        BeforeSaveHook wildcardHook = stubHook("*", 1000);
        registry.register(wildcardHook);
        registry.register(specificHook);

        List<BeforeSaveHook> hooks = registry.getHooks("users");
        assertEquals(2, hooks.size());
        assertEquals(specificHook, hooks.get(0));
        assertEquals(wildcardHook, hooks.get(1));
    }

    @Test
    @DisplayName("Should not include wildcard hooks when looking up wildcard directly")
    void shouldNotDoubleIncludeWildcardHooks() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        BeforeSaveHook wildcardHook = stubHook("*", 1000);
        registry.register(wildcardHook);

        // Looking up "*" directly should return exactly the wildcard hooks
        List<BeforeSaveHook> hooks = registry.getHooks("*");
        assertEquals(1, hooks.size());
    }

    @Test
    @DisplayName("Should invoke wildcard hooks via afterCreate with collection name")
    void shouldInvokeWildcardAfterCreateWithCollectionName() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        AtomicBoolean hookCalled = new AtomicBoolean(false);
        var capturedCollection = new String[1];

        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "*"; }
            @Override
            public int getOrder() { return 1000; }
            @Override
            public void afterCreate(String collectionName, Map<String, Object> record, String tenantId) {
                hookCalled.set(true);
                capturedCollection[0] = collectionName;
            }
        });

        registry.invokeAfterCreate("users", Map.of("id", "1"), "t1");
        assertTrue(hookCalled.get());
        assertEquals("users", capturedCollection[0]);
    }

    @Test
    @DisplayName("Should invoke wildcard hooks via afterUpdate with collection name")
    void shouldInvokeWildcardAfterUpdateWithCollectionName() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        AtomicBoolean hookCalled = new AtomicBoolean(false);
        var capturedCollection = new String[1];

        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "*"; }
            @Override
            public int getOrder() { return 1000; }
            @Override
            public void afterUpdate(String collectionName, String id, Map<String, Object> record,
                                     Map<String, Object> previous, String tenantId) {
                hookCalled.set(true);
                capturedCollection[0] = collectionName;
            }
        });

        registry.invokeAfterUpdate("fields", "id1", Map.of("name", "New"), Map.of("name", "Old"), "t1");
        assertTrue(hookCalled.get());
        assertEquals("fields", capturedCollection[0]);
    }

    @Test
    @DisplayName("Should invoke wildcard hooks via afterDelete with collection name")
    void shouldInvokeWildcardAfterDeleteWithCollectionName() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        AtomicBoolean hookCalled = new AtomicBoolean(false);
        var capturedCollection = new String[1];

        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "*"; }
            @Override
            public int getOrder() { return 1000; }
            @Override
            public void afterDelete(String collectionName, String id, String tenantId) {
                hookCalled.set(true);
                capturedCollection[0] = collectionName;
            }
        });

        registry.invokeAfterDelete("profiles", "id1", "t1");
        assertTrue(hookCalled.get());
        assertEquals("profiles", capturedCollection[0]);
    }

    @Test
    @DisplayName("Should hasHooks return true when only wildcard hooks exist")
    void shouldHasHooksReturnTrueForWildcardOnly() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        registry.register(stubHook("*", 1000));

        assertTrue(registry.hasHooks("any-collection"));
        assertTrue(registry.hasHooks("*")); // hasHooks for "*" checks the direct key
    }

    @Test
    @DisplayName("Should evaluate before-create with both specific and wildcard hooks")
    void shouldEvaluateBeforeCreateWithSpecificAndWildcardHooks() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public int getOrder() { return 0; }
            @Override
            public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
                return BeforeSaveResult.withFieldUpdates(Map.of("status", "ACTIVE"));
            }
        });
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "*"; }
            @Override
            public int getOrder() { return 1000; }
            @Override
            public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
                return BeforeSaveResult.withFieldUpdates(Map.of("audit", "tracked"));
            }
        });

        Map<String, Object> record = new HashMap<>(Map.of("name", "Test"));
        BeforeSaveResult result = registry.evaluateBeforeCreate("users", record, "t1");

        assertTrue(result.isSuccess());
        assertTrue(result.hasFieldUpdates());
        assertEquals("ACTIVE", result.getFieldUpdates().get("status"));
        assertEquals("tracked", result.getFieldUpdates().get("audit"));
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
