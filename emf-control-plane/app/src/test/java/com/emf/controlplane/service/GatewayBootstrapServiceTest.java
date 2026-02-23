package com.emf.controlplane.service;

import com.emf.controlplane.config.ControlPlaneProperties;
import com.emf.controlplane.dto.GovernorLimits;
import com.emf.controlplane.dto.GatewayBootstrapConfigDto;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.Tenant;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GatewayBootstrapService}.
 *
 * Verifies that:
 * - System collections are included in bootstrap config (not filtered out)
 * - __control-plane collection is excluded
 * - systemCollection flag is correctly set on DTOs
 * - All collections use the configured worker service URL
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayBootstrapService Tests")
class GatewayBootstrapServiceTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantService tenantService;

    @Mock
    private ControlPlaneProperties properties;

    private GatewayBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new GatewayBootstrapService(
                collectionRepository,
                tenantRepository,
                tenantService,
                properties
        );
    }

    @Nested
    @DisplayName("System Collection Inclusion Tests")
    class SystemCollectionInclusionTests {

        @Test
        @DisplayName("Should include system collections in bootstrap config")
        void shouldIncludeSystemCollections() {
            // Arrange
            Collection usersCollection = createCollection("sys-users-id", "users", "/api/users", true);
            Collection productsCollection = createCollection("prod-id", "products", "/api/products", false);

            when(collectionRepository.findAllActiveWithFields())
                    .thenReturn(List.of(usersCollection, productsCollection));
            when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of());
            when(properties.getWorkerServiceUrl()).thenReturn("http://emf-worker:80");

            // Act
            GatewayBootstrapConfigDto config = service.getBootstrapConfig();

            // Assert — both system and user collections should be present
            assertNotNull(config);
            assertEquals(2, config.getCollections().size());

            GatewayBootstrapConfigDto.CollectionDto usersDto = config.getCollections().stream()
                    .filter(c -> "users".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(usersDto.isSystemCollection(), "users should be marked as system collection");

            GatewayBootstrapConfigDto.CollectionDto productsDto = config.getCollections().stream()
                    .filter(c -> "products".equals(c.getName()))
                    .findFirst()
                    .orElseThrow();
            assertFalse(productsDto.isSystemCollection(), "products should NOT be marked as system collection");
        }

        @Test
        @DisplayName("Should exclude __control-plane from bootstrap config")
        void shouldExcludeControlPlane() {
            // Arrange
            Collection controlPlane = createCollection("cp-id", "__control-plane", "/control", true);
            Collection usersCollection = createCollection("sys-users-id", "users", "/api/users", true);

            when(collectionRepository.findAllActiveWithFields())
                    .thenReturn(List.of(controlPlane, usersCollection));
            when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of());
            when(properties.getWorkerServiceUrl()).thenReturn("http://emf-worker:80");

            // Act
            GatewayBootstrapConfigDto config = service.getBootstrapConfig();

            // Assert — __control-plane should be excluded, users should remain
            assertEquals(1, config.getCollections().size());
            assertEquals("users", config.getCollections().get(0).getName());
        }

        @Test
        @DisplayName("Should include multiple system collections")
        void shouldIncludeMultipleSystemCollections() {
            // Arrange
            Collection users = createCollection("sys-users", "users", "/api/users", true);
            Collection profiles = createCollection("sys-profiles", "profiles", "/api/profiles", true);
            Collection permissions = createCollection("sys-perms", "permission-sets", "/api/permission-sets", true);
            Collection products = createCollection("usr-products", "products", "/api/products", false);

            when(collectionRepository.findAllActiveWithFields())
                    .thenReturn(List.of(users, profiles, permissions, products));
            when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of());
            when(properties.getWorkerServiceUrl()).thenReturn("http://emf-worker:80");

            // Act
            GatewayBootstrapConfigDto config = service.getBootstrapConfig();

            // Assert — all 4 should be included
            assertEquals(4, config.getCollections().size());

            long systemCount = config.getCollections().stream()
                    .filter(GatewayBootstrapConfigDto.CollectionDto::isSystemCollection)
                    .count();
            assertEquals(3, systemCount, "Should have 3 system collections");

            long userCount = config.getCollections().stream()
                    .filter(c -> !c.isSystemCollection())
                    .count();
            assertEquals(1, userCount, "Should have 1 user collection");
        }
    }

    @Nested
    @DisplayName("Collection DTO Mapping Tests")
    class CollectionDtoMappingTests {

        @Test
        @DisplayName("Should map collection fields correctly")
        void shouldMapCollectionFieldsCorrectly() {
            // Arrange
            Collection usersCollection = createCollection("sys-users", "users", "/api/users", true);
            Field nameField = createField("name", "string", usersCollection);
            Field emailField = createField("email", "string", usersCollection);
            usersCollection.getFields().add(nameField);
            usersCollection.getFields().add(emailField);

            when(collectionRepository.findAllActiveWithFields())
                    .thenReturn(List.of(usersCollection));
            when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of());
            when(properties.getWorkerServiceUrl()).thenReturn("http://emf-worker:80");

            // Act
            GatewayBootstrapConfigDto config = service.getBootstrapConfig();

            // Assert
            assertEquals(1, config.getCollections().size());
            GatewayBootstrapConfigDto.CollectionDto dto = config.getCollections().get(0);
            assertEquals("sys-users", dto.getId());
            assertEquals("users", dto.getName());
            assertEquals("/api/users", dto.getPath());
            assertTrue(dto.isSystemCollection());
            assertEquals(2, dto.getFields().size());
        }

        @Test
        @DisplayName("Should generate path when not set on collection")
        void shouldGeneratePathWhenNotSet() {
            // Arrange
            Collection collection = createCollection("id-1", "orders", null, false);

            when(collectionRepository.findAllActiveWithFields())
                    .thenReturn(List.of(collection));
            when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of());
            when(properties.getWorkerServiceUrl()).thenReturn("http://emf-worker:80");

            // Act
            GatewayBootstrapConfigDto config = service.getBootstrapConfig();

            // Assert — path should be auto-generated as /api/{name}
            assertEquals("/api/orders", config.getCollections().get(0).getPath());
        }

        @Test
        @DisplayName("Should use configured worker service URL for all collections")
        void shouldUseConfiguredWorkerServiceUrl() {
            // Arrange
            Collection collection = createCollection("col-1", "products", "/api/products", false);

            when(collectionRepository.findAllActiveWithFields())
                    .thenReturn(List.of(collection));
            when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of());
            when(properties.getWorkerServiceUrl()).thenReturn("http://emf-worker:80");

            // Act
            GatewayBootstrapConfigDto config = service.getBootstrapConfig();

            // Assert — all collections use the K8s worker service URL
            assertEquals("http://emf-worker:80", config.getCollections().get(0).getWorkerBaseUrl());
        }
    }

    @Nested
    @DisplayName("Governor Limits Tests")
    class GovernorLimitsTests {

        @Test
        @DisplayName("Should include governor limits in bootstrap config")
        void shouldIncludeGovernorLimits() {
            // Arrange
            Tenant tenant = new Tenant();
            tenant.setId("tenant-1");
            tenant.setStatus("ACTIVE");

            when(collectionRepository.findAllActiveWithFields()).thenReturn(List.of());
            when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of(tenant));
            when(tenantService.getGovernorLimits("tenant-1"))
                    .thenReturn(new GovernorLimits(10000, 10, 100, 200, 500, 50, 200));
            when(properties.getWorkerServiceUrl()).thenReturn("http://emf-worker:80");

            // Act
            GatewayBootstrapConfigDto config = service.getBootstrapConfig();

            // Assert
            assertNotNull(config.getGovernorLimits());
            assertTrue(config.getGovernorLimits().containsKey("tenant-1"));
            assertEquals(10000, config.getGovernorLimits().get("tenant-1").getApiCallsPerDay());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Collection createCollection(String id, String name, String path, boolean systemCollection) {
        Collection collection = new Collection(name, "Description for " + name);
        collection.setId(id);
        collection.setPath(path);
        collection.setSystemCollection(systemCollection);
        collection.setActive(true);
        return collection;
    }

    private Field createField(String name, String type, Collection collection) {
        Field field = new Field();
        field.setName(name);
        field.setType(type);
        field.setActive(true);
        field.setCollection(collection);
        return field;
    }
}
