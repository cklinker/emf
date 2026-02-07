package com.emf.controlplane.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TenantContextHolder.
 */
class TenantContextHolderTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("set and get")
    class SetAndGetTests {

        @Test
        @DisplayName("should store and retrieve tenant context")
        void shouldStoreAndRetrieveTenantContext() {
            TenantContextHolder.set("tenant-123", "acme");

            TenantContextHolder.TenantContext ctx = TenantContextHolder.get();
            assertThat(ctx).isNotNull();
            assertThat(ctx.tenantId()).isEqualTo("tenant-123");
            assertThat(ctx.tenantSlug()).isEqualTo("acme");
        }

        @Test
        @DisplayName("should return null when not set")
        void shouldReturnNullWhenNotSet() {
            assertThat(TenantContextHolder.get()).isNull();
        }

        @Test
        @DisplayName("should overwrite previous context")
        void shouldOverwritePreviousContext() {
            TenantContextHolder.set("tenant-1", "first");
            TenantContextHolder.set("tenant-2", "second");

            assertThat(TenantContextHolder.getTenantId()).isEqualTo("tenant-2");
            assertThat(TenantContextHolder.getTenantSlug()).isEqualTo("second");
        }
    }

    @Nested
    @DisplayName("getTenantId")
    class GetTenantIdTests {

        @Test
        @DisplayName("should return tenant ID when set")
        void shouldReturnTenantIdWhenSet() {
            TenantContextHolder.set("tenant-123", "acme");
            assertThat(TenantContextHolder.getTenantId()).isEqualTo("tenant-123");
        }

        @Test
        @DisplayName("should return null when not set")
        void shouldReturnNullWhenNotSet() {
            assertThat(TenantContextHolder.getTenantId()).isNull();
        }
    }

    @Nested
    @DisplayName("getTenantSlug")
    class GetTenantSlugTests {

        @Test
        @DisplayName("should return tenant slug when set")
        void shouldReturnTenantSlugWhenSet() {
            TenantContextHolder.set("tenant-123", "acme");
            assertThat(TenantContextHolder.getTenantSlug()).isEqualTo("acme");
        }

        @Test
        @DisplayName("should return null when not set")
        void shouldReturnNullWhenNotSet() {
            assertThat(TenantContextHolder.getTenantSlug()).isNull();
        }
    }

    @Nested
    @DisplayName("clear")
    class ClearTests {

        @Test
        @DisplayName("should remove tenant context")
        void shouldRemoveTenantContext() {
            TenantContextHolder.set("tenant-123", "acme");
            TenantContextHolder.clear();

            assertThat(TenantContextHolder.get()).isNull();
            assertThat(TenantContextHolder.getTenantId()).isNull();
            assertThat(TenantContextHolder.getTenantSlug()).isNull();
        }

        @Test
        @DisplayName("should not throw when clearing unset context")
        void shouldNotThrowWhenClearingUnsetContext() {
            TenantContextHolder.clear(); // should not throw
            assertThat(TenantContextHolder.get()).isNull();
        }
    }

    @Nested
    @DisplayName("isSet")
    class IsSetTests {

        @Test
        @DisplayName("should return true when context is set")
        void shouldReturnTrueWhenSet() {
            TenantContextHolder.set("tenant-123", "acme");
            assertThat(TenantContextHolder.isSet()).isTrue();
        }

        @Test
        @DisplayName("should return false when context is not set")
        void shouldReturnFalseWhenNotSet() {
            assertThat(TenantContextHolder.isSet()).isFalse();
        }

        @Test
        @DisplayName("should return false after clear")
        void shouldReturnFalseAfterClear() {
            TenantContextHolder.set("tenant-123", "acme");
            TenantContextHolder.clear();
            assertThat(TenantContextHolder.isSet()).isFalse();
        }
    }

    @Test
    @DisplayName("should isolate context between threads")
    void shouldIsolateContextBetweenThreads() throws InterruptedException {
        TenantContextHolder.set("main-tenant", "main-slug");

        Thread otherThread = new Thread(() -> {
            assertThat(TenantContextHolder.isSet()).isFalse();
            TenantContextHolder.set("other-tenant", "other-slug");
            assertThat(TenantContextHolder.getTenantId()).isEqualTo("other-tenant");
            TenantContextHolder.clear();
        });
        otherThread.start();
        otherThread.join();

        // Main thread context unchanged
        assertThat(TenantContextHolder.getTenantId()).isEqualTo("main-tenant");
    }
}
