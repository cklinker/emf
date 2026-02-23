package com.emf.controlplane.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GatewayBootstrapConfigDto} and its inner DTOs.
 */
@DisplayName("GatewayBootstrapConfigDto Tests")
class GatewayBootstrapConfigDtoTest {

    @Nested
    @DisplayName("CollectionDto Tests")
    class CollectionDtoTests {

        @Test
        @DisplayName("Should create CollectionDto with systemCollection flag")
        void shouldCreateWithSystemCollectionFlag() {
            var fields = List.of(
                    new GatewayBootstrapConfigDto.FieldDto("name", "string"),
                    new GatewayBootstrapConfigDto.FieldDto("email", "string")
            );

            var dto = new GatewayBootstrapConfigDto.CollectionDto(
                    "id-1", "users", "/api/users", "http://worker:80", fields, true
            );

            assertEquals("id-1", dto.getId());
            assertEquals("users", dto.getName());
            assertEquals("/api/users", dto.getPath());
            assertEquals("http://worker:80", dto.getWorkerBaseUrl());
            assertEquals(2, dto.getFields().size());
            assertTrue(dto.isSystemCollection());
        }

        @Test
        @DisplayName("Should default systemCollection to false for 4-arg constructor")
        void shouldDefaultSystemCollectionToFalse() {
            var fields = List.of(
                    new GatewayBootstrapConfigDto.FieldDto("name", "string")
            );

            var dto = new GatewayBootstrapConfigDto.CollectionDto(
                    "id-2", "products", "/api/products", fields
            );

            assertFalse(dto.isSystemCollection());
        }

        @Test
        @DisplayName("Should default systemCollection to false for 5-arg constructor")
        void shouldDefaultSystemCollectionToFalseFor5ArgConstructor() {
            var fields = List.of(
                    new GatewayBootstrapConfigDto.FieldDto("name", "string")
            );

            var dto = new GatewayBootstrapConfigDto.CollectionDto(
                    "id-3", "orders", "/api/orders", "http://worker:80", fields
            );

            assertFalse(dto.isSystemCollection());
        }

        @Test
        @DisplayName("Should set systemCollection via setter")
        void shouldSetSystemCollectionViaSetter() {
            var dto = new GatewayBootstrapConfigDto.CollectionDto();
            assertFalse(dto.isSystemCollection());

            dto.setSystemCollection(true);
            assertTrue(dto.isSystemCollection());
        }

        @Test
        @DisplayName("Should distinguish system and user collections")
        void shouldDistinguishSystemAndUserCollections() {
            var systemDto = new GatewayBootstrapConfigDto.CollectionDto(
                    "sys-1", "users", "/api/users", "http://worker:80", List.of(), true
            );
            var userDto = new GatewayBootstrapConfigDto.CollectionDto(
                    "usr-1", "products", "/api/products", "http://worker:80", List.of(), false
            );

            assertTrue(systemDto.isSystemCollection());
            assertFalse(userDto.isSystemCollection());
        }
    }

    @Nested
    @DisplayName("GovernorLimitDto Tests")
    class GovernorLimitDtoTests {

        @Test
        @DisplayName("Should create GovernorLimitDto with api calls per day")
        void shouldCreateWithApiCallsPerDay() {
            var dto = new GatewayBootstrapConfigDto.GovernorLimitDto(10000);
            assertEquals(10000, dto.getApiCallsPerDay());
        }

        @Test
        @DisplayName("Should create GovernorLimitDto with default constructor")
        void shouldCreateWithDefaultConstructor() {
            var dto = new GatewayBootstrapConfigDto.GovernorLimitDto();
            assertEquals(0, dto.getApiCallsPerDay());
        }

        @Test
        @DisplayName("Should set api calls per day via setter")
        void shouldSetApiCallsPerDayViaSetter() {
            var dto = new GatewayBootstrapConfigDto.GovernorLimitDto();
            dto.setApiCallsPerDay(5000);
            assertEquals(5000, dto.getApiCallsPerDay());
        }
    }

    @Nested
    @DisplayName("Top-Level DTO Tests")
    class TopLevelTests {

        @Test
        @DisplayName("Should create DTO with collections and governor limits")
        void shouldCreateWithCollectionsAndGovernorLimits() {
            var collections = List.of(
                    new GatewayBootstrapConfigDto.CollectionDto(
                            "id-1", "users", "/api/users", "http://worker:80", List.of(), true
                    ),
                    new GatewayBootstrapConfigDto.CollectionDto(
                            "id-2", "products", "/api/products", "http://worker:80", List.of(), false
                    )
            );
            var limits = Map.of(
                    "tenant-1", new GatewayBootstrapConfigDto.GovernorLimitDto(10000)
            );

            var dto = new GatewayBootstrapConfigDto(collections, limits);

            assertEquals(2, dto.getCollections().size());
            assertEquals(1, dto.getGovernorLimits().size());
        }

        @Test
        @DisplayName("Should create DTO with collections only")
        void shouldCreateWithCollectionsOnly() {
            var collections = List.of(
                    new GatewayBootstrapConfigDto.CollectionDto(
                            "id-1", "users", "/api/users", List.of()
                    )
            );

            var dto = new GatewayBootstrapConfigDto(collections);

            assertEquals(1, dto.getCollections().size());
            assertNull(dto.getGovernorLimits());
        }
    }
}
