package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StorageConfig} record.
 * 
 * Validates: Requirements 1.1 - Storage mode configuration
 */
@DisplayName("StorageConfig Record Tests")
class StorageConfigTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create storage config with all parameters")
        void shouldCreateWithAllParameters() {
            Map<String, String> adapterConfig = Map.of("key", "value");
            StorageConfig config = new StorageConfig(StorageMode.PHYSICAL_TABLES, "tbl_test", adapterConfig);
            
            assertEquals(StorageMode.PHYSICAL_TABLES, config.mode());
            assertEquals("tbl_test", config.tableName());
            assertEquals(Map.of("key", "value"), config.adapterConfig());
        }

        @Test
        @DisplayName("Should default to PHYSICAL_TABLES when mode is null")
        void shouldDefaultToPhysicalTablesWhenModeIsNull() {
            StorageConfig config = new StorageConfig(null, "tbl_test", Map.of());
            
            assertEquals(StorageMode.PHYSICAL_TABLES, config.mode());
        }

        @Test
        @DisplayName("Should default to empty map when adapterConfig is null")
        void shouldDefaultToEmptyMapWhenAdapterConfigIsNull() {
            StorageConfig config = new StorageConfig(StorageMode.PHYSICAL_TABLES, "tbl_test", null);
            
            assertNotNull(config.adapterConfig());
            assertTrue(config.adapterConfig().isEmpty());
        }

        @Test
        @DisplayName("Should perform defensive copy of adapterConfig")
        void shouldPerformDefensiveCopyOfAdapterConfig() {
            Map<String, String> mutableMap = new HashMap<>();
            mutableMap.put("key", "value");
            
            StorageConfig config = new StorageConfig(StorageMode.PHYSICAL_TABLES, "tbl_test", mutableMap);
            
            // Modify original map
            mutableMap.put("newKey", "newValue");
            
            // Config should not be affected
            assertFalse(config.adapterConfig().containsKey("newKey"));
            assertEquals(1, config.adapterConfig().size());
        }

        @Test
        @DisplayName("Should return immutable adapterConfig")
        void shouldReturnImmutableAdapterConfig() {
            StorageConfig config = new StorageConfig(StorageMode.PHYSICAL_TABLES, "tbl_test", Map.of("key", "value"));
            
            assertThrows(UnsupportedOperationException.class, () -> {
                config.adapterConfig().put("newKey", "newValue");
            });
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("physicalTable() should create Mode A config")
        void physicalTableShouldCreateModeAConfig() {
            StorageConfig config = StorageConfig.physicalTable("tbl_products");
            
            assertEquals(StorageMode.PHYSICAL_TABLES, config.mode());
            assertEquals("tbl_products", config.tableName());
            assertTrue(config.adapterConfig().isEmpty());
        }

        @Test
        @DisplayName("physicalTable() should throw on null table name")
        void physicalTableShouldThrowOnNullTableName() {
            assertThrows(NullPointerException.class, () -> {
                StorageConfig.physicalTable(null);
            });
        }

        @Test
        @DisplayName("jsonbStore() should create Mode B config")
        void jsonbStoreShouldCreateModeBConfig() {
            StorageConfig config = StorageConfig.jsonbStore();
            
            assertEquals(StorageMode.JSONB_STORE, config.mode());
            assertNull(config.tableName());
            assertTrue(config.adapterConfig().isEmpty());
        }
    }

    @Nested
    @DisplayName("Record Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            StorageConfig config1 = new StorageConfig(StorageMode.PHYSICAL_TABLES, "tbl_test", Map.of("key", "value"));
            StorageConfig config2 = new StorageConfig(StorageMode.PHYSICAL_TABLES, "tbl_test", Map.of("key", "value"));
            
            assertEquals(config1, config2);
            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            StorageConfig config1 = new StorageConfig(StorageMode.PHYSICAL_TABLES, "tbl_test", Map.of());
            StorageConfig config2 = new StorageConfig(StorageMode.JSONB_STORE, "tbl_test", Map.of());
            
            assertNotEquals(config1, config2);
        }
    }
}
