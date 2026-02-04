package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ApiConfig} record.
 * 
 * Validates: Requirements 1.1 - API configuration
 */
@DisplayName("ApiConfig Record Tests")
class ApiConfigTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create API config with all parameters")
        void shouldCreateWithAllParameters() {
            ApiConfig config = new ApiConfig(true, true, true, true, true, "/api/collections/test");
            
            assertTrue(config.listEnabled());
            assertTrue(config.getEnabled());
            assertTrue(config.createEnabled());
            assertTrue(config.updateEnabled());
            assertTrue(config.deleteEnabled());
            assertEquals("/api/collections/test", config.basePath());
        }

        @Test
        @DisplayName("Should create API config with mixed enabled/disabled operations")
        void shouldCreateWithMixedOperations() {
            ApiConfig config = new ApiConfig(true, true, false, false, false, "/api/test");
            
            assertTrue(config.listEnabled());
            assertTrue(config.getEnabled());
            assertFalse(config.createEnabled());
            assertFalse(config.updateEnabled());
            assertFalse(config.deleteEnabled());
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("allEnabled() should enable all operations")
        void allEnabledShouldEnableAllOperations() {
            ApiConfig config = ApiConfig.allEnabled("/api/test");
            
            assertTrue(config.listEnabled());
            assertTrue(config.getEnabled());
            assertTrue(config.createEnabled());
            assertTrue(config.updateEnabled());
            assertTrue(config.deleteEnabled());
            assertEquals("/api/test", config.basePath());
        }

        @Test
        @DisplayName("readOnly() should enable only read operations")
        void readOnlyShouldEnableOnlyReadOperations() {
            ApiConfig config = ApiConfig.readOnly("/api/test");
            
            assertTrue(config.listEnabled());
            assertTrue(config.getEnabled());
            assertFalse(config.createEnabled());
            assertFalse(config.updateEnabled());
            assertFalse(config.deleteEnabled());
            assertEquals("/api/test", config.basePath());
        }

        @Test
        @DisplayName("disabled() should disable all operations")
        void disabledShouldDisableAllOperations() {
            ApiConfig config = ApiConfig.disabled("/api/test");
            
            assertFalse(config.listEnabled());
            assertFalse(config.getEnabled());
            assertFalse(config.createEnabled());
            assertFalse(config.updateEnabled());
            assertFalse(config.deleteEnabled());
            assertEquals("/api/test", config.basePath());
        }
    }

    @Nested
    @DisplayName("Record Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            ApiConfig config1 = new ApiConfig(true, true, true, true, true, "/api/test");
            ApiConfig config2 = new ApiConfig(true, true, true, true, true, "/api/test");
            
            assertEquals(config1, config2);
            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            ApiConfig config1 = new ApiConfig(true, true, true, true, true, "/api/test");
            ApiConfig config2 = new ApiConfig(true, true, false, true, true, "/api/test");
            
            assertNotEquals(config1, config2);
        }
    }
}
