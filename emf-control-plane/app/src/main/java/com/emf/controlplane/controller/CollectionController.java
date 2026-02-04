package com.emf.controlplane.controller;

import com.emf.controlplane.config.CacheConfig;
import com.emf.controlplane.dto.AddFieldRequest;
import com.emf.controlplane.dto.AuthorizationConfigDto;
import com.emf.controlplane.dto.CollectionDto;
import com.emf.controlplane.dto.CollectionVersionDto;
import com.emf.controlplane.dto.CreateCollectionRequest;
import com.emf.controlplane.dto.FieldDto;
import com.emf.controlplane.dto.UpdateCollectionRequest;
import com.emf.controlplane.dto.UpdateFieldRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.CollectionVersion;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.service.AuthorizationService;
import com.emf.controlplane.service.CollectionService;
import com.emf.controlplane.service.FieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing collection definitions.
 * 
 * <p>Provides CRUD operations for collections with pagination, filtering, and sorting support.
 * Write operations (POST, PUT, DELETE) require ADMIN role authorization.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>1.1: Return paginated list with filtering and sorting support</li>
 *   <li>1.2: Persist new collection and return with generated ID</li>
 *   <li>1.4: Return collection by ID if it exists</li>
 *   <li>1.6: Create new CollectionVersion on update and preserve previous version</li>
 *   <li>1.7: Soft-delete by marking as inactive</li>
 *   <li>2.1: Return list of active fields for a collection</li>
 *   <li>2.2: Add field creates new CollectionVersion with the added field</li>
 *   <li>2.4: Update field creates new CollectionVersion with the updated field</li>
 *   <li>2.5: Delete field creates new CollectionVersion marking the field as inactive</li>
 * </ul>
 */
@RestController
@RequestMapping("/control/collections")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Collections", description = "Collection management APIs")
public class CollectionController {

    private static final Logger log = LoggerFactory.getLogger(CollectionController.class);

    private final CollectionService collectionService;
    private final FieldService fieldService;
    private final AuthorizationService authorizationService;

    public CollectionController(CollectionService collectionService, FieldService fieldService, AuthorizationService authorizationService) {
        this.collectionService = collectionService;
        this.fieldService = fieldService;
        this.authorizationService = authorizationService;
    }

    /**
     * Lists all active collections with pagination, filtering, and sorting support.
     * 
     * @param filter Optional filter string to search by name or description
     * @param sort Optional sort criteria (handled by Pageable)
     * @param pageable Pagination and sorting parameters
     * @return Page of collection DTOs
     * 
     * Validates: Requirement 1.1
     */
    @GetMapping
    @Operation(
            summary = "List collections",
            description = "Returns a paginated list of active collections with optional filtering and sorting"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved collections"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<CollectionDto>> listCollections(
            @Parameter(description = "Filter string to search by name or description")
            @RequestParam(required = false) String filter,
            @Parameter(description = "Sort criteria (e.g., 'name,asc' or 'createdAt,desc')")
            @RequestParam(required = false) String sort,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        
        log.debug("REST request to list collections - filter: {}, sort: {}, pageable: {}", filter, sort, pageable);
        
        Page<Collection> collections = collectionService.listCollections(filter, sort, pageable);
        Page<CollectionDto> dtos = collections.map(CollectionDto::fromEntity);
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Creates a new collection.
     * Requires ADMIN role authorization.
     * 
     * @param request The collection creation request with name and description
     * @return The created collection with generated ID
     * 
     * Validates: Requirement 1.2
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create collection",
            description = "Creates a new collection with the provided name and description. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Collection created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "409", description = "Conflict - collection with same name already exists")
    })
    public ResponseEntity<CollectionDto> createCollection(
            @Valid @RequestBody CreateCollectionRequest request) {
        
        log.info("REST request to create collection: {}", request.getName());
        
        Collection created = collectionService.createCollection(request);
        CollectionDto dto = CollectionDto.fromEntity(created);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Retrieves a collection by its ID.
     * 
     * @param id The collection ID
     * @return The collection if found
     * 
     * Validates: Requirements 1.4, 1.5
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get collection",
            description = "Retrieves a collection by its ID"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Collection found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Collection not found")
    })
    public ResponseEntity<CollectionDto> getCollection(
            @Parameter(description = "Collection ID", required = true)
            @PathVariable String id) {
        
        log.debug("REST request to get collection: {}", id);
        
        CollectionDto dto = getCollectionDto(id);
        
        return ResponseEntity.ok(dto);
    }
    
    /**
     * Internal method to get and cache CollectionDto.
     * Separated from the controller method to avoid caching ResponseEntity.
     */
    @Cacheable(value = CacheConfig.COLLECTIONS_CACHE, key = "#id", unless = "#result == null")
    private CollectionDto getCollectionDto(String id) {
        Collection collection = collectionService.getCollection(id);
        
        // Fetch fields for this collection
        List<Field> fields = fieldService.listFields(id);
        List<FieldDto> fieldDtos = fields.stream()
                .map(FieldDto::fromEntity)
                .collect(Collectors.toList());
        
        // Fetch authorization config
        AuthorizationConfigDto authz = authorizationService.getCollectionAuthorization(id);
        
        return CollectionDto.fromEntityWithDetails(collection, fieldDtos, authz);
    }

    /**
     * Updates an existing collection.
     * Creates a new version with incremented version number.
     * Requires ADMIN role authorization.
     * 
     * @param id The collection ID to update
     * @param request The update request with new values
     * @return The updated collection
     * 
     * Validates: Requirement 1.6
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update collection",
            description = "Updates an existing collection. Creates a new version. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Collection updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Collection not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - collection with same name already exists")
    })
    public ResponseEntity<CollectionDto> updateCollection(
            @Parameter(description = "Collection ID", required = true)
            @PathVariable String id,
            @Valid @RequestBody UpdateCollectionRequest request) {
        
        log.info("REST request to update collection: {}", id);
        
        Collection updated = collectionService.updateCollection(id, request);
        CollectionDto dto = CollectionDto.fromEntity(updated);
        
        return ResponseEntity.ok(dto);
    }

    /**
     * Soft-deletes a collection by marking it as inactive.
     * The collection and its versions are preserved in the database.
     * Requires ADMIN role authorization.
     * 
     * @param id The collection ID to delete
     * 
     * Validates: Requirement 1.7
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete collection",
            description = "Soft-deletes a collection by marking it as inactive. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Collection deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Collection not found")
    })
    public ResponseEntity<Void> deleteCollection(
            @Parameter(description = "Collection ID", required = true)
            @PathVariable String id) {
        
        log.info("REST request to delete collection: {}", id);
        
        collectionService.deleteCollection(id);
        
        return ResponseEntity.noContent().build();
    }

    // ==================== Version Management Endpoints ====================

    /**
     * Lists all versions for a collection.
     * Returns versions in descending order (newest first).
     * 
     * @param id The collection ID
     * @return List of collection version DTOs
     */
    @GetMapping("/{id}/versions")
    @Operation(
            summary = "List collection versions",
            description = "Returns a list of all versions for the specified collection, ordered by version number descending"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved versions"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Collection not found")
    })
    public ResponseEntity<List<CollectionVersionDto>> listVersions(
            @Parameter(description = "Collection ID", required = true)
            @PathVariable String id) {
        
        log.debug("REST request to list versions for collection: {}", id);
        
        List<CollectionVersion> versions = collectionService.getCollectionVersions(id);
        List<CollectionVersionDto> dtos = versions.stream()
                .map(CollectionVersionDto::fromEntity)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Retrieves a specific version of a collection.
     * 
     * @param id The collection ID
     * @param version The version number
     * @return The collection version DTO
     */
    @GetMapping("/{id}/versions/{version}")
    @Operation(
            summary = "Get collection version",
            description = "Retrieves a specific version of a collection by version number"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Version found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Collection or version not found")
    })
    public ResponseEntity<CollectionVersionDto> getVersion(
            @Parameter(description = "Collection ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Version number", required = true)
            @PathVariable Integer version) {
        
        log.debug("REST request to get version {} for collection: {}", version, id);
        
        CollectionVersion collectionVersion = collectionService.getCollectionVersion(id, version);
        CollectionVersionDto dto = CollectionVersionDto.fromEntity(collectionVersion);
        
        return ResponseEntity.ok(dto);
    }

    // ==================== Field Management Endpoints ====================

    /**
     * Lists all active fields for a collection.
     * 
     * @param id The collection ID
     * @return List of active field DTOs
     * 
     * Validates: Requirement 2.1
     */
    @GetMapping("/{id}/fields")
    @Operation(
            summary = "List fields",
            description = "Returns a list of all active fields for the specified collection"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved fields"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Collection not found")
    })
    public ResponseEntity<List<FieldDto>> listFields(
            @Parameter(description = "Collection ID", required = true)
            @PathVariable String id) {
        
        log.debug("REST request to list fields for collection: {}", id);
        
        List<Field> fields = fieldService.listFields(id);
        List<FieldDto> dtos = fields.stream()
                .map(FieldDto::fromEntity)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Adds a new field to a collection.
     * Creates a new CollectionVersion with the added field.
     * Requires ADMIN role authorization.
     * 
     * @param id The collection ID to add the field to
     * @param request The field creation request
     * @return The created field DTO
     * 
     * Validates: Requirement 2.2
     */
    @PostMapping("/{id}/fields")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Add field",
            description = "Adds a new field to the collection. Creates a new version. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Field created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Collection not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - field with same name already exists")
    })
    public ResponseEntity<FieldDto> addField(
            @Parameter(description = "Collection ID", required = true)
            @PathVariable String id,
            @Valid @RequestBody AddFieldRequest request) {
        
        log.info("REST request to add field '{}' to collection: {}", request.getName(), id);
        
        Field created = fieldService.addField(id, request);
        FieldDto dto = FieldDto.fromEntity(created);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Updates an existing field in a collection.
     * Creates a new CollectionVersion with the updated field.
     * Requires ADMIN role authorization.
     * 
     * @param id The collection ID
     * @param fieldId The field ID to update
     * @param request The field update request
     * @return The updated field DTO
     * 
     * Validates: Requirement 2.4
     */
    @PutMapping("/{id}/fields/{fieldId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update field",
            description = "Updates an existing field in the collection. Creates a new version. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Field updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Collection or field not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - field with same name already exists")
    })
    public ResponseEntity<FieldDto> updateField(
            @Parameter(description = "Collection ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Field ID", required = true)
            @PathVariable String fieldId,
            @Valid @RequestBody UpdateFieldRequest request) {
        
        log.info("REST request to update field '{}' in collection: {}", fieldId, id);
        
        Field updated = fieldService.updateField(id, fieldId, request);
        FieldDto dto = FieldDto.fromEntity(updated);
        
        return ResponseEntity.ok(dto);
    }

    /**
     * Soft-deletes a field by marking it as inactive.
     * Creates a new CollectionVersion with the field marked as inactive.
     * Requires ADMIN role authorization.
     * 
     * @param id The collection ID
     * @param fieldId The field ID to delete
     * 
     * Validates: Requirement 2.5
     */
    @DeleteMapping("/{id}/fields/{fieldId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete field",
            description = "Soft-deletes a field by marking it as inactive. Creates a new version. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Field deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Collection or field not found")
    })
    public ResponseEntity<Void> deleteField(
            @Parameter(description = "Collection ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Field ID", required = true)
            @PathVariable String fieldId) {
        
        log.info("REST request to delete field '{}' from collection: {}", fieldId, id);
        
        fieldService.deleteField(id, fieldId);
        
        return ResponseEntity.noContent().build();
    }
}
