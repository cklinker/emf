package io.kelta.runtime.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the before-delete hook path: {@link BeforeSaveHook#beforeDelete(String, String)},
 * its collection-name-aware variant, and
 * {@link BeforeSaveHookRegistry#evaluateBeforeDelete(String, String, String)}.
 */
@DisplayName("BeforeSaveHookRegistry before-delete")
class BeforeSaveHookRegistryBeforeDeleteTest {

    @Test
    @DisplayName("Should return OK when no hooks are registered")
    void shouldReturnOkWhenNoHooksRegistered() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();

        BeforeSaveResult result = registry.evaluateBeforeDelete("users", "id1", "t1");

        assertTrue(result.isSuccess());
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("Should return the error when a collection-specific hook vetoes the delete")
    void shouldReturnErrorWhenCollectionSpecificHookVetoes() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public BeforeSaveResult beforeDelete(String id, String tenantId) {
                return BeforeSaveResult.error("id", "Cannot delete the last admin user");
            }
        });

        BeforeSaveResult result = registry.evaluateBeforeDelete("users", "id1", "t1");

        assertFalse(result.isSuccess());
        assertTrue(result.hasErrors());
        assertEquals("id", result.getErrors().get(0).field());
        assertEquals("Cannot delete the last admin user", result.getErrors().get(0).message());
    }

    @Test
    @DisplayName("Should not veto other collections from a collection-specific hook")
    void shouldNotVetoOtherCollections() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public BeforeSaveResult beforeDelete(String id, String tenantId) {
                return BeforeSaveResult.error("id", "Blocked");
            }
        });

        BeforeSaveResult result = registry.evaluateBeforeDelete("collections", "id1", "t1");

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Should invoke wildcard hook beforeDelete for any collection and allow veto")
    void shouldInvokeWildcardBeforeDeleteForAnyCollectionAndVeto() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        var capturedCollection = new String[1];
        var capturedId = new String[1];
        var capturedTenant = new String[1];

        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "*"; }
            @Override
            public BeforeSaveResult beforeDelete(String collectionName, String id, String tenantId) {
                capturedCollection[0] = collectionName;
                capturedId[0] = id;
                capturedTenant[0] = tenantId;
                return BeforeSaveResult.error(null, "Delete blocked by wildcard hook");
            }
        });

        BeforeSaveResult result = registry.evaluateBeforeDelete("profiles", "id42", "t1");

        assertFalse(result.isSuccess());
        assertEquals("Delete blocked by wildcard hook", result.getErrors().get(0).message());
        assertEquals("profiles", capturedCollection[0]);
        assertEquals("id42", capturedId[0]);
        assertEquals("t1", capturedTenant[0]);

        // Wildcard hooks are invoked for every collection
        BeforeSaveResult other = registry.evaluateBeforeDelete("any-collection", "id43", "t1");
        assertFalse(other.isSuccess());
        assertEquals("any-collection", capturedCollection[0]);
    }

    @Test
    @DisplayName("Default beforeDelete implementations should return OK (backward compatibility)")
    void shouldReturnOkFromDefaultBeforeDelete() {
        // A hook that overrides nothing but getCollectionName — pre-beforeDelete behavior
        BeforeSaveHook legacyHook = new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
        };

        // Both default variants return ok()
        assertTrue(legacyHook.beforeDelete("id1", "t1").isSuccess());
        assertTrue(legacyHook.beforeDelete("users", "id1", "t1").isSuccess());

        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        registry.register(legacyHook);

        BeforeSaveResult result = registry.evaluateBeforeDelete("users", "id1", "t1");
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Collection-name-aware default should delegate to legacy beforeDelete")
    void shouldDelegateCollectionNameDefaultToLegacyBeforeDelete() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        AtomicBoolean legacyCalled = new AtomicBoolean(false);

        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public BeforeSaveResult beforeDelete(String id, String tenantId) {
                legacyCalled.set(true);
                return BeforeSaveResult.error("id", "Vetoed via legacy variant");
            }
        });

        BeforeSaveResult result = registry.evaluateBeforeDelete("users", "id1", "t1");

        assertTrue(legacyCalled.get());
        assertFalse(result.isSuccess());
        assertEquals("Vetoed via legacy variant", result.getErrors().get(0).message());
    }

    @Test
    @DisplayName("Should return first error across multiple hooks ordered by getOrder()")
    void shouldReturnFirstErrorAcrossHooksOrderedByOrder() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        AtomicBoolean laterHookCalled = new AtomicBoolean(false);

        // Registered first, but higher order — must run second
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public int getOrder() { return 10; }
            @Override
            public BeforeSaveResult beforeDelete(String id, String tenantId) {
                laterHookCalled.set(true);
                return BeforeSaveResult.error("id", "Second error");
            }
        });
        // Registered second, lower order — must run first and win
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public int getOrder() { return -10; }
            @Override
            public BeforeSaveResult beforeDelete(String id, String tenantId) {
                return BeforeSaveResult.error("id", "First error");
            }
        });

        BeforeSaveResult result = registry.evaluateBeforeDelete("users", "id1", "t1");

        assertFalse(result.isSuccess());
        assertEquals("First error", result.getErrors().get(0).message());
        assertFalse(laterHookCalled.get(), "evaluation should short-circuit on the first error");
    }

    @Test
    @DisplayName("Should run collection-specific hooks before wildcard hooks on delete")
    void shouldRunSpecificHooksBeforeWildcardOnDelete() {
        BeforeSaveHookRegistry registry = new BeforeSaveHookRegistry();
        AtomicBoolean wildcardCalled = new AtomicBoolean(false);

        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "*"; }
            @Override
            public int getOrder() { return -1000; } // Order within group; wildcard still runs last
            @Override
            public BeforeSaveResult beforeDelete(String collectionName, String id, String tenantId) {
                wildcardCalled.set(true);
                return BeforeSaveResult.error(null, "Wildcard error");
            }
        });
        registry.register(new BeforeSaveHook() {
            @Override
            public String getCollectionName() { return "users"; }
            @Override
            public BeforeSaveResult beforeDelete(String id, String tenantId) {
                return BeforeSaveResult.error("id", "Specific error");
            }
        });

        BeforeSaveResult result = registry.evaluateBeforeDelete("users", "id1", "t1");

        assertFalse(result.isSuccess());
        assertEquals("Specific error", result.getErrors().get(0).message());
        assertFalse(wildcardCalled.get(), "collection-specific error should short-circuit before wildcard");
    }
}
