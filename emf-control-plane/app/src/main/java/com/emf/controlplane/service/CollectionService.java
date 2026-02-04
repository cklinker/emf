package com.emf.controlplane.service;

import com.emf.controlplane.config.CacheConfig;
import com.emf.controlplane.dto.CreateCollectionRequest;
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
import com.emf.controlplane.repository.ServiceRepository;
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
    private final ServiceRepository serviceRepository;
    private final ObjectMapper objectMapper;
    private final ConfigEventPublisher eventPublisher;

    public CollectionService(
            CollectionRepository collectionRepository,
            CollectionVersionRepository versionRepository,
            FieldRepository fieldRepository,
            ServiceRepository serviceRepository,
            ObjectMapper objectMapper,
            @Nullable ConfigEventPublisher eventPublisher) {
        this.collectionRepository = collectionRepository;
        this.versionRepository = versionRepository;
        this.fieldRepository = fieldRepository;
        this.serviceRepository = serviceRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Lists collections with pagination, filtering, and sorting support.
     * Only returns active collections.
     * 
     * @param filter Optional filter string to search by name or description
     * @param sort Optional sort criteria (handled by Pageable)
     * @param pageable Pagination and sorting parameters
     * @return Page of active collections matching the criteria
     * 
     * Validates: Requirement 1.1
     */
    @Transactional(readOnly = true)
    public Page<Collection> listCollections(String filter, String sort, Pageable pageable) {
        log.debug("Listing collections with filter: {}, sort: {}, pageable: {}", filter, sort, pageable);
        
        if (filter != null && !filter.isBlank()) {
            return collectionRepository.findByActiveAndSearchTerm(filter.trim(), pageable);
        }
        
        return collectionRepository.findByActiveTrue(pageable);
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
        log.info("Creating collection with name: {} for service: {}", request.getName(), request.getServiceId());
        
        // Verify service exists
        com.emf.controlplane.entity.Service service = serviceRepository.findByIdAndActiveTrue(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service", request.getServiceId()));
        
        // Check for duplicate name within the service
        if (collectionRepository.existsByNameAndActiveTrue(request.getName())) {
            throw new DuplicateResourceException("Collection", "name", request.getName());
        }
        
        // Create the collection entity
        Collection collection = new Collection(service, request.getName(), request.getDescription());
        collection.setCurrentVersion(1);
        collection.setActive(true);
        
        // Set path if provided, otherwise generate default path
        if (request.getPath() != null && !request.getPath().isEmpty()) {
            collection.setPath(request.getPath());
        } else {
            // Generate default path: /api/{collectionName}
            String basePath = service.getBasePath();
            if (basePath == null || basePath.isEmpty()) {
                basePath = "/api";
            }
            if (!basePath.startsWith("/")) {
                basePath = "/" + basePath;
            }
            if (basePath.endsWith("/")) {
                basePath = basePath.substring(0, basePath.length() - 1);
            }
            collection.setPath(basePath + "/" + request.getName());
        }
        
        // Save the collection first to get the ID
        collection = collectionRepository.save(collection);
        
        // Create initial version (version 1)
        createNewVersion(collection);
        
        // Publish event
        publishCollectionChangedEvent(collection, ChangeType.CREATED);
        
        log.info("Created collection with id: {} for service: {}", collection.getId(), service.getName());
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
        log.debug("Fetching collection from database: {}", id);
        
        return collectionRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
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
    @CacheEvict(value = CacheConfig.COLLECTIONS_CACHE, key = "#id")
    public Collection updateCollection(String id, UpdateCollectionRequest request) {
        log.info("Updating collection with id: {}", id);
        
        Collection collection = collectionRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
        
        // Check for duplicate name if name is being changed
        if (request.getName() != null && !request.getName().equals(collection.getName())) {
            if (collectionRepository.existsByNameAndActiveTrue(request.getName())) {
                throw new DuplicateResourceException("Collection", "name", request.getName());
            }
            collection.setName(request.getName());
        }
        
        // Update description if provided
        if (request.getDescription() != null) {
            collection.setDescription(request.getDescription());
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
    @CacheEvict(value = CacheConfig.COLLECTIONS_CACHE, key = "#id")
    public void deleteCollection(String id) {
        log.info("Deleting collection with id: {}", id);
        
        Collection collection = collectionRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
        
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
    @CacheEvict(value = CacheConfig.COLLECTIONS_CACHE, key = "#collectionId")
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
