package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StorageMode} enum.
 * 
 * Validates: Requirements 9.2, 9.3 - Storage modes (Mode A and Mode B)
 */
@DisplayName("StorageMode Enum Tests")
class StorageModeTest {

    @Test
    @DisplayName("Should have PHYSICAL_TABLES mode (Mode A)")
    void shouldHavePhysicalTablesMode() {
        assertNotNull(StorageMode.PHYSICAL_TABLES);
    }

    @Test
    @DisplayName("Should have JSONB_STORE mode (Mode B)")
    void shouldHaveJsonbStoreMode() {
        assertNotNull(StorageMode.JSONB_STORE);
    }

    @Test
    @DisplayName("Should have exactly 2 storage modes")
    void shouldHaveExactlyTwoModes() {
        assertEquals(2, StorageMode.values().length);
    }

    @Test
    @DisplayName("Should be able to convert from string")
    void shouldConvertFromString() {
        assertEquals(StorageMode.PHYSICAL_TABLES, StorageMode.valueOf("PHYSICAL_TABLES"));
        assertEquals(StorageMode.JSONB_STORE, StorageMode.valueOf("JSONB_STORE"));
    }
}
