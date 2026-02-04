package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AuthzConfig} record.
 * 
 * Validates: Requirements 1.1 - Authorization configuration
 */
@DisplayName("AuthzConfig Record Tests")
class AuthzConfigTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create authz config with all parameters")
        void shouldCreateWithAllParameters() {
            List<String> readRoles = List.of("USER", "ADMIN");
            List<String> writeRoles = List.of("ADMIN");
            
            AuthzConfig config = new AuthzConfig(true, readRoles, writeRoles);
            
            assertTrue(config.enabled());
            assertEquals(List.of("USER", "ADMIN"), config.readRoles());
            assertEquals(List.of("ADMIN"), config.writeRoles());
        }

        @Test
        @DisplayName("Should default to empty lists when roles are null")
        void shouldDefaultToEmptyListsWhenRolesAreNull() {
            AuthzConfig config = new AuthzConfig(true, null, null);
            
            assertTrue(config.enabled());
            assertNotNull(config.readRoles());
            assertNotNull(config.writeRoles());
            assertTrue(config.readRoles().isEmpty());
            assertTrue(config.writeRoles().isEmpty());
        }

        @Test
        @DisplayName("Should perform defensive copy of readRoles")
        void shouldPerformDefensiveCopyOfReadRoles() {
            List<String> mutableList = new ArrayList<>();
            mutableList.add("USER");
            
            AuthzConfig config = new AuthzConfig(true, mutableList, List.of());
            
            // Modify original list
            mutableList.add("ADMIN");
            
            // Config should not be affected
            assertEquals(1, config.readRoles().size());
            assertFalse(config.readRoles().contains("ADMIN"));
        }

        @Test
        @DisplayName("Should perform defensive copy of writeRoles")
        void shouldPerformDefensiveCopyOfWriteRoles() {
            List<String> mutableList = new ArrayList<>();
            mutableList.add("ADMIN");
            
            AuthzConfig config = new AuthzConfig(true, List.of(), mutableList);
            
            // Modify original list
            mutableList.add("SUPER_ADMIN");
            
            // Config should not be affected
            assertEquals(1, config.writeRoles().size());
            assertFalse(config.writeRoles().contains("SUPER_ADMIN"));
        }

        @Test
        @DisplayName("Should return immutable readRoles")
        void shouldReturnImmutableReadRoles() {
            AuthzConfig config = new AuthzConfig(true, List.of("USER"), List.of());
            
            assertThrows(UnsupportedOperationException.class, () -> {
                config.readRoles().add("ADMIN");
            });
        }

        @Test
        @DisplayName("Should return immutable writeRoles")
        void shouldReturnImmutableWriteRoles() {
            AuthzConfig config = new AuthzConfig(true, List.of(), List.of("ADMIN"));
            
            assertThrows(UnsupportedOperationException.class, () -> {
                config.writeRoles().add("SUPER_ADMIN");
            });
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("disabled() should create config with authorization disabled")
        void disabledShouldCreateDisabledConfig() {
            AuthzConfig config = AuthzConfig.disabled();
            
            assertFalse(config.enabled());
            assertTrue(config.readRoles().isEmpty());
            assertTrue(config.writeRoles().isEmpty());
        }

        @Test
        @DisplayName("withRoles() should create config with specified roles")
        void withRolesShouldCreateConfigWithRoles() {
            AuthzConfig config = AuthzConfig.withRoles(
                List.of("USER", "ADMIN"),
                List.of("ADMIN")
            );
            
            assertTrue(config.enabled());
            assertEquals(List.of("USER", "ADMIN"), config.readRoles());
            assertEquals(List.of("ADMIN"), config.writeRoles());
        }

        @Test
        @DisplayName("withRoles() should throw on null readRoles")
        void withRolesShouldThrowOnNullReadRoles() {
            assertThrows(NullPointerException.class, () -> {
                AuthzConfig.withRoles(null, List.of());
            });
        }

        @Test
        @DisplayName("withRoles() should throw on null writeRoles")
        void withRolesShouldThrowOnNullWriteRoles() {
            assertThrows(NullPointerException.class, () -> {
                AuthzConfig.withRoles(List.of(), null);
            });
        }
    }

    @Nested
    @DisplayName("Record Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            AuthzConfig config1 = new AuthzConfig(true, List.of("USER"), List.of("ADMIN"));
            AuthzConfig config2 = new AuthzConfig(true, List.of("USER"), List.of("ADMIN"));
            
            assertEquals(config1, config2);
            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            AuthzConfig config1 = new AuthzConfig(true, List.of("USER"), List.of("ADMIN"));
            AuthzConfig config2 = new AuthzConfig(false, List.of("USER"), List.of("ADMIN"));
            
            assertNotEquals(config1, config2);
        }
    }
}
