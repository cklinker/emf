package com.emf.controlplane.service;

import com.emf.controlplane.config.CacheConfig;
import com.emf.controlplane.dto.AddFieldRequest;
import com.emf.controlplane.dto.UpdateFieldRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.CollectionVersion;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.runtime.event.ChangeType;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.CollectionVersionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.validation.FieldTypeValidatorRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing field definitions within collections.
 * Handles CRUD operations with versioning and event publishing.
 * 
 * Requirements satisfied:
 * - 2.1: Return list of active fields for a collection
 * - 2.2: Add field creates new CollectionVersion with the added field
 * - 2.4: Update field creates new CollectionVersion with the updated field
 * - 2.5: Delete field creates new CollectionVersion marking the field as inactive
 */
@Service
public class FieldService {

    private static final Logger log = LoggerFactory.getLogger(FieldService.class);

    /**
     * Legacy type aliases: maps old lowercase names to canonical FieldType names.
     */
    public static final Map<String, String> TYPE_ALIASES = Map.ofEntries(
            // Legacy aliases (backward compat)
            Map.entry("string", "STRING"),
            Map.entry("number", "DOUBLE"),
            Map.entry("boolean", "BOOLEAN"),
            Map.entry("date", "DATE"),
            Map.entry("datetime", "DATETIME"),
            Map.entry("reference", "REFERENCE"),
            Map.entry("array", "ARRAY"),
            Map.entry("object", "JSON"),
            // All canonical types (accepted as lowercase)
            Map.entry("integer", "INTEGER"),
            Map.entry("long", "LONG"),
            Map.entry("double", "DOUBLE"),
            Map.entry("json", "JSON"),
            Map.entry("picklist", "PICKLIST"),
            Map.entry("multi_picklist", "MULTI_PICKLIST"),
            Map.entry("auto_number", "AUTO_NUMBER"),
            Map.entry("currency", "CURRENCY"),
            Map.entry("percent", "PERCENT"),
            Map.entry("phone", "PHONE"),
            Map.entry("email", "EMAIL"),
            Map.entry("url", "URL"),
            Map.entry("rich_text", "RICH_TEXT"),
            Map.entry("encrypted", "ENCRYPTED"),
            Map.entry("external_id", "EXTERNAL_ID"),
            Map.entry("geolocation", "GEOLOCATION"),
            Map.entry("lookup", "LOOKUP"),
            Map.entry("master_detail", "MASTER_DETAIL"),
            Map.entry("formula", "FORMULA"),
            Map.entry("rollup_summary", "ROLLUP_SUMMARY")
    );

    /**
     * Valid field types supported by the system (for backward compatibility checks).
     */
    public static final Set<String> VALID_FIELD_TYPES = TYPE_ALIASES.keySet();

    private final FieldRepository fieldRepository;
    private final CollectionRepository collectionRepository;
    private final CollectionVersionRepository versionRepository;
    private final ObjectMapper objectMapper;
    private final ConfigEventPublisher eventPublisher;
    private final FieldTypeValidatorRegistry fieldTypeValidatorRegistry;

    public FieldService(
            FieldRepository fieldRepository,
            CollectionRepository collectionRepository,
            CollectionVersionRepository versionRepository,
            ObjectMapper objectMapper,
            @Nullable ConfigEventPublisher eventPublisher,
            FieldTypeValidatorRegistry fieldTypeValidatorRegistry) {
        this.fieldRepository = fieldRepository;
        this.collectionRepository = collectionRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.fieldTypeValidatorRegistry = fieldTypeValidatorRegistry;
    }

    /**
     * Lists all active fields for a collection.
     * 
     * @param collectionId The collection ID
     * @return List of active fields for the collection
     * @throws ResourceNotFoundException if the collection does not exist or is inactive
     * 
     * Validates: Requirement 2.1
     */
    @Transactional(readOnly = true)
    public List<Field> listFields(String collectionId) {
        log.debug("Listing fields for collection: {}", collectionId);
        
        // Verify collection exists and is active
        Collection collection = collectionRepository.findByIdAndActiveTrue(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", collectionId));
        
        return fieldRepository.findByCollectionIdAndActiveTrue(collectionId);
    }

    /**
     * Adds a new field to a collection.
     * Creates a new CollectionVersion with the added field.
     * Evicts the collection from cache after the field is added.
     * 
     * @param collectionId The collection ID to add the field to
     * @param request The field creation request
     * @return The created field
     * @throws ResourceNotFoundException if the collection does not exist or is inactive
     * @throws DuplicateResourceException if a field with the same name already exists
     * @throws ValidationException if the field type or constraints are invalid
     * 
     * Validates: Requirements 2.2, 2.3, 14.3
     */
    @Transactional
    @CacheEvict(value = CacheConfig.COLLECTIONS_CACHE, key = "#collectionId")
    public Field addField(String collectionId, AddFieldRequest request) {
        log.info("Adding field '{}' to collection: {}", request.getName(), collectionId);
        
        // Verify collection exists and is active
        Collection collection = collectionRepository.findByIdAndActiveTrue(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", collectionId));
        
        // Check for duplicate field name
        if (fieldRepository.existsByCollectionIdAndNameAndActiveTrue(collectionId, request.getName())) {
            throw new DuplicateResourceException("Field", "name", request.getName());
        }

        // Resolve type (supports legacy aliases)
        String canonicalType = resolveFieldType(request.getType());

        // Validate fieldTypeConfig via type-specific validator
        validateFieldTypeConfig(canonicalType, request.getFieldTypeConfig());

        // Validate constraints if provided
        if (request.getConstraints() != null && !request.getConstraints().isBlank()) {
            validateConstraints(canonicalType.toLowerCase(), request.getConstraints());
        }

        // Create the field entity
        Field field = new Field(request.getName(), canonicalType);
        field.setRequired(request.isRequired());
        field.setDescription(request.getDescription());
        field.setConstraints(request.getConstraints());
        field.setFieldTypeConfig(request.getFieldTypeConfig());
        field.setActive(true);
        field.setCollection(collection);

        // For AUTO_NUMBER: set sequence name
        if ("AUTO_NUMBER".equals(canonicalType)) {
            String seqName = "seq_" + collection.getName() + "_" + request.getName();
            field.setAutoNumberSequenceName(seqName);
        }

        // For LOOKUP / MASTER_DETAIL: set relationship metadata
        if ("LOOKUP".equals(canonicalType) || "MASTER_DETAIL".equals(canonicalType)) {
            configureRelationshipField(field, canonicalType, collection, request);
        }

        // Save the field
        field = fieldRepository.save(field);
        
        // Increment collection version and create new version record
        collection.setCurrentVersion(collection.getCurrentVersion() + 1);
        createNewVersion(collection);
        collectionRepository.save(collection);
        
        // Publish event (stubbed for now - will be implemented in task 11)
        publishCollectionChangedEvent(collection);
        
        log.info("Added field '{}' with id: {} to collection: {}", request.getName(), field.getId(), collectionId);
        return field;
    }

    /**
     * Updates an existing field in a collection.
     * Creates a new CollectionVersion with the updated field.
     * Evicts the collection from cache after the field is updated.
     * 
     * @param collectionId The collection ID
     * @param fieldId The field ID to update
     * @param request The field update request
     * @return The updated field
     * @throws ResourceNotFoundException if the collection or field does not exist or is inactive
     * @throws DuplicateResourceException if updating name to one that already exists
     * @throws ValidationException if the field type or constraints are invalid
     * 
     * Validates: Requirements 2.4, 14.3
     */
    @Transactional
    @CacheEvict(value = CacheConfig.COLLECTIONS_CACHE, key = "#collectionId")
    public Field updateField(String collectionId, String fieldId, UpdateFieldRequest request) {
        log.info("Updating field '{}' in collection: {}", fieldId, collectionId);
        
        // Verify collection exists and is active
        Collection collection = collectionRepository.findByIdAndActiveTrue(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", collectionId));
        
        // Find the field
        Field field = fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", fieldId));
        
        // Check for duplicate name if name is being changed
        if (request.getName() != null && !request.getName().equals(field.getName())) {
            if (fieldRepository.existsByCollectionIdAndNameAndActiveTrue(collectionId, request.getName())) {
                throw new DuplicateResourceException("Field", "name", request.getName());
            }
            field.setName(request.getName());
        }
        
        // Update type if provided
        if (request.getType() != null) {
            String canonicalType = resolveFieldType(request.getType());
            field.setType(canonicalType);
        }
        
        // Update required if provided
        if (request.getRequired() != null) {
            field.setRequired(request.getRequired());
        }
        
        // Update description if provided
        if (request.getDescription() != null) {
            field.setDescription(request.getDescription());
        }
        
        // Update constraints if provided
        if (request.getConstraints() != null) {
            String effectiveType = request.getType() != null ? request.getType() : field.getType();
            if (!request.getConstraints().isBlank()) {
                validateConstraints(effectiveType, request.getConstraints());
            }
            field.setConstraints(request.getConstraints().isBlank() ? null : request.getConstraints());
        }
        
        // Save the field
        field = fieldRepository.save(field);
        
        // Increment collection version and create new version record
        collection.setCurrentVersion(collection.getCurrentVersion() + 1);
        createNewVersion(collection);
        collectionRepository.save(collection);
        
        // Publish event (stubbed for now - will be implemented in task 11)
        publishCollectionChangedEvent(collection);
        
        log.info("Updated field '{}' in collection: {}, new version: {}", fieldId, collectionId, collection.getCurrentVersion());
        return field;
    }

    /**
     * Soft-deletes a field by marking it as inactive.
     * Creates a new CollectionVersion with the field marked as inactive.
     * Evicts the collection from cache after the field is deleted.
     * 
     * @param collectionId The collection ID
     * @param fieldId The field ID to delete
     * @throws ResourceNotFoundException if the collection or field does not exist or is inactive
     * 
     * Validates: Requirements 2.5, 14.3
     */
    @Transactional
    @CacheEvict(value = CacheConfig.COLLECTIONS_CACHE, key = "#collectionId")
    public void deleteField(String collectionId, String fieldId) {
        log.info("Deleting field '{}' from collection: {}", fieldId, collectionId);
        
        // Verify collection exists and is active
        Collection collection = collectionRepository.findByIdAndActiveTrue(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", collectionId));
        
        // Find the field
        Field field = fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", fieldId));
        
        // Soft delete - mark as inactive
        field.setActive(false);
        fieldRepository.save(field);
        
        // Increment collection version and create new version record
        collection.setCurrentVersion(collection.getCurrentVersion() + 1);
        createNewVersion(collection);
        collectionRepository.save(collection);
        
        // Publish event (stubbed for now - will be implemented in task 11)
        publishCollectionChangedEvent(collection);
        
        log.info("Soft-deleted field '{}' from collection: {}, new version: {}", fieldId, collectionId, collection.getCurrentVersion());
    }

    /**
     * Retrieves a specific field by ID.
     * 
     * @param collectionId The collection ID
     * @param fieldId The field ID
     * @return The field if found
     * @throws ResourceNotFoundException if the collection or field does not exist or is inactive
     */
    @Transactional(readOnly = true)
    public Field getField(String collectionId, String fieldId) {
        log.debug("Getting field '{}' from collection: {}", fieldId, collectionId);
        
        // Verify collection exists and is active
        collectionRepository.findByIdAndActiveTrue(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", collectionId));
        
        return fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", fieldId));
    }

    /**
     * Resolves a field type input to its canonical name.
     * Supports legacy lowercase names and new type names.
     *
     * @param input The field type string from the request
     * @return The canonical field type name (e.g., "STRING", "DOUBLE")
     * @throws ValidationException if the type is not valid
     */
    public static String resolveFieldType(String input) {
        String canonical = TYPE_ALIASES.get(input.toLowerCase());
        if (canonical == null) {
            throw new ValidationException("type",
                    String.format("Invalid field type '%s'. Must be one of: %s",
                            input, String.join(", ", TYPE_ALIASES.keySet())));
        }
        return canonical;
    }

    /**
     * Validates fieldTypeConfig via the type-specific validator, if one exists.
     */
    private void validateFieldTypeConfig(String canonicalType, String fieldTypeConfigJson) {
        try {
            JsonNode configNode = fieldTypeConfigJson != null && !fieldTypeConfigJson.isBlank()
                    ? objectMapper.readTree(fieldTypeConfigJson)
                    : null;
            fieldTypeValidatorRegistry.getValidator(canonicalType)
                    .ifPresent(v -> v.validateConfig(configNode));
        } catch (JsonProcessingException e) {
            throw new ValidationException("fieldTypeConfig", "Invalid JSON format: " + e.getMessage());
        }
    }

    /**
     * Validates that the constraints JSON is valid for the given field type.
     * 
     * @param type The field type
     * @param constraints The constraints JSON string
     * @throws ValidationException if the constraints are invalid
     */
    private void validateConstraints(String type, String constraints) {
        try {
            JsonNode constraintsNode = objectMapper.readTree(constraints);
            
            // Validate constraints based on field type
            switch (type) {
                case "string":
                    validateStringConstraints(constraintsNode);
                    break;
                case "number":
                    validateNumberConstraints(constraintsNode);
                    break;
                case "reference":
                    validateReferenceConstraints(constraintsNode);
                    break;
                case "array":
                    validateArrayConstraints(constraintsNode);
                    break;
                // boolean, date, datetime, object don't have specific constraints to validate
            }
        } catch (JsonProcessingException e) {
            throw new ValidationException("constraints", "Invalid JSON format: " + e.getMessage());
        }
    }

    /**
     * Validates string field constraints.
     */
    private void validateStringConstraints(JsonNode constraints) {
        if (constraints.has("minLength")) {
            JsonNode minLength = constraints.get("minLength");
            if (!minLength.isInt() || minLength.asInt() < 0) {
                throw new ValidationException("constraints.minLength", "Must be a non-negative integer");
            }
        }
        if (constraints.has("maxLength")) {
            JsonNode maxLength = constraints.get("maxLength");
            if (!maxLength.isInt() || maxLength.asInt() < 0) {
                throw new ValidationException("constraints.maxLength", "Must be a non-negative integer");
            }
        }
        if (constraints.has("minLength") && constraints.has("maxLength")) {
            int min = constraints.get("minLength").asInt();
            int max = constraints.get("maxLength").asInt();
            if (min > max) {
                throw new ValidationException("constraints", "minLength cannot be greater than maxLength");
            }
        }
        if (constraints.has("pattern")) {
            JsonNode pattern = constraints.get("pattern");
            if (!pattern.isTextual()) {
                throw new ValidationException("constraints.pattern", "Must be a string");
            }
            // Validate that the pattern is a valid regex
            try {
                java.util.regex.Pattern.compile(pattern.asText());
            } catch (java.util.regex.PatternSyntaxException e) {
                throw new ValidationException("constraints.pattern", "Invalid regex pattern: " + e.getMessage());
            }
        }
    }

    /**
     * Validates number field constraints.
     */
    private void validateNumberConstraints(JsonNode constraints) {
        if (constraints.has("min")) {
            JsonNode min = constraints.get("min");
            if (!min.isNumber()) {
                throw new ValidationException("constraints.min", "Must be a number");
            }
        }
        if (constraints.has("max")) {
            JsonNode max = constraints.get("max");
            if (!max.isNumber()) {
                throw new ValidationException("constraints.max", "Must be a number");
            }
        }
        if (constraints.has("min") && constraints.has("max")) {
            double min = constraints.get("min").asDouble();
            double max = constraints.get("max").asDouble();
            if (min > max) {
                throw new ValidationException("constraints", "min cannot be greater than max");
            }
        }
    }

    /**
     * Validates reference field constraints.
     */
    private void validateReferenceConstraints(JsonNode constraints) {
        if (!constraints.has("collection")) {
            throw new ValidationException("constraints.collection", "Reference fields must specify a target collection");
        }
        JsonNode collection = constraints.get("collection");
        if (!collection.isTextual() || collection.asText().isBlank()) {
            throw new ValidationException("constraints.collection", "Must be a non-empty string");
        }
    }

    /**
     * Validates array field constraints.
     */
    private void validateArrayConstraints(JsonNode constraints) {
        if (constraints.has("minItems")) {
            JsonNode minItems = constraints.get("minItems");
            if (!minItems.isInt() || minItems.asInt() < 0) {
                throw new ValidationException("constraints.minItems", "Must be a non-negative integer");
            }
        }
        if (constraints.has("maxItems")) {
            JsonNode maxItems = constraints.get("maxItems");
            if (!maxItems.isInt() || maxItems.asInt() < 0) {
                throw new ValidationException("constraints.maxItems", "Must be a non-negative integer");
            }
        }
        if (constraints.has("minItems") && constraints.has("maxItems")) {
            int min = constraints.get("minItems").asInt();
            int max = constraints.get("maxItems").asInt();
            if (min > max) {
                throw new ValidationException("constraints", "minItems cannot be greater than maxItems");
            }
        }
        if (constraints.has("itemType")) {
            JsonNode itemType = constraints.get("itemType");
            if (!itemType.isTextual()) {
                throw new ValidationException("constraints.itemType", "Must be a string");
            }
            if (!VALID_FIELD_TYPES.contains(itemType.asText())) {
                throw new ValidationException("constraints.itemType", 
                        String.format("Invalid item type '%s'. Must be one of: %s", 
                                itemType.asText(), String.join(", ", VALID_FIELD_TYPES)));
            }
        }
    }

    /**
     * Creates a new version record for the collection.
     * Captures the current schema state as a JSON snapshot.
     * 
     * @param collection The collection to create a version for
     */
    private void createNewVersion(Collection collection) {
        String schemaJson = buildSchemaJson(collection);
        
        CollectionVersion version = new CollectionVersion(
                collection,
                collection.getCurrentVersion(),
                schemaJson
        );
        
        versionRepository.save(version);
        collection.addVersion(version);
        
        log.debug("Created version {} for collection {}", collection.getCurrentVersion(), collection.getId());
    }

    /**
     * Builds a JSON representation of the collection's current schema.
     * Includes collection metadata and all active fields.
     * 
     * @param collection The collection to serialize
     * @return JSON string representing the schema
     */
    private String buildSchemaJson(Collection collection) {
        try {
            Map<String, Object> schema = new HashMap<>();
            schema.put("name", collection.getName());
            schema.put("description", collection.getDescription());
            schema.put("version", collection.getCurrentVersion());
            
            // Include active fields in the schema
            List<Field> activeFields = fieldRepository.findByCollectionIdAndActiveTrue(collection.getId());
            List<Map<String, Object>> fieldSchemas = activeFields.stream()
                    .map(this::buildFieldSchema)
                    .collect(Collectors.toList());
            schema.put("fields", fieldSchemas);
            
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize collection schema", e);
            return "{}";
        }
    }

    /**
     * Builds a map representation of a field for schema serialization.
     * 
     * @param field The field to serialize
     * @return Map containing field properties
     */
    private Map<String, Object> buildFieldSchema(Field field) {
        Map<String, Object> fieldSchema = new HashMap<>();
        fieldSchema.put("id", field.getId());
        fieldSchema.put("name", field.getName());
        fieldSchema.put("type", field.getType());
        fieldSchema.put("required", field.isRequired());
        fieldSchema.put("description", field.getDescription());
        if (field.getConstraints() != null) {
            fieldSchema.put("constraints", field.getConstraints());
        }
        if (field.getRelationshipType() != null) {
            fieldSchema.put("relationshipType", field.getRelationshipType());
            fieldSchema.put("relationshipName", field.getRelationshipName());
            fieldSchema.put("cascadeDelete", field.isCascadeDelete());
            fieldSchema.put("referenceCollectionId", field.getReferenceCollectionId());
        }
        return fieldSchema;
    }

    /**
     * Configures relationship metadata for LOOKUP and MASTER_DETAIL fields.
     * Validates the target collection and enforces relationship rules:
     * <ul>
     *   <li>Target collection must exist and be active</li>
     *   <li>MASTER_DETAIL: no self-referencing, max 2 per collection, always required + cascade</li>
     *   <li>LOOKUP: nullable, no cascade delete</li>
     * </ul>
     */
    private void configureRelationshipField(Field field, String canonicalType,
                                            Collection collection, AddFieldRequest request) {
        // Parse fieldTypeConfig to get target collection
        String parsedTargetCollection = null;
        String parsedRelationshipName = null;
        if (request.getFieldTypeConfig() != null && !request.getFieldTypeConfig().isBlank()) {
            try {
                JsonNode config = objectMapper.readTree(request.getFieldTypeConfig());
                if (config.has("targetCollection")) {
                    parsedTargetCollection = config.get("targetCollection").asText();
                }
                if (config.has("relationshipName")) {
                    parsedRelationshipName = config.get("relationshipName").asText();
                }
            } catch (JsonProcessingException e) {
                throw new ValidationException("fieldTypeConfig", "Invalid JSON: " + e.getMessage());
            }
        }

        if (parsedTargetCollection == null || parsedTargetCollection.isBlank()) {
            throw new ValidationException("fieldTypeConfig",
                    canonicalType + " fields require fieldTypeConfig.targetCollection");
        }

        // Effectively final for lambda
        final String targetCollectionName = parsedTargetCollection;

        // Resolve target collection
        Collection targetCollection = collectionRepository.findByNameAndActiveTrue(targetCollectionName)
                .orElseThrow(() -> new ValidationException("fieldTypeConfig.targetCollection",
                        "Target collection '" + targetCollectionName + "' not found"));

        // Default relationship name to target collection name if not specified
        String relationshipName = parsedRelationshipName;
        if (relationshipName == null || relationshipName.isBlank()) {
            relationshipName = targetCollection.getDisplayName() != null
                    ? targetCollection.getDisplayName() : targetCollectionName;
        }

        if ("MASTER_DETAIL".equals(canonicalType)) {
            // No self-referencing master-detail
            if (targetCollection.getId().equals(collection.getId())) {
                throw new ValidationException("fieldTypeConfig.targetCollection",
                        "MASTER_DETAIL cannot reference its own collection");
            }

            // Max 2 master-detail fields per collection
            long masterDetailCount = fieldRepository.countMasterDetailFieldsByCollectionId(collection.getId());
            if (masterDetailCount >= 2) {
                throw new ValidationException("type",
                        "Collection already has the maximum of 2 MASTER_DETAIL relationships");
            }

            // Force required and cascade delete
            field.setRequired(true);
            field.setCascadeDelete(true);
        } else {
            // LOOKUP: nullable, no cascade
            field.setCascadeDelete(false);
        }

        field.setRelationshipType(canonicalType);
        field.setRelationshipName(relationshipName);
        field.setReferenceCollectionId(targetCollection.getId());
        field.setReferenceTarget(targetCollectionName);
    }

    /**
     * Publishes a collection changed event to Kafka.
     * Only publishes if the event publisher is available (Kafka is enabled).
     *
     * @param collection The collection that changed
     *
     * Validates: Requirement 2.6
     */
    private void publishCollectionChangedEvent(Collection collection) {
        if (eventPublisher != null) {
            eventPublisher.publishCollectionChanged(collection, ChangeType.UPDATED);
        } else {
            log.debug("Event publishing disabled - collection changed: {}", collection.getId());
        }
    }
}
