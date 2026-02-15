package com.emf.controlplane.service;

import com.emf.controlplane.dto.AuthorizationHintsDto;
import com.emf.controlplane.dto.FieldAuthorizationHintsDto;
import com.emf.controlplane.dto.FieldMetadataDto;
import com.emf.controlplane.dto.ResourceDiscoveryDto;
import com.emf.controlplane.dto.ResourceMetadataDto;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DiscoveryService.
 * Tests resource discovery including schemas, operations, and authorization hints.
 *
 * Requirements validated:
 * - 8.1: Return all active collections with their schemas
 * - 8.2: Include field definitions, types, and constraints
 * - 8.3: Include available operations
 * - 8.4: Include authorization hints
 */
@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private FieldRepository fieldRepository;

    private ObjectMapper objectMapper;

    private DiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        discoveryService = new DiscoveryService(
                collectionRepository,
                fieldRepository,
                objectMapper
        );
    }

    @Nested
    @DisplayName("discoverResources")
    class DiscoverResourcesTests {

        @Test
        @DisplayName("should return empty list when no active collections exist")
        void shouldReturnEmptyListWhenNoCollections() {
            // Given
            when(collectionRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

            // When
            ResourceDiscoveryDto result = discoveryService.discoverResources();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getResources()).isEmpty();
        }

        @Test
        @DisplayName("should return all active collections with basic metadata")
        void shouldReturnAllActiveCollections() {
            // Given
            Collection collection1 = createTestCollection("col-1", "Users", "User collection");
            Collection collection2 = createTestCollection("col-2", "Products", "Product collection");

            when(collectionRepository.findByActiveTrue()).thenReturn(Arrays.asList(collection1, collection2));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            ResourceDiscoveryDto result = discoveryService.discoverResources();

            // Then
            assertThat(result.getResources()).hasSize(2);

            ResourceMetadataDto users = result.getResources().stream()
                    .filter(r -> r.getName().equals("Users"))
                    .findFirst()
                    .orElseThrow();
            assertThat(users.getId()).isEqualTo("col-1");
            assertThat(users.getDescription()).isEqualTo("User collection");
            assertThat(users.getCurrentVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("should include standard CRUD operations for each collection")
        void shouldIncludeStandardOperations() {
            // Given
            Collection collection = createTestCollection("col-1", "Users", "User collection");

            when(collectionRepository.findByActiveTrue()).thenReturn(List.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            ResourceDiscoveryDto result = discoveryService.discoverResources();

            // Then
            ResourceMetadataDto resource = result.getResources().get(0);
            assertThat(resource.getAvailableOperations())
                    .containsExactlyInAnyOrder("CREATE", "READ", "UPDATE", "DELETE", "LIST");
        }

        @Test
        @DisplayName("should include field definitions with types and constraints")
        void shouldIncludeFieldDefinitions() {
            // Given
            Collection collection = createTestCollection("col-1", "Users", "User collection");

            Field nameField = createTestField("field-1", "name", "string", true, collection);
            nameField.setDescription("User's full name");
            nameField.setConstraints("{\"minLength\": 1, \"maxLength\": 100}");

            Field ageField = createTestField("field-2", "age", "number", false, collection);
            ageField.setConstraints("{\"min\": 0, \"max\": 150}");

            when(collectionRepository.findByActiveTrue()).thenReturn(List.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue("col-1"))
                    .thenReturn(Arrays.asList(nameField, ageField));

            // When
            ResourceDiscoveryDto result = discoveryService.discoverResources();

            // Then
            ResourceMetadataDto resource = result.getResources().get(0);
            assertThat(resource.getFields()).hasSize(2);

            FieldMetadataDto nameFieldDto = resource.getFields().stream()
                    .filter(f -> f.getName().equals("name"))
                    .findFirst()
                    .orElseThrow();
            assertThat(nameFieldDto.getId()).isEqualTo("field-1");
            assertThat(nameFieldDto.getType()).isEqualTo("string");
            assertThat(nameFieldDto.isRequired()).isTrue();
            assertThat(nameFieldDto.getDescription()).isEqualTo("User's full name");
            assertThat(nameFieldDto.getConstraints()).containsEntry("minLength", 1);
            assertThat(nameFieldDto.getConstraints()).containsEntry("maxLength", 100);

            FieldMetadataDto ageFieldDto = resource.getFields().stream()
                    .filter(f -> f.getName().equals("age"))
                    .findFirst()
                    .orElseThrow();
            assertThat(ageFieldDto.getType()).isEqualTo("number");
            assertThat(ageFieldDto.isRequired()).isFalse();
            assertThat(ageFieldDto.getConstraints()).containsEntry("min", 0);
            assertThat(ageFieldDto.getConstraints()).containsEntry("max", 150);
        }

        @Test
        @DisplayName("should handle fields without constraints")
        void shouldHandleFieldsWithoutConstraints() {
            // Given
            Collection collection = createTestCollection("col-1", "Users", "User collection");
            Field field = createTestField("field-1", "name", "string", true, collection);
            field.setConstraints(null);

            when(collectionRepository.findByActiveTrue()).thenReturn(List.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue("col-1")).thenReturn(List.of(field));

            // When
            ResourceDiscoveryDto result = discoveryService.discoverResources();

            // Then
            FieldMetadataDto fieldDto = result.getResources().get(0).getFields().get(0);
            assertThat(fieldDto.getConstraints()).isNull();
        }

        @Test
        @DisplayName("should handle invalid constraints JSON gracefully")
        void shouldHandleInvalidConstraintsJson() {
            // Given
            Collection collection = createTestCollection("col-1", "Users", "User collection");
            Field field = createTestField("field-1", "name", "string", true, collection);
            field.setConstraints("invalid json");

            when(collectionRepository.findByActiveTrue()).thenReturn(List.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue("col-1")).thenReturn(List.of(field));

            // When
            ResourceDiscoveryDto result = discoveryService.discoverResources();

            // Then
            FieldMetadataDto fieldDto = result.getResources().get(0).getFields().get(0);
            assertThat(fieldDto.getConstraints()).isNull();
        }

        @Test
        @DisplayName("should return empty authorization hints when no policies exist")
        void shouldReturnEmptyAuthHintsWhenNoPolicies() {
            // Given
            Collection collection = createTestCollection("col-1", "Users", "User collection");

            when(collectionRepository.findByActiveTrue()).thenReturn(List.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue("col-1")).thenReturn(Collections.emptyList());

            // When
            ResourceDiscoveryDto result = discoveryService.discoverResources();

            // Then
            AuthorizationHintsDto authHints = result.getResources().get(0).getAuthorizationHints();
            assertThat(authHints).isNotNull();
            assertThat(authHints.getOperationPolicies()).isEmpty();
        }
    }

    // Helper methods to create test entities

    private Collection createTestCollection(String id, String name, String description) {
        Collection collection = new Collection(name, description);
        collection.setId(id);
        collection.setActive(true);
        collection.setCurrentVersion(1);
        return collection;
    }

    private Field createTestField(String id, String name, String type, boolean required, Collection collection) {
        Field field = new Field(name, type);
        field.setId(id);
        field.setRequired(required);
        field.setActive(true);
        field.setCollection(collection);
        return field;
    }
}
