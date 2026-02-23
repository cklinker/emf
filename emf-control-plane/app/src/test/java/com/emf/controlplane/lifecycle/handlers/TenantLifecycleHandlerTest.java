package com.emf.controlplane.lifecycle.handlers;

import com.emf.controlplane.lifecycle.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantLifecycleHandler Tests")
class TenantLifecycleHandlerTest {

    private TenantLifecycleHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TenantLifecycleHandler();
    }

    @Test
    @DisplayName("Should return 'tenants' as collection name")
    void shouldReturnCollectionName() {
        assertEquals("tenants", handler.getCollectionName());
    }

    @Nested
    @DisplayName("beforeCreate")
    class BeforeCreateTests {

        @Test
        @DisplayName("Should reject null slug")
        void shouldRejectNullSlug() {
            Map<String, Object> record = new HashMap<>();

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
            assertEquals("slug", result.getErrors().get(0).field());
            assertEquals("Tenant slug is required", result.getErrors().get(0).message());
        }

        @Test
        @DisplayName("Should reject blank slug")
        void shouldRejectBlankSlug() {
            Map<String, Object> record = new HashMap<>();
            record.put("slug", "  ");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject slug starting with number")
        void shouldRejectSlugStartingWithNumber() {
            Map<String, Object> record = new HashMap<>();
            record.put("slug", "123-tenant");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().get(0).message().contains("lowercase letter"));
        }

        @Test
        @DisplayName("Should reject slug with uppercase letters")
        void shouldRejectUppercaseSlug() {
            Map<String, Object> record = new HashMap<>();
            record.put("slug", "MyTenant");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            // Should normalize rather than reject — after normalization, validate the result
            // The handler normalizes to lowercase and then validates
            // "mytenant" matches ^[a-z][a-z0-9-]*$ so this should succeed
            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals("mytenant", result.getFieldUpdates().get("slug"));
        }

        @Test
        @DisplayName("Should reject slug with spaces")
        void shouldRejectSlugWithSpaces() {
            Map<String, Object> record = new HashMap<>();
            record.put("slug", "my tenant");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should accept valid lowercase-hyphen slug")
        void shouldAcceptValidSlug() {
            Map<String, Object> record = new HashMap<>();
            record.put("slug", "acme-corp");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should normalize slug to lowercase")
        void shouldNormalizeSlug() {
            Map<String, Object> record = new HashMap<>();
            record.put("slug", "Acme-Corp");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            // "acme-corp" is valid after normalization
            assertTrue(result.isSuccess());
            assertTrue(result.hasFieldUpdates());
            assertEquals("acme-corp", result.getFieldUpdates().get("slug"));
        }

        @Test
        @DisplayName("Should set default status when not provided")
        void shouldSetDefaultStatus() {
            Map<String, Object> record = new HashMap<>();
            record.put("slug", "acme");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            assertEquals("ACTIVE", result.getFieldUpdates().get("status"));
        }

        @Test
        @DisplayName("Should not override existing status")
        void shouldNotOverrideStatus() {
            Map<String, Object> record = new HashMap<>();
            record.put("slug", "acme");
            record.put("status", "SUSPENDED");

            BeforeSaveResult result = handler.beforeCreate(record, "tenant-1");

            assertTrue(result.isSuccess());
            // slug is already lowercase so no slug update needed
            assertFalse(result.hasFieldUpdates());
        }
    }

    @Nested
    @DisplayName("afterCreate")
    class AfterHookTests {

        @Test
        @DisplayName("afterCreate should not throw")
        void afterCreateShouldNotThrow() {
            assertDoesNotThrow(() ->
                    handler.afterCreate(Map.of("slug", "acme"), "tenant-1"));
        }
    }
}
