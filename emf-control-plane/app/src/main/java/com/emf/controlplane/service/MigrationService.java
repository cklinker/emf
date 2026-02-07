package com.emf.controlplane.service;

import com.emf.controlplane.dto.MigrationPlanDto;
import com.emf.controlplane.dto.MigrationRunDto;
import com.emf.controlplane.dto.MigrationStepDto;
import com.emf.controlplane.dto.PlanMigrationRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.MigrationRun;
import com.emf.controlplane.entity.MigrationStep;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.repository.MigrationRunRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing schema migrations.
 * Handles migration planning, execution tracking, and history retrieval.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>7.1: Generate a migration plan showing steps to migrate from current to target schema</li>
 *   <li>7.2: Return the history of executed migrations</li>
 *   <li>7.3: Return migration run details including all steps and their status</li>
 *   <li>7.4: Track each migration step with success/failure status</li>
 * </ul>
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    // Migration operation types
    public static final String OP_ADD_FIELD = "ADD_FIELD";
    public static final String OP_REMOVE_FIELD = "REMOVE_FIELD";
    public static final String OP_MODIFY_FIELD = "MODIFY_FIELD";
    public static final String OP_RENAME_FIELD = "RENAME_FIELD";
    public static final String OP_CHANGE_TYPE = "CHANGE_TYPE";
    public static final String OP_CHANGE_REQUIRED = "CHANGE_REQUIRED";
    public static final String OP_UPDATE_COLLECTION = "UPDATE_COLLECTION";

    // Migration status values
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    private final MigrationRunRepository migrationRunRepository;
    private final CollectionRepository collectionRepository;
    private final FieldRepository fieldRepository;
    private final ObjectMapper objectMapper;

    public MigrationService(
            MigrationRunRepository migrationRunRepository,
            CollectionRepository collectionRepository,
            FieldRepository fieldRepository,
            ObjectMapper objectMapper) {
        this.migrationRunRepository = migrationRunRepository;
        this.collectionRepository = collectionRepository;
        this.fieldRepository = fieldRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Plans a migration from the current schema to the proposed schema.
     * Generates a list of steps required to transform the current schema to the target.
     * The migration plan is persisted to the database for tracking.
     *
     * @param request The migration plan request containing collection ID and proposed definition
     * @return The migration plan with generated steps
     * @throws ResourceNotFoundException if the collection does not exist
     *
     * Validates: Requirement 7.1
     */
    @Transactional
    public MigrationPlanDto planMigration(PlanMigrationRequest request) {
        log.info("Planning migration for collection: {}", request.getCollectionId());

        // Get the current collection
        Collection collection = collectionRepository.findByIdAndActiveTrue(request.getCollectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Collection", request.getCollectionId()));

        // Get current fields
        List<Field> currentFields = fieldRepository.findByCollectionIdAndActiveTrue(collection.getId());

        // Generate migration steps
        List<MigrationStepDto> steps = generateSteps(collection, currentFields, request.getProposedDefinition());

        // Create migration run entity
        Integer fromVersion = collection.getCurrentVersion();
        Integer toVersion = fromVersion + 1;

        MigrationRun migrationRun = new MigrationRun(collection.getId(), fromVersion, toVersion);
        migrationRun.setStatus(STATUS_PLANNED);
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            migrationRun.setTenantId(tenantId);
        }

        // Add steps to the migration run
        int stepNumber = 1;
        for (MigrationStepDto stepDto : steps) {
            MigrationStep step = new MigrationStep(stepNumber++, stepDto.getOperation());
            step.setDetails(stepDto.getDetails());
            step.setStatus(STATUS_PENDING);
            migrationRun.addStep(step);
        }

        // Save the migration run
        migrationRun = migrationRunRepository.save(migrationRun);

        // Build the response DTO
        MigrationPlanDto planDto = new MigrationPlanDto(
                migrationRun.getId(),
                collection.getId(),
                collection.getName(),
                fromVersion,
                toVersion
        );
        planDto.setStatus(STATUS_PLANNED);
        planDto.setCreatedAt(migrationRun.getCreatedAt());

        // Add steps to the DTO
        for (MigrationStep step : migrationRun.getSteps()) {
            MigrationStepDto stepDto = new MigrationStepDto(
                    step.getId(),
                    step.getStepNumber(),
                    step.getOperation(),
                    step.getStatus()
            );
            stepDto.setDetails(step.getDetails());
            stepDto.setCreatedAt(step.getCreatedAt());
            planDto.addStep(stepDto);
        }

        log.info("Created migration plan {} with {} steps for collection {}", 
                planDto.getId(), planDto.getStepCount(), collection.getId());

        return planDto;
    }

    /**
     * Lists all migration runs ordered by creation date descending.
     *
     * @return List of all migration runs
     *
     * Validates: Requirement 7.2
     */
    @Transactional(readOnly = true)
    public List<MigrationRunDto> listMigrationRuns() {
        log.debug("Listing all migration runs");

        List<MigrationRun> runs = migrationRunRepository.findAll();
        
        // Sort by createdAt descending
        runs.sort((a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        return runs.stream()
                .map(MigrationRunDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific migration run by ID.
     *
     * @param id The migration run ID
     * @return The migration run with all steps
     * @throws ResourceNotFoundException if the migration run does not exist
     *
     * Validates: Requirement 7.3
     */
    @Transactional(readOnly = true)
    public MigrationRunDto getMigrationRun(String id) {
        log.debug("Getting migration run: {}", id);

        MigrationRun run = migrationRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MigrationRun", id));

        return MigrationRunDto.fromEntity(run);
    }

    /**
     * Generates migration steps by comparing the current schema with the proposed schema.
     * Identifies fields to add, remove, and modify.
     *
     * @param collection The current collection
     * @param currentFields The current fields in the collection
     * @param proposed The proposed schema definition
     * @return List of migration steps
     *
     * Validates: Requirement 7.1
     */
    private List<MigrationStepDto> generateSteps(
            Collection collection,
            List<Field> currentFields,
            PlanMigrationRequest.ProposedDefinition proposed) {

        List<MigrationStepDto> steps = new ArrayList<>();
        int stepNumber = 1;

        // Check for collection-level changes
        if (proposed.getName() != null && !proposed.getName().equals(collection.getName())) {
            steps.add(createStep(stepNumber++, OP_UPDATE_COLLECTION, 
                    buildCollectionChangeDetails("name", collection.getName(), proposed.getName())));
        }

        if (proposed.getDescription() != null && !proposed.getDescription().equals(collection.getDescription())) {
            steps.add(createStep(stepNumber++, OP_UPDATE_COLLECTION,
                    buildCollectionChangeDetails("description", collection.getDescription(), proposed.getDescription())));
        }

        // Build maps for field comparison
        Map<String, Field> currentFieldMap = currentFields.stream()
                .collect(Collectors.toMap(Field::getName, f -> f, (a, b) -> a));

        Map<String, PlanMigrationRequest.ProposedField> proposedFieldMap = new HashMap<>();
        if (proposed.getFields() != null) {
            for (PlanMigrationRequest.ProposedField pf : proposed.getFields()) {
                if (pf.getName() != null) {
                    proposedFieldMap.put(pf.getName(), pf);
                }
            }
        }

        // Find fields to add (in proposed but not in current)
        for (PlanMigrationRequest.ProposedField proposedField : proposedFieldMap.values()) {
            if (!currentFieldMap.containsKey(proposedField.getName())) {
                steps.add(createStep(stepNumber++, OP_ADD_FIELD,
                        buildAddFieldDetails(proposedField)));
            }
        }

        // Find fields to remove (in current but not in proposed)
        for (Field currentField : currentFields) {
            if (!proposedFieldMap.containsKey(currentField.getName())) {
                steps.add(createStep(stepNumber++, OP_REMOVE_FIELD,
                        buildRemoveFieldDetails(currentField)));
            }
        }

        // Find fields to modify (in both but with differences)
        for (Field currentField : currentFields) {
            PlanMigrationRequest.ProposedField proposedField = proposedFieldMap.get(currentField.getName());
            if (proposedField != null) {
                List<MigrationStepDto> modifySteps = generateFieldModificationSteps(
                        currentField, proposedField, stepNumber);
                stepNumber += modifySteps.size();
                steps.addAll(modifySteps);
            }
        }

        // Re-number steps sequentially
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setStepNumber(i + 1);
        }

        return steps;
    }

    /**
     * Generates modification steps for a field that exists in both current and proposed schemas.
     */
    private List<MigrationStepDto> generateFieldModificationSteps(
            Field current,
            PlanMigrationRequest.ProposedField proposed,
            int startStepNumber) {

        List<MigrationStepDto> steps = new ArrayList<>();
        int stepNumber = startStepNumber;

        // Check for type change
        if (proposed.getType() != null && !proposed.getType().equals(current.getType())) {
            steps.add(createStep(stepNumber++, OP_CHANGE_TYPE,
                    buildFieldChangeDetails(current.getName(), "type", current.getType(), proposed.getType())));
        }

        // Check for required change
        if (proposed.isRequired() != current.isRequired()) {
            steps.add(createStep(stepNumber++, OP_CHANGE_REQUIRED,
                    buildFieldChangeDetails(current.getName(), "required", 
                            String.valueOf(current.isRequired()), String.valueOf(proposed.isRequired()))));
        }

        // Check for description change
        if (proposed.getDescription() != null && !proposed.getDescription().equals(current.getDescription())) {
            steps.add(createStep(stepNumber++, OP_MODIFY_FIELD,
                    buildFieldChangeDetails(current.getName(), "description", 
                            current.getDescription(), proposed.getDescription())));
        }

        // Check for constraints change
        if (proposed.getConstraints() != null && !proposed.getConstraints().equals(current.getConstraints())) {
            steps.add(createStep(stepNumber++, OP_MODIFY_FIELD,
                    buildFieldChangeDetails(current.getName(), "constraints", 
                            current.getConstraints(), proposed.getConstraints())));
        }

        return steps;
    }

    /**
     * Creates a migration step DTO.
     */
    private MigrationStepDto createStep(int stepNumber, String operation, String details) {
        MigrationStepDto step = new MigrationStepDto();
        step.setStepNumber(stepNumber);
        step.setOperation(operation);
        step.setDetails(details);
        step.setStatus(STATUS_PENDING);
        return step;
    }

    /**
     * Builds JSON details for a collection-level change.
     */
    private String buildCollectionChangeDetails(String property, String oldValue, String newValue) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("property", property);
            details.put("oldValue", oldValue);
            details.put("newValue", newValue);
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize collection change details", e);
            return "{}";
        }
    }

    /**
     * Builds JSON details for adding a field.
     */
    private String buildAddFieldDetails(PlanMigrationRequest.ProposedField field) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("fieldName", field.getName());
            details.put("fieldType", field.getType());
            details.put("required", field.isRequired());
            if (field.getDescription() != null) {
                details.put("description", field.getDescription());
            }
            if (field.getConstraints() != null) {
                details.put("constraints", field.getConstraints());
            }
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize add field details", e);
            return "{}";
        }
    }

    /**
     * Builds JSON details for removing a field.
     */
    private String buildRemoveFieldDetails(Field field) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("fieldId", field.getId());
            details.put("fieldName", field.getName());
            details.put("fieldType", field.getType());
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize remove field details", e);
            return "{}";
        }
    }

    /**
     * Builds JSON details for a field property change.
     */
    private String buildFieldChangeDetails(String fieldName, String property, String oldValue, String newValue) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("fieldName", fieldName);
            details.put("property", property);
            details.put("oldValue", oldValue);
            details.put("newValue", newValue);
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize field change details", e);
            return "{}";
        }
    }
}
