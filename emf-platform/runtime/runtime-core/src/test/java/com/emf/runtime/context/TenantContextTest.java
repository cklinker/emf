package com.emf.runtime.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantContext")
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should store and retrieve tenant ID")
    void shouldStoreAndRetrieveTenantId() {
        TenantContext.set("tenant-123");
        assertEquals("tenant-123", TenantContext.get());
    }

    @Test
    @DisplayName("Should store and retrieve tenant slug")
    void shouldStoreAndRetrieveSlug() {
        TenantContext.setSlug("acme-corp");
        assertEquals("acme-corp", TenantContext.getSlug());
    }

    @Test
    @DisplayName("Should return null when tenant ID not set")
    void shouldReturnNullWhenNotSet() {
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("Should return null when slug not set")
    void shouldReturnNullWhenSlugNotSet() {
        assertNull(TenantContext.getSlug());
    }

    @Test
    @DisplayName("Should clear both tenant ID and slug")
    void shouldClearBoth() {
        TenantContext.set("tenant-123");
        TenantContext.setSlug("acme-corp");
        TenantContext.clear();
        assertNull(TenantContext.get());
        assertNull(TenantContext.getSlug());
    }

    @Test
    @DisplayName("Should store tenant ID and slug independently")
    void shouldStoreIndependently() {
        TenantContext.set("tenant-123");
        TenantContext.setSlug("acme-corp");
        assertEquals("tenant-123", TenantContext.get());
        assertEquals("acme-corp", TenantContext.getSlug());
    }
}
