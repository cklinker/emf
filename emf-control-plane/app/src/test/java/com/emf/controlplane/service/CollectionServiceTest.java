package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateCollectionRequest;
import com.emf.controlplane.dto.UpdateCollectionRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.CollectionVersion;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.CollectionVersionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CollectionService.
 * Tests CRUD operations, versioning, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private CollectionVersionRepository versionRepository;

    @Mock
    private FieldRepository fieldRepository;

    private ObjectMapper objectMapper;

    private CollectionService collectionService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        collectionService = new CollectionService(
                collectionRepository,
                versionRepository,
                fieldRepository,
                objectMapper,
                null,  // ConfigEventPublisher is optional in tests
                null   // CollectionAssignmentService is optional in tests
        );
    }

    @Nested
    @DisplayName("listCollections")
    class ListCollectionsTests {

        @Test
        @DisplayName("should return paginated list of active collections without filter")
        void shouldReturnPaginatedListWithoutFilter() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Collection collection = createTestCollection("test-id", "Test Collection");
            Page<Collection> expectedPage = new PageImpl<>(List.of(collection), pageable, 1);
            
            when(collectionRepository.findByActiveTrueExcludeSystem(pageable)).thenReturn(expectedPage);

            // When
            Page<Collection> result = collectionService.listCollections(null, null, pageable, false);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Test Collection");
            verify(collectionRepository).findByActiveTrueExcludeSystem(pageable);
            verify(collectionRepository, never()).findByActiveAndSearchTermExcludeSystem(anyString(), any());
        }

        @Test
        @DisplayName("should return filtered list when filter is provided")
        void shouldReturnFilteredListWhenFilterProvided() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            String filter = "test";
            Collection collection = createTestCollection("test-id", "Test Collection");
            Page<Collection> expectedPage = new PageImpl<>(List.of(collection), pageable, 1);

            when(collectionRepository.findByActiveAndSearchTermExcludeSystem(filter, pageable)).thenReturn(expectedPage);

            // When
            Page<Collection> result = collectionService.listCollections(filter, null, pageable, false);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            verify(collectionRepository).findByActiveAndSearchTermExcludeSystem(filter, pageable);
            verify(collectionRepository, never()).findByActiveTrueExcludeSystem(any());
        }

        @Test
        @DisplayName("should return empty page when no collections match filter")
        void shouldReturnEmptyPageWhenNoMatch() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            String filter = "nonexistent";
            Page<Collection> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            when(collectionRepository.findByActiveAndSearchTermExcludeSystem(filter, pageable)).thenReturn(emptyPage);

            // When
            Page<Collection> result = collectionService.listCollections(filter, null, pageable, false);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should ignore blank filter and return all active collections")
        void shouldIgnoreBlankFilter() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Collection collection = createTestCollection("test-id", "Test Collection");
            Page<Collection> expectedPage = new PageImpl<>(List.of(collection), pageable, 1);

            when(collectionRepository.findByActiveTrueExcludeSystem(pageable)).thenReturn(expectedPage);

            // When
            Page<Collection> result = collectionService.listCollections("   ", null, pageable, false);

            // Then
            assertThat(result).isNotNull();
            verify(collectionRepository).findByActiveTrueExcludeSystem(pageable);
            verify(collectionRepository, never()).findByActiveAndSearchTermExcludeSystem(anyString(), any());
        }
    }

    @Nested
    @DisplayName("createCollection")
    class CreateCollectionTests {

        @Test
        @DisplayName("should create collection with generated ID and initial version")
        void shouldCreateCollectionWithGeneratedIdAndVersion() {
            // Given
            CreateCollectionRequest request = new CreateCollectionRequest("New Collection", "Description");

            when(collectionRepository.existsByNameAndActiveTrue("New Collection")).thenReturn(false);
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> {
                Collection c = invocation.getArgument(0);
                return c;
            });
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            Collection result = collectionService.createCollection(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("New Collection");
            assertThat(result.getDescription()).isEqualTo("Description");
            assertThat(result.isActive()).isTrue();
            assertThat(result.getCurrentVersion()).isEqualTo(1);
            assertThat(result.getVersions()).hasSize(1);
            assertThat(result.getVersions().get(0).getVersion()).isEqualTo(1);

            verify(collectionRepository).save(any(Collection.class));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when name already exists")
        void shouldThrowExceptionWhenNameExists() {
            // Given
            CreateCollectionRequest request = new CreateCollectionRequest("Existing Collection", "Description");

            when(collectionRepository.existsByNameAndActiveTrue("Existing Collection")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> collectionService.createCollection(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Collection")
                    .hasMessageContaining("name")
                    .hasMessageContaining("Existing Collection");

            verify(collectionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should create initial version with schema JSON")
        void shouldCreateInitialVersionWithSchema() {
            // Given
            CreateCollectionRequest request = new CreateCollectionRequest("Test Collection", "Test Description");

            when(collectionRepository.existsByNameAndActiveTrue(anyString())).thenReturn(false);
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            Collection result = collectionService.createCollection(request);

            // Then
            assertThat(result.getVersions()).hasSize(1);
            CollectionVersion savedVersion = result.getVersions().get(0);
            assertThat(savedVersion.getVersion()).isEqualTo(1);
            assertThat(savedVersion.getSchema()).isNotNull();
            assertThat(savedVersion.getSchema()).contains("Test Collection");
        }
    }

    @Nested
    @DisplayName("getCollection")
    class GetCollectionTests {

        @Test
        @DisplayName("should return collection when found and active")
        void shouldReturnCollectionWhenFoundAndActive() {
            // Given
            String collectionId = "test-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));

            // When
            Collection result = collectionService.getCollection(collectionId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(collectionId);
            assertThat(result.getName()).isEqualTo("Test Collection");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when collection not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            String collectionId = "nonexistent-id";
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> collectionService.getCollection(collectionId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Collection")
                    .hasMessageContaining(collectionId);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when collection is inactive")
        void shouldThrowExceptionWhenInactive() {
            // Given
            String collectionId = "inactive-id";
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> collectionService.getCollection(collectionId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateCollection")
    class UpdateCollectionTests {

        @Test
        @DisplayName("should update collection and increment version")
        void shouldUpdateCollectionAndIncrementVersion() {
            // Given
            String collectionId = "test-id";
            Collection existingCollection = createTestCollection(collectionId, "Old Name");
            existingCollection.setCurrentVersion(1);
            
            UpdateCollectionRequest request = new UpdateCollectionRequest("New Name", "New Description");
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(existingCollection));
            when(collectionRepository.existsByNameAndActiveTrue("New Name")).thenReturn(false);
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            Collection result = collectionService.updateCollection(collectionId, request);

            // Then
            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getDescription()).isEqualTo("New Description");
            assertThat(result.getCurrentVersion()).isEqualTo(2);
            assertThat(result.getVersions()).hasSize(1);
            assertThat(result.getVersions().get(0).getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when collection not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            String collectionId = "nonexistent-id";
            UpdateCollectionRequest request = new UpdateCollectionRequest("New Name", null);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> collectionService.updateCollection(collectionId, request))
                    .isInstanceOf(ResourceNotFoundException.class);
            
            verify(collectionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when new name already exists")
        void shouldThrowExceptionWhenNewNameExists() {
            // Given
            String collectionId = "test-id";
            Collection existingCollection = createTestCollection(collectionId, "Old Name");
            UpdateCollectionRequest request = new UpdateCollectionRequest("Existing Name", null);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(existingCollection));
            when(collectionRepository.existsByNameAndActiveTrue("Existing Name")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> collectionService.updateCollection(collectionId, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Existing Name");
            
            verify(collectionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should allow update with same name")
        void shouldAllowUpdateWithSameName() {
            // Given
            String collectionId = "test-id";
            Collection existingCollection = createTestCollection(collectionId, "Same Name");
            existingCollection.setCurrentVersion(1);
            
            UpdateCollectionRequest request = new UpdateCollectionRequest("Same Name", "Updated Description");
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(existingCollection));
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            Collection result = collectionService.updateCollection(collectionId, request);

            // Then
            assertThat(result.getName()).isEqualTo("Same Name");
            assertThat(result.getDescription()).isEqualTo("Updated Description");
            // Should not check for duplicate since name is unchanged
            verify(collectionRepository, never()).existsByNameAndActiveTrue(anyString());
        }

        @Test
        @DisplayName("should only update provided fields")
        void shouldOnlyUpdateProvidedFields() {
            // Given
            String collectionId = "test-id";
            Collection existingCollection = createTestCollection(collectionId, "Original Name");
            existingCollection.setDescription("Original Description");
            existingCollection.setCurrentVersion(1);
            
            UpdateCollectionRequest request = new UpdateCollectionRequest(null, "New Description");
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(existingCollection));
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            Collection result = collectionService.updateCollection(collectionId, request);

            // Then
            assertThat(result.getName()).isEqualTo("Original Name");
            assertThat(result.getDescription()).isEqualTo("New Description");
        }
    }

    @Nested
    @DisplayName("deleteCollection")
    class DeleteCollectionTests {

        @Test
        @DisplayName("should soft delete collection by marking as inactive")
        void shouldSoftDeleteCollection() {
            // Given
            String collectionId = "test-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            collection.setActive(true);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(collectionId)).thenReturn(Collections.emptyList());

            // When
            collectionService.deleteCollection(collectionId);

            // Then
            ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
            verify(collectionRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("should also mark all fields as inactive")
        void shouldMarkFieldsAsInactive() {
            // Given
            String collectionId = "test-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            
            Field field1 = new Field("field1", "string");
            field1.setActive(true);
            Field field2 = new Field("field2", "number");
            field2.setActive(true);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(collectionId)).thenReturn(List.of(field1, field2));
            when(fieldRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            collectionService.deleteCollection(collectionId);

            // Then
            assertThat(field1.isActive()).isFalse();
            assertThat(field2.isActive()).isFalse();
            verify(fieldRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when collection not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            String collectionId = "nonexistent-id";
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> collectionService.deleteCollection(collectionId))
                    .isInstanceOf(ResourceNotFoundException.class);
            
            verify(collectionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when collection already deleted")
        void shouldThrowExceptionWhenAlreadyDeleted() {
            // Given
            String collectionId = "deleted-id";
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> collectionService.deleteCollection(collectionId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getCollectionVersions")
    class GetCollectionVersionsTests {

        @Test
        @DisplayName("should return all versions for a collection")
        void shouldReturnAllVersions() {
            // Given
            String collectionId = "test-id";
            Collection collection = createTestCollection(collectionId, "Test");
            
            CollectionVersion v1 = new CollectionVersion(collection, 1, "{}");
            CollectionVersion v2 = new CollectionVersion(collection, 2, "{}");
            
            when(collectionRepository.existsById(collectionId)).thenReturn(true);
            when(versionRepository.findByCollectionIdOrderByVersionDesc(collectionId))
                    .thenReturn(List.of(v2, v1));

            // When
            List<CollectionVersion> result = collectionService.getCollectionVersions(collectionId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getVersion()).isEqualTo(2);
            assertThat(result.get(1).getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when collection not found")
        void shouldThrowExceptionWhenCollectionNotFound() {
            // Given
            String collectionId = "nonexistent-id";
            
            when(collectionRepository.existsById(collectionId)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> collectionService.getCollectionVersions(collectionId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getCollectionVersion")
    class GetCollectionVersionTests {

        @Test
        @DisplayName("should return specific version")
        void shouldReturnSpecificVersion() {
            // Given
            String collectionId = "test-id";
            Integer version = 2;
            Collection collection = createTestCollection(collectionId, "Test");
            CollectionVersion collectionVersion = new CollectionVersion(collection, version, "{}");
            
            when(collectionRepository.existsById(collectionId)).thenReturn(true);
            when(versionRepository.findByCollectionIdAndVersion(collectionId, version))
                    .thenReturn(Optional.of(collectionVersion));

            // When
            CollectionVersion result = collectionService.getCollectionVersion(collectionId, version);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getVersion()).isEqualTo(version);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when version not found")
        void shouldThrowExceptionWhenVersionNotFound() {
            // Given
            String collectionId = "test-id";
            Integer version = 99;
            
            when(collectionRepository.existsById(collectionId)).thenReturn(true);
            when(versionRepository.findByCollectionIdAndVersion(collectionId, version))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> collectionService.getCollectionVersion(collectionId, version))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("CollectionVersion");
        }
    }

    // Helper method to create test collections
    private Collection createTestCollection(String id, String name) {
        Collection collection = new Collection(name, "Test description");
        collection.setId(id);
        collection.setActive(true);
        collection.setCurrentVersion(1);
        return collection;
    }
}
