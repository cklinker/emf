package com.emf.controlplane.service;

import com.emf.controlplane.config.CacheConfig;
import com.emf.controlplane.dto.CollectionDto;
import com.emf.controlplane.dto.CreateCollectionRequest;
import com.emf.controlplane.dto.FieldDto;
import com.emf.controlplane.dto.UpdateCollectionRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.CollectionVersion;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.runtime.event.ChangeType;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.CollectionVersionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing collection definitions.
 * Handles CRUD operations with versioning, event publishing, and caching.
 * 
 * Requirements satisfied:
 * - 1.1: Return paginated list with filtering and sorting support
 * - 1.2: Persist new collection and return with generated ID
 * - 1.4: Return collection by ID if it exists
 * - 1.6: Create new CollectionVersion on update and preserve previous version
 * - 1.7: Soft-delete by marking as inactive
 */
@Service
public class CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionService.class);

    private final CollectionRepository collectionRepository;
    private final CollectionVersionRepository versionRepository;
    private final FieldRepository fieldRepository;
    private final ObjectMapper objectMapper;
    private final ConfigEventPublisher eventPublisher;
    private final DefaultProfileSeeder defaultProfileSeeder;

    public CollectionService(
            CollectionRepository collectionRepository,
            CollectionVersionRepository versionRepository,
            FieldRepository fieldRepository,
            ObjectMapper objectMapper,
            @Nullable ConfigEventPublisher eventPublisher,
            @Nullable DefaultProfileSeeder defaultProfileSeeder) {
        this.collectionRepository = collectionRepository;
        this.versionRepository = versionRepository;
        this.fieldRepository = fieldRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.defaultProfileSeeder = defaultProfileSeeder;
    }

    /**
     * Lists collections with pagination, filtering, and sorting support.
     * Only returns active collections. System collections are excluded by default.
     *
     * @param filter Optional filter string to search by name or description
     * @param sort Optional sort criteria (handled by Pageable)
     * @param pageable Pagination and sorting parameters
     * @param includeSystem Whether to include system collections (default: false)
     * @return Page of active collections matching the criteria
     *
     * Validates: Requirement 1.1
     */
    @Transactional(readOnly = true)
    public Page<Collection> listCollections(String filter, String sort, Pageable pageable, boolean includeSystem) {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Listing collections for tenant: {} with filter: {}, includeSystem: {}", tenantId, filter, includeSystem);

        if (tenantId != null) {
            if (filter != null && !filter.isBlank()) {
                return includeSystem
                        ? collectionRepository.findByTenantIdAndActiveAndSearchTerm(tenantId, filter.trim(), pageable)
                        : collectionRepository.findByTenantIdAndActiveAndSearchTermExcludeSystem(tenantId, filter.trim(), pageable);
            }
            return includeSystem
                    ? collectionRepository.findByTenantIdAndActiveTrue(tenantId, pageable)
                    : collectionRepository.findByTenantIdAndActiveTrueExcludeSystem(tenantId, pageable);
        }

        if (filter != null && !filter.isBlank()) {
            return includeSystem
                    ? collectionRepository.findByActiveAndSearchTerm(filter.trim(), pageable)
                    : collectionRepository.findByActiveAndSearchTermExcludeSystem(filter.trim(), pageable);
        }
        return includeSystem
                ? collectionRepository.findByActiveTrue(pageable)
                : collectionRepository.findByActiveTrueExcludeSystem(pageable);
    }

    /**
     * Creates a new collection with the given request data.
     * Generates a unique ID and creates an initial version (version 1).
     * 
     * @param request The collection creation request
     * @return The created collection with generated ID
     * @throws DuplicateResourceException if a collection with the same name already exists
     * @throws ResourceNotFoundException if the service does not exist
     * 
     * Validates: Requirements 1.2, 15.1
     */
    @Transactional
    public Collection createCollection(CreateCollectionRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Creating collection with name: {} tenant: {}", request.getName(), tenantId);

        // Check for duplicate name within the tenant
        if (tenantId != null) {
            if (collectionRepository.existsByTenantIdAndNameAndActiveTrue(tenantId, request.getName())) {
                throw new DuplicateResourceException("Collection", "name", request.getName());
            }
        } else {
            if (collectionRepository.existsByNameAndActiveTrue(request.getName())) {
                throw new DuplicateResourceException("Collection", "name", request.getName());
            }
        }

        // Create the collection entity
        Collection collection = new Collection(request.getName(), request.getDescription());
        collection.setCurrentVersion(1);
        collection.setActive(true);
        if (tenantId != null) {
            collection.setTenantId(tenantId);
        }

        // Set path if provided, otherwise generate default path
        if (request.getPath() != null && !request.getPath().isEmpty()) {
            collection.setPath(request.getPath());
        } else {
            collection.setPath("/api/" + request.getName());
        }

        // Save the collection first to get the ID
        collection = collectionRepository.save(collection);

        // Create initial version (version 1)
        createNewVersion(collection);

        // Publish event
        publishCollectionChangedEvent(collection, ChangeType.CREATED);

        // Auto-create object permissions for all profiles in the tenant
        if (defaultProfileSeeder != null && tenantId != null) {
            try {
                defaultProfileSeeder.seedObjectPermissionsForCollection(tenantId, collection.getId());
            } catch (Exception e) {
                log.warn("Failed to seed object permissions for collection {}: {}",
                        collection.getId(), e.getMessage());
            }
        }

        log.info("Created collection with id: {}", collection.getId());
        return collection;
    }

    /**
     * Retrieves a collection by its ID.
     * Only returns active collections.
     * Results are cached in Redis with the configured TTL.
     * Falls back to direct database query if cache is unavailable.
     * 
     * @param id The collection ID
     * @return The collection if found
     * @throws ResourceNotFoundException if the collection does not exist or is inactive
     * 
     * Validates: Requirements 1.4, 1.5, 14.1
     */
    @Transactional(readOnly = true)
    public Collection getCollection(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Fetching collection: {} for tenant: {}", id, tenantId);

        if (tenantId != null) {
            return collectionRepository.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
        }
        return collectionRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
    }

    /**
     * Retrieves a collection by its ID or name.
     * First attempts lookup by ID, then falls back to lookup by name.
     *
     * @param idOrName The collection ID (UUID) or name
     * @return The collection entity
     * @throws ResourceNotFoundException if collection not found by either ID or name
     */
    @Transactional(readOnly = true)
    public Collection getCollectionByIdOrName(String idOrName) {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Fetching collection by ID or name: {} for tenant: {}", idOrName, tenantId);

        if (tenantId != null) {
            // Try by ID first, then by name — both scoped to tenant
            java.util.Optional<Collection> byId = collectionRepository.findByIdAndTenantIdAndActiveTrue(idOrName, tenantId);
            if (byId.isPresent()) {
                return byId.get();
            }
            log.debug("Collection not found by ID '{}', trying name lookup", idOrName);
            return collectionRepository.findByTenantIdAndNameAndActiveTrue(tenantId, idOrName)
                    .orElseThrow(() -> new ResourceNotFoundException("Collection", idOrName));
        }

        // No tenant context — try by ID, then by name globally
        java.util.Optional<Collection> byId = collectionRepository.findByIdAndActiveTrue(idOrName);
        if (byId.isPresent()) {
            return byId.get();
        }
        log.debug("Collection not found by ID '{}', trying name lookup", idOrName);
        return collectionRepository.findByNameAndActiveTrue(idOrName)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", idOrName));
    }

    /**
     * Gets a cached CollectionDto by ID or name.
     * Resolves the collection, fetches its fields, and builds a full DTO.
     * Results are cached in Redis with the configured TTL.
     *
     * @param id The collection ID (UUID) or name
     * @return The CollectionDto with fields populated
     * @throws ResourceNotFoundException if the collection does not exist
     */
    @Cacheable(value = CacheConfig.COLLECTIONS_CACHE, key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    public CollectionDto getCollectionDto(String id) {
        Collection collection = getCollectionByIdOrName(id);
        String collectionId = collection.getId();

        List<Field> fields = fieldRepository.findByCollectionIdAndActiveTrue(collectionId);
        List<FieldDto> fieldDtos = fields.stream()
                .map(FieldDto::fromEntity)
                .collect(Collectors.toList());

        return CollectionDto.fromEntityWithDetails(collection, fieldDtos);
    }

    /**
     * Updates an existing collection.
     * Creates a new version with incremented version number.
     * Evicts the collection from cache after update.
     * 
     * @param id The collection ID to update
     * @param request The update request with new values
     * @return The updated collection
     * @throws ResourceNotFoundException if the collection does not exist or is inactive
     * @throws DuplicateResourceException if updating name to one that already exists
     * 
     * Validates: Requirements 1.6, 14.3, 15.2
     */
    @Transactional
    @CacheEvict(value = CacheConfig.COLLECTIONS_CACHE, allEntries = true)
    public Collection updateCollection(String id, UpdateCollectionRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Updating collection with id: {} for tenant: {}", id, tenantId);

        Collection collection;
        if (tenantId != null) {
            collection = collectionRepository.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
        } else {
            collection = collectionRepository.findByIdAndActiveTrue(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
        }

        // Check for duplicate name if name is being changed
        if (request.getName() != null && !request.getName().equals(collection.getName())) {
            boolean nameExists = tenantId != null
                    ? collectionRepository.existsByTenantIdAndNameAndActiveTrue(tenantId, request.getName())
                    : collectionRepository.existsByNameAndActiveTrue(request.getName());
            if (nameExists) {
                throw new DuplicateResourceException("Collection", "name", request.getName());
            }
            collection.setName(request.getName());
        }
        
        // Update description if provided
        if (request.getDescription() != null) {
            collection.setDescription(request.getDescription());
        }

        // Update display field if provided
        if (request.getDisplayFieldId() != null) {
            if (request.getDisplayFieldId().isEmpty()) {
                // Empty string means clear the display field
                collection.setDisplayFieldId(null);
            } else {
                // Validate the field belongs to this collection and is active
                Field displayField = fieldRepository.findByIdAndCollectionIdAndActiveTrue(
                        request.getDisplayFieldId(), collection.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Field", request.getDisplayFieldId()));
                collection.setDisplayFieldId(displayField.getId());
            }
        }

        // Increment version and create new version record
        collection.setCurrentVersion(collection.getCurrentVersion() + 1);
        createNewVersion(collection);
        
        // Save the updated collection
        collection = collectionRepository.save(collection);
        
        // Publish event (cache is already evicted by annotation)
        publishCollectionChangedEvent(collection, ChangeType.UPDATED);
        
        log.info("Updated collection with id: {}, new version: {}", id, collection.getCurrentVersion());
        return collection;
    }

    /**
     * Soft-deletes a collection by marking it as inactive.
     * The collection and its versions are preserved in the database.
     * Evicts the collection from cache after deletion.
     * 
     * @param id The collection ID to delete
     * @throws ResourceNotFoundException if the collection does not exist or is already inactive
     * 
     * Validates: Requirements 1.7, 14.3
     */
    @Transactional
    @CacheEvict(value = CacheConfig.COLLECTIONS_CACHE, allEntries = true)
    public void deleteCollection(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Deleting collection with id: {} for tenant: {}", id, tenantId);

        Collection collection;
        if (tenantId != null) {
            collection = collectionRepository.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
        } else {
            collection = collectionRepository.findByIdAndActiveTrue(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
        }
        
        // Soft delete - mark as inactive
        collection.setActive(false);
        
        // Also mark all fields as inactive
        List<Field> fields = fieldRepository.findByCollectionIdAndActiveTrue(id);
        for (Field field : fields) {
            field.setActive(false);
        }
        fieldRepository.saveAll(fields);
        
        collectionRepository.save(collection);
        
        // Publish event (cache is already evicted by annotation)
        publishCollectionChangedEvent(collection, ChangeType.DELETED);
        
        log.info("Soft-deleted collection with id: {}", id);
    }

    /**
     * Creates a new version record for the collection.
     * Captures the current schema state as a JSON snapshot.
     * The version is persisted via cascade when the collection is saved.
     * 
     * @param collection The collection to create a version for
     * 
     * Validates: Requirements 15.1, 15.2, 15.4
     */
    private void createNewVersion(Collection collection) {
        String schemaJson = buildSchemaJson(collection);
        
        CollectionVersion version = new CollectionVersion(
                collection,
                collection.getCurrentVersion(),
                schemaJson
        );
        
        // Add to collection's versions list - will be persisted via cascade
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
        return fieldSchema;
    }

    /**
     * Publishes a collection changed event to Kafka.
     * Only publishes if the event publisher is available (Kafka is enabled).
     * 
     * @param collection The collection that changed
     * @param changeType The type of change (CREATED, UPDATED, DELETED)
     * 
     * Validates: Requirement 1.8
     */
    private void publishCollectionChangedEvent(Collection collection, ChangeType changeType) {
        if (eventPublisher != null) {
            eventPublisher.publishCollectionChanged(collection, changeType);
        } else {
            log.debug("Event publishing disabled - collection changed: {}", collection.getId());
        }
    }

    /**
     * Evicts a collection from the cache.
     * This method is used for programmatic cache eviction when field changes occur.
     * 
     * @param collectionId The collection ID to evict from cache
     * 
     * Validates: Requirement 14.3
     */
    @CacheEvict(value = CacheConfig.COLLECTIONS_CACHE, allEntries = true)
    public void evictFromCache(String collectionId) {
        log.debug("Evicting collection from cache: {}", collectionId);
    }

    /**
     * Retrieves all versions for a collection.
     * Used for historical tracking and rollback support.
     * 
     * @param collectionId The collection ID
     * @return List of all versions ordered by version number descending
     * @throws ResourceNotFoundException if the collection does not exist
     * 
     * Validates: Requirement 15.5
     */
    @Transactional(readOnly = true)
    public List<CollectionVersion> getCollectionVersions(String collectionId) {
        // Verify collection exists
        if (!collectionRepository.existsById(collectionId)) {
            throw new ResourceNotFoundException("Collection", collectionId);
        }
        
        return versionRepository.findByCollectionIdOrderByVersionDesc(collectionId);
    }

    /**
     * Retrieves a specific version of a collection.
     * 
     * @param collectionId The collection ID
     * @param version The version number
     * @return The collection version if found
     * @throws ResourceNotFoundException if the collection or version does not exist
     * 
     * Validates: Requirement 15.5
     */
    @Transactional(readOnly = true)
    public CollectionVersion getCollectionVersion(String collectionId, Integer version) {
        // Verify collection exists
        if (!collectionRepository.existsById(collectionId)) {
            throw new ResourceNotFoundException("Collection", collectionId);
        }
        
        return versionRepository.findByCollectionIdAndVersion(collectionId, version)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CollectionVersion", collectionId + "/v" + version));
    }
}
