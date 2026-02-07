package com.emf.controlplane.service;

import com.emf.controlplane.dto.AddFieldRequest;
import com.emf.controlplane.dto.UpdateFieldRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.CollectionVersion;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.Service;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.CollectionVersionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.validation.FieldTypeValidatorRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FieldService.
 * Tests CRUD operations, versioning, and validation.
 */
@ExtendWith(MockitoExtension.class)
class FieldServiceTest {

    @Mock
    private FieldRepository fieldRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private CollectionVersionRepository versionRepository;

    private ObjectMapper objectMapper;
    private FieldService fieldService;


    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        fieldService = new FieldService(
                fieldRepository,
                collectionRepository,
                versionRepository,
                objectMapper,
                null,  // ConfigEventPublisher is optional in tests
                new FieldTypeValidatorRegistry(java.util.Collections.emptyList())
        );
    }

    @Nested
    @DisplayName("listFields")
    class ListFieldsTests {

        @Test
        @DisplayName("should return list of active fields for a collection")
        void shouldReturnActiveFields() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            
            Field field1 = createTestField("field-1", "name", "string", collection);
            Field field2 = createTestField("field-2", "age", "number", collection);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue(collectionId)).thenReturn(List.of(field1, field2));

            // When
            List<Field> result = fieldService.listFields(collectionId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Field::getName).containsExactly("name", "age");
            verify(fieldRepository).findByCollectionIdAndActiveTrue(collectionId);
        }

        @Test
        @DisplayName("should return empty list when collection has no fields")
        void shouldReturnEmptyListWhenNoFields() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue(collectionId)).thenReturn(Collections.emptyList());

            // When
            List<Field> result = fieldService.listFields(collectionId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when collection not found")
        void shouldThrowExceptionWhenCollectionNotFound() {
            // Given
            String collectionId = "nonexistent-id";
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> fieldService.listFields(collectionId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Collection")
                    .hasMessageContaining(collectionId);
        }
    }


    @Nested
    @DisplayName("addField")
    class AddFieldTests {

        @Test
        @DisplayName("should add field and create new collection version")
        void shouldAddFieldAndCreateVersion() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            collection.setCurrentVersion(1);
            
            AddFieldRequest request = new AddFieldRequest("email", "string", true, "User email", null);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.existsByCollectionIdAndNameAndActiveTrue(collectionId, "email")).thenReturn(false);
            when(fieldRepository.save(any(Field.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(versionRepository.save(any(CollectionVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            Field result = fieldService.addField(collectionId, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("email");
            assertThat(result.getType()).isEqualTo("STRING");
            assertThat(result.isRequired()).isTrue();
            assertThat(result.getDescription()).isEqualTo("User email");
            assertThat(result.isActive()).isTrue();
            
            // Verify version was incremented
            assertThat(collection.getCurrentVersion()).isEqualTo(2);
            verify(versionRepository).save(any(CollectionVersion.class));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when field name already exists")
        void shouldThrowExceptionWhenFieldNameExists() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            AddFieldRequest request = new AddFieldRequest("existingField", "string");
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.existsByCollectionIdAndNameAndActiveTrue(collectionId, "existingField")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> fieldService.addField(collectionId, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Field")
                    .hasMessageContaining("existingField");
            
            verify(fieldRepository, never()).save(any());
            verify(versionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException for invalid field type")
        void shouldThrowExceptionForInvalidFieldType() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            AddFieldRequest request = new AddFieldRequest("field", "invalidType");
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.existsByCollectionIdAndNameAndActiveTrue(anyString(), anyString())).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> fieldService.addField(collectionId, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("type")
                    .hasMessageContaining("invalidType");
        }

        @Test
        @DisplayName("should accept all valid field types")
        void shouldAcceptAllValidFieldTypes() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            collection.setCurrentVersion(1);

            // Target collection for LOOKUP/MASTER_DETAIL types
            Collection targetCollection = createTestCollection("target-collection-id", "Target");
            targetCollection.setCurrentVersion(1);

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.existsByCollectionIdAndNameAndActiveTrue(anyString(), anyString())).thenReturn(false);
            when(fieldRepository.save(any(Field.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(versionRepository.save(any(CollectionVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());
            when(collectionRepository.findByNameAndActiveTrue("Target")).thenReturn(Optional.of(targetCollection));
            when(fieldRepository.countMasterDetailFieldsByCollectionId(anyString())).thenReturn(0L);

            // When/Then - all valid types should work
            for (String type : FieldService.VALID_FIELD_TYPES) {
                AddFieldRequest request;
                String canonical = FieldService.TYPE_ALIASES.get(type);
                if ("LOOKUP".equals(canonical) || "MASTER_DETAIL".equals(canonical)) {
                    request = new AddFieldRequest("field_" + type, type, false, null, null,
                            "{\"targetCollection\": \"Target\", \"relationshipName\": \"Target\"}");
                } else {
                    request = new AddFieldRequest("field_" + type, type);
                }
                Field result = fieldService.addField(collectionId, request);
                assertThat(result.getType()).isEqualTo(canonical);
            }
        }

        @Test
        @DisplayName("should add field with valid string constraints")
        void shouldAddFieldWithValidStringConstraints() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            collection.setCurrentVersion(1);
            
            String constraints = "{\"minLength\": 1, \"maxLength\": 100, \"pattern\": \"^[a-z]+$\"}";
            AddFieldRequest request = new AddFieldRequest("name", "string", false, null, constraints);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.existsByCollectionIdAndNameAndActiveTrue(anyString(), anyString())).thenReturn(false);
            when(fieldRepository.save(any(Field.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(versionRepository.save(any(CollectionVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            Field result = fieldService.addField(collectionId, request);

            // Then
            assertThat(result.getConstraints()).isEqualTo(constraints);
        }

        @Test
        @DisplayName("should throw ValidationException for invalid constraints JSON")
        void shouldThrowExceptionForInvalidConstraintsJson() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            AddFieldRequest request = new AddFieldRequest("field", "string", false, null, "invalid json");
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.existsByCollectionIdAndNameAndActiveTrue(anyString(), anyString())).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> fieldService.addField(collectionId, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("constraints")
                    .hasMessageContaining("Invalid JSON");
        }

        @Test
        @DisplayName("should throw ValidationException when minLength > maxLength")
        void shouldThrowExceptionWhenMinLengthGreaterThanMaxLength() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            String constraints = "{\"minLength\": 100, \"maxLength\": 10}";
            AddFieldRequest request = new AddFieldRequest("field", "string", false, null, constraints);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.existsByCollectionIdAndNameAndActiveTrue(anyString(), anyString())).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> fieldService.addField(collectionId, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("minLength cannot be greater than maxLength");
        }

        @Test
        @DisplayName("should throw ValidationException for reference field without collection")
        void shouldThrowExceptionForReferenceWithoutCollection() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            String constraints = "{}";
            AddFieldRequest request = new AddFieldRequest("ref", "reference", false, null, constraints);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.existsByCollectionIdAndNameAndActiveTrue(anyString(), anyString())).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> fieldService.addField(collectionId, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("collection")
                    .hasMessageContaining("Reference fields must specify a target collection");
        }
    }


    @Nested
    @DisplayName("updateField")
    class UpdateFieldTests {

        @Test
        @DisplayName("should update field and create new collection version")
        void shouldUpdateFieldAndCreateVersion() {
            // Given
            String collectionId = "test-collection-id";
            String fieldId = "test-field-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            collection.setCurrentVersion(1);
            Field existingField = createTestField(fieldId, "oldName", "string", collection);
            
            UpdateFieldRequest request = new UpdateFieldRequest("newName", null, true, "Updated description", null);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)).thenReturn(Optional.of(existingField));
            when(fieldRepository.existsByCollectionIdAndNameAndActiveTrue(collectionId, "newName")).thenReturn(false);
            when(fieldRepository.save(any(Field.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(versionRepository.save(any(CollectionVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            Field result = fieldService.updateField(collectionId, fieldId, request);

            // Then
            assertThat(result.getName()).isEqualTo("newName");
            assertThat(result.isRequired()).isTrue();
            assertThat(result.getDescription()).isEqualTo("Updated description");
            assertThat(collection.getCurrentVersion()).isEqualTo(2);
            verify(versionRepository).save(any(CollectionVersion.class));
        }

        @Test
        @DisplayName("should only update provided fields")
        void shouldOnlyUpdateProvidedFields() {
            // Given
            String collectionId = "test-collection-id";
            String fieldId = "test-field-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            collection.setCurrentVersion(1);
            Field existingField = createTestField(fieldId, "originalName", "string", collection);
            existingField.setRequired(false);
            existingField.setDescription("Original description");
            
            UpdateFieldRequest request = new UpdateFieldRequest(null, null, null, "New description", null);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)).thenReturn(Optional.of(existingField));
            when(fieldRepository.save(any(Field.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(versionRepository.save(any(CollectionVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            Field result = fieldService.updateField(collectionId, fieldId, request);

            // Then
            assertThat(result.getName()).isEqualTo("originalName");
            assertThat(result.getType()).isEqualTo("string");
            assertThat(result.isRequired()).isFalse();
            assertThat(result.getDescription()).isEqualTo("New description");
        }

        @Test
        @DisplayName("should allow update with same name")
        void shouldAllowUpdateWithSameName() {
            // Given
            String collectionId = "test-collection-id";
            String fieldId = "test-field-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            collection.setCurrentVersion(1);
            Field existingField = createTestField(fieldId, "sameName", "string", collection);
            
            UpdateFieldRequest request = new UpdateFieldRequest("sameName", null, true, null, null);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)).thenReturn(Optional.of(existingField));
            when(fieldRepository.save(any(Field.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(versionRepository.save(any(CollectionVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            Field result = fieldService.updateField(collectionId, fieldId, request);

            // Then
            assertThat(result.getName()).isEqualTo("sameName");
            verify(fieldRepository, never()).existsByCollectionIdAndNameAndActiveTrue(anyString(), anyString());
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when new name already exists")
        void shouldThrowExceptionWhenNewNameExists() {
            // Given
            String collectionId = "test-collection-id";
            String fieldId = "test-field-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            Field existingField = createTestField(fieldId, "oldName", "string", collection);
            
            UpdateFieldRequest request = new UpdateFieldRequest("existingName", null, null, null, null);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)).thenReturn(Optional.of(existingField));
            when(fieldRepository.existsByCollectionIdAndNameAndActiveTrue(collectionId, "existingName")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> fieldService.updateField(collectionId, fieldId, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("existingName");
            
            verify(fieldRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when field not found")
        void shouldThrowExceptionWhenFieldNotFound() {
            // Given
            String collectionId = "test-collection-id";
            String fieldId = "nonexistent-field-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            
            UpdateFieldRequest request = new UpdateFieldRequest("newName", null, null, null, null);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> fieldService.updateField(collectionId, fieldId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Field")
                    .hasMessageContaining(fieldId);
        }

        @Test
        @DisplayName("should throw ValidationException for invalid type update")
        void shouldThrowExceptionForInvalidTypeUpdate() {
            // Given
            String collectionId = "test-collection-id";
            String fieldId = "test-field-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            Field existingField = createTestField(fieldId, "field", "string", collection);
            
            UpdateFieldRequest request = new UpdateFieldRequest(null, "invalidType", null, null, null);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)).thenReturn(Optional.of(existingField));

            // When/Then
            assertThatThrownBy(() -> fieldService.updateField(collectionId, fieldId, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("type");
        }
    }


    @Nested
    @DisplayName("deleteField")
    class DeleteFieldTests {

        @Test
        @DisplayName("should soft delete field and create new collection version")
        void shouldSoftDeleteFieldAndCreateVersion() {
            // Given
            String collectionId = "test-collection-id";
            String fieldId = "test-field-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            collection.setCurrentVersion(1);
            Field field = createTestField(fieldId, "fieldToDelete", "string", collection);
            field.setActive(true);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)).thenReturn(Optional.of(field));
            when(fieldRepository.save(any(Field.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(versionRepository.save(any(CollectionVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldRepository.findByCollectionIdAndActiveTrue(anyString())).thenReturn(Collections.emptyList());

            // When
            fieldService.deleteField(collectionId, fieldId);

            // Then
            ArgumentCaptor<Field> fieldCaptor = ArgumentCaptor.forClass(Field.class);
            verify(fieldRepository).save(fieldCaptor.capture());
            assertThat(fieldCaptor.getValue().isActive()).isFalse();
            
            assertThat(collection.getCurrentVersion()).isEqualTo(2);
            verify(versionRepository).save(any(CollectionVersion.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when collection not found")
        void shouldThrowExceptionWhenCollectionNotFound() {
            // Given
            String collectionId = "nonexistent-collection-id";
            String fieldId = "test-field-id";
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> fieldService.deleteField(collectionId, fieldId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Collection");
            
            verify(fieldRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when field not found")
        void shouldThrowExceptionWhenFieldNotFound() {
            // Given
            String collectionId = "test-collection-id";
            String fieldId = "nonexistent-field-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> fieldService.deleteField(collectionId, fieldId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Field")
                    .hasMessageContaining(fieldId);
            
            verify(fieldRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when field already deleted")
        void shouldThrowExceptionWhenFieldAlreadyDeleted() {
            // Given
            String collectionId = "test-collection-id";
            String fieldId = "deleted-field-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> fieldService.deleteField(collectionId, fieldId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getField")
    class GetFieldTests {

        @Test
        @DisplayName("should return field when found")
        void shouldReturnFieldWhenFound() {
            // Given
            String collectionId = "test-collection-id";
            String fieldId = "test-field-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            Field field = createTestField(fieldId, "testField", "string", collection);
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)).thenReturn(Optional.of(field));

            // When
            Field result = fieldService.getField(collectionId, fieldId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(fieldId);
            assertThat(result.getName()).isEqualTo("testField");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when field not found")
        void shouldThrowExceptionWhenFieldNotFound() {
            // Given
            String collectionId = "test-collection-id";
            String fieldId = "nonexistent-field-id";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> fieldService.getField(collectionId, fieldId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Field");
        }
    }

    // Helper methods
    private Collection createTestCollection(String id, String name) {
        Service service = new Service("test-service", "Test Service");
        service.setId("service-1");
        Collection collection = new Collection(service, name, "Test description");
        collection.setId(id);
        collection.setActive(true);
        collection.setCurrentVersion(1);
        return collection;
    }

    private Field createTestField(String id, String name, String type, Collection collection) {
        Field field = new Field(name, type);
        field.setId(id);
        field.setActive(true);
        field.setCollection(collection);
        return field;
    }
}
