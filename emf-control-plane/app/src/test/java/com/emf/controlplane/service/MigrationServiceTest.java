package com.emf.controlplane.service;

import com.emf.controlplane.dto.MigrationPlanDto;
import com.emf.controlplane.dto.MigrationRunDto;
import com.emf.controlplane.dto.PlanMigrationRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.MigrationRun;
import com.emf.controlplane.entity.MigrationStep;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.repository.MigrationRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MigrationService.
 * Tests migration planning, listing, and retrieval operations.
 */
@ExtendWith(MockitoExtension.class)
class MigrationServiceTest {

    @Mock
    private MigrationRunRepository migrationRunRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private FieldRepository fieldRepository;

    private ObjectMapper objectMapper;

    private MigrationService migrationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        migrationService = new MigrationService(
                migrationRunRepository,
                collectionRepository,
                fieldRepository,
                objectMapper
        );
    }

    @Nested
    @DisplayName("planMigration")
    class PlanMigrationTests {

        @Test
        @DisplayName("should create migration plan with add field steps")
        void shouldCreatePlanWithAddFieldSteps() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "TestCollection");
            collection.setDescription("Description"); // Set description to match proposed
            
            // Current schema has no fields
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue(collectionId)).thenReturn(Collections.emptyList());
            when(migrationRunRepository.save(any(MigrationRun.class))).thenAnswer(invocation -> {
                MigrationRun run = invocation.getArgument(0);
                run.setCreatedAt(Instant.now());
                return run;
            });

            // Proposed schema has one new field
            PlanMigrationRequest.ProposedField newField = new PlanMigrationRequest.ProposedField(
                    null, "newField", "string", true);
            PlanMigrationRequest.ProposedDefinition proposed = new PlanMigrationRequest.ProposedDefinition(
                    "TestCollection", "Description", List.of(newField));
            PlanMigrationRequest request = new PlanMigrationRequest(collectionId, proposed);

            // When
            MigrationPlanDto result = migrationService.planMigration(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCollectionId()).isEqualTo(collectionId);
            assertThat(result.getFromVersion()).isEqualTo(1);
            assertThat(result.getToVersion()).isEqualTo(2);
            assertThat(result.getStatus()).isEqualTo("PLANNED");
            assertThat(result.getSteps()).hasSize(1);
            assertThat(result.getSteps().get(0).getOperation()).isEqualTo(MigrationService.OP_ADD_FIELD);
            
            verify(migrationRunRepository).save(any(MigrationRun.class));
        }

        @Test
        @DisplayName("should create migration plan with remove field steps")
        void shouldCreatePlanWithRemoveFieldSteps() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "TestCollection");
            collection.setDescription("Description"); // Set description to match proposed
            
            // Current schema has one field
            Field existingField = createTestField("field-id", "existingField", "string");
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue(collectionId)).thenReturn(List.of(existingField));
            when(migrationRunRepository.save(any(MigrationRun.class))).thenAnswer(invocation -> {
                MigrationRun run = invocation.getArgument(0);
                run.setCreatedAt(Instant.now());
                return run;
            });

            // Proposed schema has no fields
            PlanMigrationRequest.ProposedDefinition proposed = new PlanMigrationRequest.ProposedDefinition(
                    "TestCollection", "Description", Collections.emptyList());
            PlanMigrationRequest request = new PlanMigrationRequest(collectionId, proposed);

            // When
            MigrationPlanDto result = migrationService.planMigration(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSteps()).hasSize(1);
            assertThat(result.getSteps().get(0).getOperation()).isEqualTo(MigrationService.OP_REMOVE_FIELD);
        }

        @Test
        @DisplayName("should create migration plan with modify field steps")
        void shouldCreatePlanWithModifyFieldSteps() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "TestCollection");
            collection.setDescription("Description"); // Set description to match proposed
            
            // Current schema has one field with type "string"
            Field existingField = createTestField("field-id", "myField", "string");
            existingField.setRequired(false);
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue(collectionId)).thenReturn(List.of(existingField));
            when(migrationRunRepository.save(any(MigrationRun.class))).thenAnswer(invocation -> {
                MigrationRun run = invocation.getArgument(0);
                run.setCreatedAt(Instant.now());
                return run;
            });

            // Proposed schema has same field but with different type and required
            PlanMigrationRequest.ProposedField modifiedField = new PlanMigrationRequest.ProposedField(
                    "field-id", "myField", "number", true);
            PlanMigrationRequest.ProposedDefinition proposed = new PlanMigrationRequest.ProposedDefinition(
                    "TestCollection", "Description", List.of(modifiedField));
            PlanMigrationRequest request = new PlanMigrationRequest(collectionId, proposed);

            // When
            MigrationPlanDto result = migrationService.planMigration(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSteps()).hasSize(2); // CHANGE_TYPE and CHANGE_REQUIRED
            
            List<String> operations = result.getSteps().stream()
                    .map(s -> s.getOperation())
                    .toList();
            assertThat(operations).contains(MigrationService.OP_CHANGE_TYPE);
            assertThat(operations).contains(MigrationService.OP_CHANGE_REQUIRED);
        }

        @Test
        @DisplayName("should create empty plan when no changes detected")
        void shouldCreateEmptyPlanWhenNoChanges() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "TestCollection");
            collection.setDescription("Description");
            
            // Current schema has one field
            Field existingField = createTestField("field-id", "myField", "string");
            existingField.setRequired(true);
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue(collectionId)).thenReturn(List.of(existingField));
            when(migrationRunRepository.save(any(MigrationRun.class))).thenAnswer(invocation -> {
                MigrationRun run = invocation.getArgument(0);
                run.setCreatedAt(Instant.now());
                return run;
            });

            // Proposed schema is identical
            PlanMigrationRequest.ProposedField sameField = new PlanMigrationRequest.ProposedField(
                    "field-id", "myField", "string", true);
            PlanMigrationRequest.ProposedDefinition proposed = new PlanMigrationRequest.ProposedDefinition(
                    "TestCollection", "Description", List.of(sameField));
            PlanMigrationRequest request = new PlanMigrationRequest(collectionId, proposed);

            // When
            MigrationPlanDto result = migrationService.planMigration(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSteps()).isEmpty();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when collection not found")
        void shouldThrowExceptionWhenCollectionNotFound() {
            // Given
            String collectionId = "nonexistent-id";
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.empty());

            PlanMigrationRequest.ProposedDefinition proposed = new PlanMigrationRequest.ProposedDefinition(
                    "Test", "Description", Collections.emptyList());
            PlanMigrationRequest request = new PlanMigrationRequest(collectionId, proposed);

            // When/Then
            assertThatThrownBy(() -> migrationService.planMigration(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Collection")
                    .hasMessageContaining(collectionId);
            
            verify(migrationRunRepository, never()).save(any());
        }

        @Test
        @DisplayName("should persist migration run with steps")
        void shouldPersistMigrationRunWithSteps() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "TestCollection");
            collection.setDescription("Description"); // Set description to match proposed
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue(collectionId)).thenReturn(Collections.emptyList());
            
            ArgumentCaptor<MigrationRun> runCaptor = ArgumentCaptor.forClass(MigrationRun.class);
            when(migrationRunRepository.save(runCaptor.capture())).thenAnswer(invocation -> {
                MigrationRun run = invocation.getArgument(0);
                run.setCreatedAt(Instant.now());
                return run;
            });

            PlanMigrationRequest.ProposedField newField = new PlanMigrationRequest.ProposedField(
                    null, "newField", "string", true);
            PlanMigrationRequest.ProposedDefinition proposed = new PlanMigrationRequest.ProposedDefinition(
                    "TestCollection", "Description", List.of(newField));
            PlanMigrationRequest request = new PlanMigrationRequest(collectionId, proposed);

            // When
            migrationService.planMigration(request);

            // Then
            MigrationRun savedRun = runCaptor.getValue();
            assertThat(savedRun.getCollectionId()).isEqualTo(collectionId);
            assertThat(savedRun.getFromVersion()).isEqualTo(1);
            assertThat(savedRun.getToVersion()).isEqualTo(2);
            assertThat(savedRun.getStatus()).isEqualTo("PLANNED");
            assertThat(savedRun.getSteps()).hasSize(1);
            assertThat(savedRun.getSteps().get(0).getOperation()).isEqualTo(MigrationService.OP_ADD_FIELD);
        }

        @Test
        @DisplayName("should detect collection name change")
        void shouldDetectCollectionNameChange() {
            // Given
            String collectionId = "test-collection-id";
            Collection collection = createTestCollection(collectionId, "OldName");
            collection.setDescription("Description"); // Set description to match proposed
            
            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldRepository.findByCollectionIdAndActiveTrue(collectionId)).thenReturn(Collections.emptyList());
            when(migrationRunRepository.save(any(MigrationRun.class))).thenAnswer(invocation -> {
                MigrationRun run = invocation.getArgument(0);
                run.setCreatedAt(Instant.now());
                return run;
            });

            PlanMigrationRequest.ProposedDefinition proposed = new PlanMigrationRequest.ProposedDefinition(
                    "NewName", "Description", Collections.emptyList());
            PlanMigrationRequest request = new PlanMigrationRequest(collectionId, proposed);

            // When
            MigrationPlanDto result = migrationService.planMigration(request);

            // Then
            assertThat(result.getSteps()).hasSize(1);
            assertThat(result.getSteps().get(0).getOperation()).isEqualTo(MigrationService.OP_UPDATE_COLLECTION);
        }
    }

    @Nested
    @DisplayName("listMigrationRuns")
    class ListMigrationRunsTests {

        @Test
        @DisplayName("should return all migration runs ordered by creation date")
        void shouldReturnAllMigrationRunsOrdered() {
            // Given
            MigrationRun run1 = createTestMigrationRun("run-1", "collection-1", 1, 2);
            run1.setCreatedAt(Instant.parse("2024-01-01T10:00:00Z"));
            
            MigrationRun run2 = createTestMigrationRun("run-2", "collection-1", 2, 3);
            run2.setCreatedAt(Instant.parse("2024-01-02T10:00:00Z"));
            
            when(migrationRunRepository.findAll()).thenReturn(new ArrayList<>(List.of(run1, run2)));

            // When
            List<MigrationRunDto> result = migrationService.listMigrationRuns();

            // Then
            assertThat(result).hasSize(2);
            // Should be ordered by createdAt descending
            assertThat(result.get(0).getId()).isEqualTo("run-2");
            assertThat(result.get(1).getId()).isEqualTo("run-1");
        }

        @Test
        @DisplayName("should return empty list when no migration runs exist")
        void shouldReturnEmptyListWhenNoRuns() {
            // Given
            when(migrationRunRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            List<MigrationRunDto> result = migrationService.listMigrationRuns();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should include steps in migration run DTOs")
        void shouldIncludeStepsInDtos() {
            // Given
            MigrationRun run = createTestMigrationRun("run-1", "collection-1", 1, 2);
            run.setCreatedAt(Instant.now());
            
            MigrationStep step1 = new MigrationStep(1, "ADD_FIELD");
            step1.setStatus("PENDING");
            run.addStep(step1);
            
            MigrationStep step2 = new MigrationStep(2, "REMOVE_FIELD");
            step2.setStatus("PENDING");
            run.addStep(step2);
            
            when(migrationRunRepository.findAll()).thenReturn(new ArrayList<>(List.of(run)));

            // When
            List<MigrationRunDto> result = migrationService.listMigrationRuns();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSteps()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getMigrationRun")
    class GetMigrationRunTests {

        @Test
        @DisplayName("should return migration run when found")
        void shouldReturnMigrationRunWhenFound() {
            // Given
            String runId = "test-run-id";
            MigrationRun run = createTestMigrationRun(runId, "collection-1", 1, 2);
            run.setCreatedAt(Instant.now());
            
            MigrationStep step = new MigrationStep(1, "ADD_FIELD");
            step.setStatus("COMPLETED");
            step.setDetails("{\"fieldName\":\"newField\"}");
            run.addStep(step);
            
            when(migrationRunRepository.findById(runId)).thenReturn(Optional.of(run));

            // When
            MigrationRunDto result = migrationService.getMigrationRun(runId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(runId);
            assertThat(result.getCollectionId()).isEqualTo("collection-1");
            assertThat(result.getFromVersion()).isEqualTo(1);
            assertThat(result.getToVersion()).isEqualTo(2);
            assertThat(result.getSteps()).hasSize(1);
            assertThat(result.getSteps().get(0).getOperation()).isEqualTo("ADD_FIELD");
            assertThat(result.getSteps().get(0).getStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when migration run not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            String runId = "nonexistent-id";
            when(migrationRunRepository.findById(runId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> migrationService.getMigrationRun(runId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("MigrationRun")
                    .hasMessageContaining(runId);
        }

        @Test
        @DisplayName("should return migration run with error message when failed")
        void shouldReturnRunWithErrorMessage() {
            // Given
            String runId = "failed-run-id";
            MigrationRun run = createTestMigrationRun(runId, "collection-1", 1, 2);
            run.setStatus("FAILED");
            run.setErrorMessage("Migration failed due to constraint violation");
            run.setCreatedAt(Instant.now());
            
            when(migrationRunRepository.findById(runId)).thenReturn(Optional.of(run));

            // When
            MigrationRunDto result = migrationService.getMigrationRun(runId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("FAILED");
            assertThat(result.getErrorMessage()).isEqualTo("Migration failed due to constraint violation");
        }
    }

    // Helper methods

    private Collection createTestCollection(String id, String name) {
        com.emf.controlplane.entity.Service service = new com.emf.controlplane.entity.Service("test-service", "Test Service");
        service.setId("service-1");
        Collection collection = new Collection(service, name, "Test description");
        collection.setId(id);
        collection.setActive(true);
        collection.setCurrentVersion(1);
        return collection;
    }

    private Field createTestField(String id, String name, String type) {
        Field field = new Field(name, type);
        field.setId(id);
        field.setActive(true);
        field.setRequired(false);
        return field;
    }

    private MigrationRun createTestMigrationRun(String id, String collectionId, 
                                                 Integer fromVersion, Integer toVersion) {
        MigrationRun run = new MigrationRun(collectionId, fromVersion, toVersion);
        run.setId(id);
        run.setStatus("PLANNED");
        return run;
    }
}
