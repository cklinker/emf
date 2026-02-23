package com.emf.runtime.module.schema.hooks;

import com.emf.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantLifecycleHook")
class TenantLifecycleHookTest {

    private final TenantLifecycleHook hook = new TenantLifecycleHook();

    @Test
    @DisplayName("Should target 'tenants' collection")
    void shouldTargetTenants() {
        assertEquals("tenants", hook.getCollectionName());
    }

    @Nested
    @DisplayName("beforeCreate")
    class BeforeCreate {

        @Test
        @DisplayName("Should reject missing slug")
        void shouldRejectMissingSlug() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(), "t1");
            assertFalse(result.isSuccess());
            assertEquals("slug", result.getErrors().get(0).field());
        }

        @Test
        @DisplayName("Should reject blank slug")
        void shouldRejectBlankSlug() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("slug", "  ")), "t1");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject slug starting with number")
        void shouldRejectSlugStartingWithNumber() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("slug", "123tenant")), "t1");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject slug with underscores")
        void shouldRejectSlugWithUnderscores() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("slug", "my_tenant")), "t1");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept valid slug")
        void shouldAcceptValidSlug() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("slug", "acme-corp")), "t1");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should normalize slug to lowercase")
        void shouldNormalizeSlugToLowercase() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("slug", "Acme-Corp")), "t1");
            // "Acme-Corp" lowered -> "acme-corp" which is valid
            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals("acme-corp", result.getFieldUpdates().get("slug"));
        }

        @Test
        @DisplayName("Should set default status to ACTIVE")
        void shouldSetDefaultStatus() {
            BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("slug", "acme")), "t1");
            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals("ACTIVE", result.getFieldUpdates().get("status"));
        }

        @Test
        @DisplayName("Should not override existing status")
        void shouldNotOverrideExistingStatus() {
            Map<String, Object> record = new HashMap<>(Map.of("slug", "acme", "status", "INACTIVE"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
            assertNull(result.getFieldUpdates().get("status"));
        }

        @Test
        @DisplayName("Should return ok when all defaults present and slug already lowercase")
        void shouldReturnOkWhenDefaultsPresent() {
            Map<String, Object> record = new HashMap<>(Map.of("slug", "acme", "status", "ACTIVE"));
            BeforeSaveResult result = hook.beforeCreate(record, "t1");
            assertTrue(result.isSuccess());
            assertFalse(result.hasFieldUpdates());
        }
    }
}
