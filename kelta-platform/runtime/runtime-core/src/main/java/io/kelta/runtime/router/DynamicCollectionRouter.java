package io.kelta.runtime.router;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.system.SystemCollectionDefinitions;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.FilterOperator;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.query.ReadOnlyCollectionException;
import io.kelta.runtime.registry.CollectionOnDemandLoader;
import io.kelta.runtime.registry.CollectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dynamic REST controller for collection CRUD operations.
 * 
 * <p>This controller provides a unified API for all collections registered in the
 * {@link CollectionRegistry}. It dynamically routes requests based on the collection
 * name in the URL path.
 * 
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/{collectionName} - List records with pagination, sorting, filtering</li>
 *   <li>GET /api/{collectionName}/{id} - Get a single record by ID</li>
 *   <li>POST /api/{collectionName} - Create a new record</li>
 *   <li>PUT /api/{collectionName}/{id} - Update an existing record</li>
 *   <li>DELETE /api/{collectionName}/{id} - Delete a record</li>
 * </ul>
 *
 * <p>Sub-resource endpoints (for parent-child relationships):
 * <ul>
 *   <li>GET /api/{parent}/{parentId}/{child} - List child records for a parent</li>
 *   <li>GET /api/{parent}/{parentId}/{child}/{childId} - Get a child record</li>
 *   <li>POST /api/{parent}/{parentId}/{child} - Create a child record</li>
 *   <li>PUT /api/{parent}/{parentId}/{child}/{childId} - Update a child record</li>
 *   <li>DELETE /api/{parent}/{parentId}/{child}/{childId} - Delete a child record</li>
 * </ul>
 * 
 * <p>Query Parameters for list endpoint:
 * <ul>
 *   <li>page[number] - Page number (default: 1)</li>
 *   <li>page[size] - Page size (default: 20)</li>
 *   <li>sort - Comma-separated sort fields (prefix with - for descending)</li>
 *   <li>fields - Comma-separated field names to return</li>
 *   <li>filter[field][op] - Filter conditions</li>
 * </ul>
 * 
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api")
public class DynamicCollectionRouter {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicCollectionRouter.class);

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    
    private final CollectionRegistry registry;
    private final QueryEngine queryEngine;

    /**
     * Optional on-demand loader that fetches unknown collections from the
     * control plane. When present, the router will try to load a collection
     * before returning 404.
     */
    private CollectionOnDemandLoader onDemandLoader;

    /**
     * Optional resolver that translates user identifiers (e.g., email) to
     * platform_user UUIDs for audit fields (created_by, updated_by).
     */
    private UserIdResolver userIdResolver;

    /**
     * Optional cache for system collection query results. When present, list
     * and get-by-id operations on system collections check the cache first and
     * store results on cache miss. Write operations evict the cache for the
     * affected collection.
     */
    private SystemCollectionCache systemCollectionCache;

    /**
     * Creates a new DynamicCollectionRouter.
     *
     * @param registry the collection registry
     * @param queryEngine the query engine
     */
    public DynamicCollectionRouter(CollectionRegistry registry, QueryEngine queryEngine) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.queryEngine = Objects.requireNonNull(queryEngine, "queryEngine cannot be null");
    }

    @Autowired(required = false)
    public void setOnDemandLoader(CollectionOnDemandLoader onDemandLoader) {
        this.onDemandLoader = onDemandLoader;
    }

    @Autowired(required = false)
    public void setUserIdResolver(UserIdResolver userIdResolver) {
        this.userIdResolver = userIdResolver;
    }

    @Autowired(required = false)
    public void setSystemCollectionCache(SystemCollectionCache systemCollectionCache) {
        this.systemCollectionCache = systemCollectionCache;
    }

    /**
     * Lists records from a collection with pagination, sorting, and filtering.
     *
     * @param collectionName the collection name
     * @param params query parameters for pagination, sorting, filtering, and field selection
     * @param request the HTTP servlet request
     * @return the query result or 404 if collection not found
     */
    @GetMapping("/{collectionName}")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable("collectionName") String collectionName,
            @RequestParam(required = false) Map<String, String> params,
            HttpServletRequest request) {

        logger.debug("List request for collection '{}' with params: {}", collectionName, params);

        setTenantContext(request);
        try {
            CollectionDefinition definition = resolveCollection(collectionName, request);
            if (definition == null) {
                logger.debug("Collection '{}' not found", collectionName);
                return ResponseEntity.notFound().build();
            }

            QueryRequest queryRequest = QueryRequest.fromParams(params);

            // For tenant-scoped system collections, inject tenant_id filter
            queryRequest = injectTenantFilter(queryRequest, definition, request);

            // Check system collection cache before querying
            String tenantId = request.getHeader("X-Tenant-ID");
            List<String> includeNames = parseIncludeParam(params);
            if (definition.systemCollection() && systemCollectionCache != null && includeNames.isEmpty()) {
                String queryHash = buildQueryHash(queryRequest);
                Optional<Map<String, Object>> cached = systemCollectionCache.getListResponse(
                        tenantId, collectionName, queryHash);
                if (cached.isPresent()) {
                    logger.debug("Cache hit for system collection list: {}", collectionName);
                    return ResponseEntity.ok(cached.get());
                }
            }

            QueryResult result = queryEngine.executeQuery(definition, queryRequest);

            Map<String, Object> response = toJsonApiListResponse(result, collectionName, definition);

            // Resolve JSON:API ?include= parameter for inverse (has-many) relationships
            if (!includeNames.isEmpty() && !result.data().isEmpty()) {
                List<Map<String, Object>> included = resolveIncludes(
                        includeNames, result.data(), collectionName, definition, request);
                if (!included.isEmpty()) {
                    response.put("included", included);
                }
            }

            // Cache the response for system collections (only when no includes, to keep cache simple)
            if (definition.systemCollection() && systemCollectionCache != null && includeNames.isEmpty()) {
                String queryHash = buildQueryHash(queryRequest);
                systemCollectionCache.putListResponse(tenantId, collectionName, queryHash, response);
            }

            return ResponseEntity.ok(response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Gets a single record by ID.
     * 
     * @param collectionName the collection name
     * @param id the record ID
     * @return the record in JSON:API format or 404 if not found
     */
    @GetMapping("/{collectionName}/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable("collectionName") String collectionName,
            @PathVariable("id") String id,
            @RequestParam(required = false) Map<String, String> params,
            HttpServletRequest request) {

        logger.debug("Get request for collection '{}', id '{}'", collectionName, id);

        setTenantContext(request);
        try {
            CollectionDefinition definition = resolveCollection(collectionName, request);
            if (definition == null) {
                logger.debug("Collection '{}' not found", collectionName);
                return ResponseEntity.notFound().build();
            }

            // Check system collection cache for get-by-id
            String tenantId = request.getHeader("X-Tenant-ID");
            if (definition.systemCollection() && systemCollectionCache != null) {
                Optional<Map<String, Object>> cached = systemCollectionCache.getByIdResponse(
                        tenantId, collectionName, id);
                if (cached.isPresent()) {
                    logger.debug("Cache hit for system collection get: {}/{}", collectionName, id);
                    return ResponseEntity.ok(cached.get());
                }
            }

            Optional<Map<String, Object>> record = queryEngine.getById(definition, id);

            // If not found by ID and the value is not a UUID, try display field lookup
            if (record.isEmpty() && !UUID_PATTERN.matcher(id).matches()) {
                record = resolveByDisplayField(definition, id, request);
            }

            if (record.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = toJsonApiResponse(record.get(), collectionName, definition);

            // Resolve JSON:API ?include= parameter for inverse (has-many) relationships
            List<String> includeNames = parseIncludeParam(params);
            if (!includeNames.isEmpty()) {
                List<Map<String, Object>> included = resolveIncludes(
                        includeNames, List.of(record.get()), collectionName, definition, request);
                if (!included.isEmpty()) {
                    response.put("included", included);
                }
            }

            // Cache the response for system collections (only when no includes)
            if (definition.systemCollection() && systemCollectionCache != null && includeNames.isEmpty()) {
                systemCollectionCache.putByIdResponse(tenantId, collectionName, id, response);
            }

            return ResponseEntity.ok(response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Creates a new record in the collection.
     * 
     * @param collectionName the collection name
     * @param requestBody the JSON:API formatted request body
     * @return the created record with 201 status, or 404 if collection not found
     */
    @PostMapping("/{collectionName}")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable("collectionName") String collectionName,
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {

        logger.debug("Create request for collection '{}' with data: {}", collectionName, requestBody);

        setTenantContext(request);
        try {
            CollectionDefinition definition = resolveCollection(collectionName, request);
            if (definition == null) {
                logger.debug("Collection '{}' not found", collectionName);
                return ResponseEntity.notFound().build();
            }

            // Reject writes to read-only collections
            if (definition.readOnly()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        toJsonApiErrorResponse("Collection '" + collectionName + "' is read-only"));
            }

            // Extract attributes from JSON:API format
            Map<String, Object> attributes = extractAttributes(requestBody);

            // Extract relationships from JSON:API format
            Map<String, Object> relationships = extractRelationships(requestBody);

            // Merge attributes and relationships for storage
            Map<String, Object> data = new java.util.HashMap<>(attributes);
            data.putAll(relationships);

            // Inject audit fields from gateway-forwarded user ID (resolved to platform_user UUID)
            String userId = resolveUserId(request);
            if (userId != null) {
                data.put("createdBy", userId);
                data.put("updatedBy", userId);
            }

            // Inject tenant ID for tenant-scoped system collections
            injectTenantId(data, definition, request);

            Map<String, Object> created = queryEngine.create(definition, data);

            // Evict system collection cache after write
            evictSystemCollectionCache(definition, request);

            // Return in JSON:API format
            return ResponseEntity.status(HttpStatus.CREATED).body(toJsonApiResponse(created, collectionName, definition));
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Updates an existing record in the collection (PUT).
     * 
     * @param collectionName the collection name
     * @param id the record ID
     * @param requestBody the JSON:API formatted request body with updated data
     * @return the updated record in JSON:API format, or 404 if collection or record not found
     */
    @PutMapping("/{collectionName}/{id}")
    public ResponseEntity<Map<String, Object>> updatePut(
            @PathVariable("collectionName") String collectionName,
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {

        return performUpdate(collectionName, id, requestBody, request);
    }
    
    /**
     * Updates an existing record in the collection (PATCH).
     * 
     * @param collectionName the collection name
     * @param id the record ID
     * @param requestBody the JSON:API formatted request body with updated data
     * @return the updated record in JSON:API format, or 404 if collection or record not found
     */
    @PatchMapping("/{collectionName}/{id}")
    public ResponseEntity<Map<String, Object>> updatePatch(
            @PathVariable("collectionName") String collectionName,
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {

        return performUpdate(collectionName, id, requestBody, request);
    }
    
    /**
     * Performs the update operation.
     *
     * @param collectionName the collection name
     * @param id the record ID
     * @param requestBody the JSON:API formatted request body with updated data
     * @param request the HTTP servlet request
     * @return the updated record in JSON:API format, or 404 if collection or record not found
     */
    private ResponseEntity<Map<String, Object>> performUpdate(
            String collectionName,
            String id,
            Map<String, Object> requestBody,
            HttpServletRequest request) {

        logger.debug("Update request for collection '{}', id '{}' with data: {}", collectionName, id, requestBody);

        setTenantContext(request);
        try {
            CollectionDefinition definition = resolveCollection(collectionName, request);
            if (definition == null) {
                logger.debug("Collection '{}' not found", collectionName);
                return ResponseEntity.notFound().build();
            }

            // Reject writes to read-only collections
            if (definition.readOnly()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        toJsonApiErrorResponse("Collection '" + collectionName + "' is read-only"));
            }

            // Extract attributes from JSON:API format
            Map<String, Object> attributes = extractAttributes(requestBody);

            // Extract relationships from JSON:API format
            Map<String, Object> relationships = extractRelationships(requestBody);

            // Merge attributes and relationships for storage
            Map<String, Object> data = new java.util.HashMap<>(attributes);
            data.putAll(relationships);

            // Inject audit field from gateway-forwarded user ID (resolved to platform_user UUID)
            String userId = resolveUserId(request);
            if (userId != null) {
                data.put("updatedBy", userId);
            }

            Optional<Map<String, Object>> updated = queryEngine.update(definition, id, data);

            // Evict system collection cache after write
            if (updated.isPresent()) {
                evictSystemCollectionCache(definition, request);
            }

            return updated.map(r -> ResponseEntity.ok(toJsonApiResponse(r, collectionName, definition)))
                          .orElse(ResponseEntity.notFound().build());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Deletes a record from the collection.
     * 
     * @param collectionName the collection name
     * @param id the record ID
     * @return 204 No Content if deleted, 404 if collection or record not found
     */
    @DeleteMapping("/{collectionName}/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("collectionName") String collectionName,
            @PathVariable("id") String id,
            HttpServletRequest request) {

        logger.debug("Delete request for collection '{}', id '{}'", collectionName, id);

        setTenantContext(request);
        try {
            CollectionDefinition definition = resolveCollection(collectionName, request);
            if (definition == null) {
                logger.debug("Collection '{}' not found", collectionName);
                return ResponseEntity.notFound().build();
            }

            // Reject deletes on read-only collections
            if (definition.readOnly()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            boolean deleted = queryEngine.delete(definition, id);

            // Evict system collection cache after delete
            if (deleted) {
                evictSystemCollectionCache(definition, request);
            }

            return deleted ? ResponseEntity.noContent().build()
                           : ResponseEntity.notFound().build();
        } finally {
            TenantContext.clear();
        }
    }

    // ==================== Sub-Resource Endpoints ====================

    /**
     * Lists child records under a parent resource.
     *
     * <p>Resolves the relationship between parent and child collections,
     * then filters child records by the parent's ID.
     *
     * @param parentName the parent collection name
     * @param parentId the parent record ID
     * @param childName the child collection name
     * @param params query parameters for pagination, sorting, filtering
     * @param request the HTTP servlet request
     * @return child records filtered by parent, or 404 if collections/relationship not found
     */
    @GetMapping("/{parentName}/{parentId}/{childName}")
    public ResponseEntity<Map<String, Object>> listChildren(
            @PathVariable("parentName") String parentName,
            @PathVariable("parentId") String parentId,
            @PathVariable("childName") String childName,
            @RequestParam(required = false) Map<String, String> params,
            HttpServletRequest request) {

        logger.debug("List children request: parent='{}', parentId='{}', child='{}'",
                parentName, parentId, childName);

        setTenantContext(request);
        try {
            SubResourceRelation relation = resolveSubResource(parentName, childName, request);
            if (relation == null) {
                return ResponseEntity.notFound().build();
            }

            QueryRequest queryRequest = QueryRequest.fromParams(params);

            // Inject parent ID filter on the child's reference field
            List<FilterCondition> filters = new ArrayList<>(queryRequest.filters());
            filters.add(new FilterCondition(relation.parentRefFieldName(), FilterOperator.EQ, parentId));
            queryRequest = queryRequest.withFilters(filters);

            // Inject tenant filter if applicable
            queryRequest = injectTenantFilter(queryRequest, relation.childDef(), request);

            QueryResult result = queryEngine.executeQuery(relation.childDef(), queryRequest);

            return ResponseEntity.ok(toJsonApiListResponse(result, childName, relation.childDef()));
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Gets a single child record under a parent resource.
     *
     * @param parentName the parent collection name
     * @param parentId the parent record ID
     * @param childName the child collection name
     * @param childId the child record ID
     * @param request the HTTP servlet request
     * @return the child record, or 404 if not found
     */
    @GetMapping("/{parentName}/{parentId}/{childName}/{childId}")
    public ResponseEntity<Map<String, Object>> getChild(
            @PathVariable("parentName") String parentName,
            @PathVariable("parentId") String parentId,
            @PathVariable("childName") String childName,
            @PathVariable("childId") String childId,
            HttpServletRequest request) {

        logger.debug("Get child request: parent='{}', parentId='{}', child='{}', childId='{}'",
                parentName, parentId, childName, childId);

        setTenantContext(request);
        try {
            SubResourceRelation relation = resolveSubResource(parentName, childName, request);
            if (relation == null) {
                return ResponseEntity.notFound().build();
            }

            Optional<Map<String, Object>> record = queryEngine.getById(relation.childDef(), childId);
            return record.map(r -> ResponseEntity.ok(toJsonApiResponse(r, childName, relation.childDef())))
                         .orElse(ResponseEntity.notFound().build());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Creates a child record under a parent resource.
     *
     * <p>Automatically sets the parent reference field to the parent ID.
     *
     * @param parentName the parent collection name
     * @param parentId the parent record ID
     * @param childName the child collection name
     * @param requestBody the JSON:API formatted request body
     * @param request the HTTP servlet request
     * @return the created record with 201 status
     */
    @PostMapping("/{parentName}/{parentId}/{childName}")
    public ResponseEntity<Map<String, Object>> createChild(
            @PathVariable("parentName") String parentName,
            @PathVariable("parentId") String parentId,
            @PathVariable("childName") String childName,
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {

        logger.debug("Create child request: parent='{}', parentId='{}', child='{}'",
                parentName, parentId, childName);

        setTenantContext(request);
        try {
            SubResourceRelation relation = resolveSubResource(parentName, childName, request);
            if (relation == null) {
                return ResponseEntity.notFound().build();
            }

            if (relation.childDef().readOnly()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        toJsonApiErrorResponse("Collection '" + childName + "' is read-only"));
            }

            Map<String, Object> attributes = extractAttributes(requestBody);
            Map<String, Object> relationships = extractRelationships(requestBody);

            Map<String, Object> data = new java.util.HashMap<>(attributes);
            data.putAll(relationships);

            // Auto-set the parent reference field
            data.put(relation.parentRefFieldName(), parentId);

            // Inject audit fields (resolved to platform_user UUID)
            String userId = resolveUserId(request);
            if (userId != null) {
                data.put("createdBy", userId);
                data.put("updatedBy", userId);
            }

            // Inject tenant ID for tenant-scoped system collections
            injectTenantId(data, relation.childDef(), request);

            Map<String, Object> created = queryEngine.create(relation.childDef(), data);

            evictSystemCollectionCache(relation.childDef(), request);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toJsonApiResponse(created, childName, relation.childDef()));
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Updates a child record under a parent resource (PUT).
     *
     * @param parentName the parent collection name
     * @param parentId the parent record ID
     * @param childName the child collection name
     * @param childId the child record ID
     * @param requestBody the JSON:API formatted request body
     * @param request the HTTP servlet request
     * @return the updated record, or 404 if not found
     */
    @PutMapping("/{parentName}/{parentId}/{childName}/{childId}")
    public ResponseEntity<Map<String, Object>> updateChild(
            @PathVariable("parentName") String parentName,
            @PathVariable("parentId") String parentId,
            @PathVariable("childName") String childName,
            @PathVariable("childId") String childId,
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {

        return performChildUpdate(parentName, parentId, childName, childId, requestBody, request);
    }

    /**
     * Updates a child record under a parent resource (PATCH).
     *
     * @param parentName the parent collection name
     * @param parentId the parent record ID
     * @param childName the child collection name
     * @param childId the child record ID
     * @param requestBody the JSON:API formatted request body
     * @param request the HTTP servlet request
     * @return the updated record, or 404 if not found
     */
    @PatchMapping("/{parentName}/{parentId}/{childName}/{childId}")
    public ResponseEntity<Map<String, Object>> patchChild(
            @PathVariable("parentName") String parentName,
            @PathVariable("parentId") String parentId,
            @PathVariable("childName") String childName,
            @PathVariable("childId") String childId,
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {

        return performChildUpdate(parentName, parentId, childName, childId, requestBody, request);
    }

    /**
     * Performs the child update operation.
     */
    private ResponseEntity<Map<String, Object>> performChildUpdate(
            String parentName, String parentId,
            String childName, String childId,
            Map<String, Object> requestBody,
            HttpServletRequest request) {

        logger.debug("Update child request: parent='{}', parentId='{}', child='{}', childId='{}'",
                parentName, parentId, childName, childId);

        setTenantContext(request);
        try {
            SubResourceRelation relation = resolveSubResource(parentName, childName, request);
            if (relation == null) {
                return ResponseEntity.notFound().build();
            }

            if (relation.childDef().readOnly()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        toJsonApiErrorResponse("Collection '" + childName + "' is read-only"));
            }

            Map<String, Object> attributes = extractAttributes(requestBody);
            Map<String, Object> relationships = extractRelationships(requestBody);

            Map<String, Object> data = new java.util.HashMap<>(attributes);
            data.putAll(relationships);

            String userId = resolveUserId(request);
            if (userId != null) {
                data.put("updatedBy", userId);
            }

            Optional<Map<String, Object>> updated = queryEngine.update(relation.childDef(), childId, data);

            if (updated.isPresent()) {
                evictSystemCollectionCache(relation.childDef(), request);
            }

            return updated.map(r -> ResponseEntity.ok(toJsonApiResponse(r, childName, relation.childDef())))
                          .orElse(ResponseEntity.notFound().build());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Deletes a child record under a parent resource.
     *
     * @param parentName the parent collection name
     * @param parentId the parent record ID
     * @param childName the child collection name
     * @param childId the child record ID
     * @param request the HTTP servlet request
     * @return 204 No Content if deleted, 404 if not found
     */
    @DeleteMapping("/{parentName}/{parentId}/{childName}/{childId}")
    public ResponseEntity<Void> deleteChild(
            @PathVariable("parentName") String parentName,
            @PathVariable("parentId") String parentId,
            @PathVariable("childName") String childName,
            @PathVariable("childId") String childId,
            HttpServletRequest request) {

        logger.debug("Delete child request: parent='{}', parentId='{}', child='{}', childId='{}'",
                parentName, parentId, childName, childId);

        setTenantContext(request);
        try {
            SubResourceRelation relation = resolveSubResource(parentName, childName, request);
            if (relation == null) {
                return ResponseEntity.notFound().build();
            }

            if (relation.childDef().readOnly()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            boolean deleted = queryEngine.delete(relation.childDef(), childId);

            if (deleted) {
                evictSystemCollectionCache(relation.childDef(), request);
            }

            return deleted ? ResponseEntity.noContent().build()
                           : ResponseEntity.notFound().build();
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Resolves a sub-resource relationship between parent and child collections.
     *
     * @param parentName the parent collection name
     * @param childName the child collection name
     * @param request the HTTP request (for on-demand loading)
     * @return the SubResourceRelation, or null if not found
     */
    private SubResourceRelation resolveSubResource(String parentName, String childName,
                                                     HttpServletRequest request) {
        CollectionDefinition parentDef = resolveCollection(parentName, request);
        if (parentDef == null) {
            logger.debug("Parent collection '{}' not found", parentName);
            return null;
        }

        CollectionDefinition childDef = resolveCollection(childName, request);
        if (childDef == null) {
            logger.debug("Child collection '{}' not found", childName);
            return null;
        }

        Optional<SubResourceRelation> relation = SubResourceResolver.resolve(parentDef, childDef);
        if (relation.isEmpty()) {
            logger.debug("No relationship found between parent '{}' and child '{}'",
                    parentName, childName);
            return null;
        }

        return relation.get();
    }

    /**
     * Injects a tenant_id filter for tenant-scoped system collections.
     * This ensures list queries only return records for the current tenant.
     *
     * @param queryRequest the original query request
     * @param definition the collection definition
     * @param request the HTTP servlet request (used to extract tenant ID)
     * @return the query request with tenant filter injected, or the original if not applicable
     */
    private QueryRequest injectTenantFilter(QueryRequest queryRequest, CollectionDefinition definition,
                                             HttpServletRequest request) {
        if (!definition.systemCollection() || !definition.tenantScoped()) {
            return queryRequest;
        }

        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId == null || tenantId.isBlank()) {
            logger.warn("No tenant ID found for tenant-scoped system collection '{}'", definition.name());
            return queryRequest;
        }

        // For the 'collections' collection, include both tenant-specific and system
        // records so that system collections are visible alongside custom ones.
        List<FilterCondition> filters = new ArrayList<>(queryRequest.filters());
        if ("collections".equals(definition.name())) {
            filters.add(new FilterCondition("tenantId", FilterOperator.IN,
                    List.of(tenantId, SystemCollectionDefinitions.SYSTEM_TENANT_ID)));
        } else {
            filters.add(new FilterCondition("tenantId", FilterOperator.EQ, tenantId));
        }
        return queryRequest.withFilters(filters);
    }

    /**
     * Injects tenant ID into record data for tenant-scoped system collections.
     *
     * @param data the record data
     * @param definition the collection definition
     * @param request the HTTP servlet request (used to extract tenant ID)
     */
    /**
     * Resolves the user identifier from the X-User-Id header to a platform_user UUID.
     * If a resolver is configured and the identifier is not already a UUID, the resolver
     * is used to translate it. Otherwise, the raw identifier is returned.
     */
    private String resolveUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            return null;
        }
        if (userIdResolver != null && !UUID_PATTERN.matcher(userId).matches()) {
            String tenantId = request.getHeader("X-Tenant-ID");
            String resolved = userIdResolver.resolve(userId, tenantId);
            if (resolved != null) {
                return resolved;
            }
        }
        return userId;
    }

    /**
     * Sets the tenant context (ID and slug) from the request headers.
     */
    private void setTenantContext(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-ID");
        String tenantSlug = request.getHeader("X-Tenant-Slug");
        TenantContext.set(tenantId);
        TenantContext.setSlug(tenantSlug);
    }

    private void injectTenantId(Map<String, Object> data, CollectionDefinition definition,
                                 HttpServletRequest request) {
        if (!definition.systemCollection() || !definition.tenantScoped()) {
            return;
        }

        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId != null && !tenantId.isBlank() && !data.containsKey("tenantId")) {
            data.put("tenantId", tenantId);
        }
    }

    /**
     * Builds a JSON:API error response.
     *
     * @param message the error message
     * @return a JSON:API error document
     */
    private Map<String, Object> toJsonApiErrorResponse(String message) {
        Map<String, Object> error = new java.util.HashMap<>();
        error.put("status", "403");
        error.put("title", "Forbidden");
        error.put("detail", message);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("errors", List.of(error));
        return response;
    }

    /**
     * Resolves a collection definition by name, with on-demand loading fallback.
     *
     * <p>First checks the local registry. If not found and an on-demand loader
     * is configured, attempts to load the collection from the control plane
     * using the tenant ID from the request's {@code X-Tenant-ID} header.
     *
     * @param collectionName the collection name
     * @param request the HTTP servlet request (used to extract tenant ID)
     * @return the collection definition, or {@code null} if not found
     */
    private CollectionDefinition resolveCollection(String collectionName, HttpServletRequest request) {
        // TenantContext is already set before this method is called,
        // so registry.get() will use the tenant-scoped key automatically
        CollectionDefinition definition = registry.get(collectionName);
        if (definition != null) {
            return definition;
        }

        // Fallback: try to load on demand from the control plane
        if (onDemandLoader != null) {
            String tenantId = request.getHeader("X-Tenant-ID");
            logger.info("Collection '{}' not in registry, attempting on-demand load (tenantId={})",
                    collectionName, tenantId);
            try {
                definition = onDemandLoader.load(collectionName, tenantId);
                if (definition != null) {
                    logger.info("Successfully loaded collection '{}' on demand", collectionName);
                }
            } catch (Exception e) {
                logger.warn("On-demand load failed for collection '{}': {}", collectionName, e.getMessage());
            }
        }

        return definition;
    }

    /**
     * Resolves a record by its display field value.
     *
     * <p>Looks up the collection's display field and verifies it is both unique
     * and required (non-nullable). If so, queries for a single record matching
     * the given value. This allows GET-by-id to accept human-readable values
     * (e.g., a slug or name) instead of UUIDs.
     *
     * @param definition the collection definition
     * @param value the display field value to look up
     * @param request the HTTP servlet request
     * @return the matching record, or empty if not found or display field is not eligible
     */
    private Optional<Map<String, Object>> resolveByDisplayField(
            CollectionDefinition definition, String value, HttpServletRequest request) {

        String displayFieldName = definition.displayFieldName();
        if (displayFieldName == null) {
            logger.debug("Collection '{}' has no display field configured", definition.name());
            return Optional.empty();
        }

        FieldDefinition displayField = definition.getField(displayFieldName);
        if (displayField == null) {
            logger.debug("Display field '{}' not found in collection '{}'",
                    displayFieldName, definition.name());
            return Optional.empty();
        }

        // Only allow display-field lookup when the field is unique and required
        if (!displayField.unique() || displayField.nullable()) {
            logger.debug("Display field '{}' on collection '{}' is not eligible for lookup " +
                    "(unique={}, nullable={})", displayFieldName, definition.name(),
                    displayField.unique(), displayField.nullable());
            return Optional.empty();
        }

        logger.debug("Resolving '{}' by display field '{}' = '{}'",
                definition.name(), displayFieldName, value);

        List<FilterCondition> filters = new ArrayList<>();
        filters.add(new FilterCondition(displayFieldName, FilterOperator.EQ, value));

        // Inject tenant filter if applicable
        QueryRequest queryRequest = new QueryRequest(
                new Pagination(1, 1), List.of(), List.of(), filters);
        queryRequest = injectTenantFilter(queryRequest, definition, request);

        QueryResult result = queryEngine.executeQuery(definition, queryRequest);
        if (result.data().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.data().get(0));
    }

    /**
     * Extracts attributes from a JSON:API formatted request body.
     * 
     * @param requestBody the JSON:API request body
     * @return the attributes map, or empty map if not found
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAttributes(Map<String, Object> requestBody) {
        if (requestBody.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
            if (data != null && data.containsKey("attributes")) {
                Object attributes = data.get("attributes");
                if (attributes instanceof Map) {
                    return (Map<String, Object>) attributes;
                }
            }
        }
        // If not JSON:API format, assume the whole body is attributes
        return requestBody;
    }
    
    /**
     * Extracts relationships from a JSON:API formatted request body.
     * Converts relationship references to field values using the relationship
     * name directly as the field name (e.g., "category" → field "category").
     *
     * @param requestBody the JSON:API request body
     * @return the relationships as field values, or empty map if not found
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRelationships(Map<String, Object> requestBody) {
        Map<String, Object> fieldValues = new java.util.HashMap<>();

        if (requestBody.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
            if (data != null && data.containsKey("relationships")) {
                Object relationships = data.get("relationships");
                if (relationships instanceof Map) {
                    Map<String, Object> relationshipsMap = (Map<String, Object>) relationships;

                    for (Map.Entry<String, Object> entry : relationshipsMap.entrySet()) {
                        String relationshipName = entry.getKey();
                        Object relationshipValue = entry.getValue();

                        if (relationshipValue instanceof Map) {
                            Map<String, Object> relationship = (Map<String, Object>) relationshipValue;
                            if (relationship.containsKey("data")) {
                                Object relationshipData = relationship.get("data");
                                if (relationshipData instanceof Map) {
                                    Map<String, Object> relData = (Map<String, Object>) relationshipData;
                                    if (relData.containsKey("id")) {
                                        // Use relationship name directly as field name
                                        fieldValues.put(relationshipName, relData.get("id"));
                                    }
                                } else if (relationshipData == null) {
                                    // Null relationship — clear the field
                                    fieldValues.put(relationshipName, null);
                                }
                            }
                        }
                    }
                }
            }
        }

        return fieldValues;
    }
    
    /**
     * Converts a record to a JSON:API resource object using the collection
     * definition to identify relationship fields.
     *
     * <p>Relationship fields (those with {@code type.isRelationship()}) are placed
     * in the {@code relationships} section with their target collection as the
     * JSON:API type. All other fields go in {@code attributes}.
     *
     * @param record the record data
     * @param type the resource type (collection name)
     * @param definition the collection definition for field metadata
     * @return the JSON:API resource object
     */
    private Map<String, Object> toJsonApiResourceObject(Map<String, Object> record, String type,
                                                         CollectionDefinition definition) {
        Map<String, Object> resourceObject = new java.util.HashMap<>();

        resourceObject.put("type", type);
        resourceObject.put("id", record.get("id"));

        Map<String, Object> attributes = new java.util.HashMap<>();
        Map<String, Object> relationships = new java.util.HashMap<>();

        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Skip id — it's at the top level
            if ("id".equals(key)) {
                continue;
            }

            // Use field schema to detect relationship fields
            FieldDefinition fieldDef = definition.getField(key);
            if (fieldDef != null && fieldDef.type().isRelationship()
                    && fieldDef.referenceConfig() != null) {
                String relType = fieldDef.referenceConfig().targetCollection();
                Map<String, Object> relationshipData = new java.util.HashMap<>();
                if (value != null) {
                    relationshipData.put("data", Map.of(
                        "type", relType,
                        "id", value
                    ));
                } else {
                    relationshipData.put("data", null);
                }
                relationships.put(key, relationshipData);
            } else {
                attributes.put(key, value);
            }
        }

        resourceObject.put("attributes", attributes);
        if (!relationships.isEmpty()) {
            resourceObject.put("relationships", relationships);
        }

        return resourceObject;
    }

    /**
     * Wraps a single record in a JSON:API document envelope.
     *
     * @param record the record data
     * @param type the resource type (collection name)
     * @param definition the collection definition
     * @return the JSON:API document with {@code data} key
     */
    private Map<String, Object> toJsonApiResponse(Map<String, Object> record, String type,
                                                   CollectionDefinition definition) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("data", toJsonApiResourceObject(record, type, definition));
        return response;
    }

    /**
     * Converts a QueryResult into a JSON:API list response with proper
     * relationship formatting and pagination metadata.
     *
     * @param result the query result
     * @param type the resource type (collection name)
     * @param definition the collection definition
     * @return the JSON:API document with {@code data} array and {@code metadata}
     */
    private Map<String, Object> toJsonApiListResponse(QueryResult result, String type,
                                                       CollectionDefinition definition) {
        List<Map<String, Object>> jsonApiData = new ArrayList<>();
        for (Map<String, Object> record : result.data()) {
            jsonApiData.add(toJsonApiResourceObject(record, type, definition));
        }

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("data", jsonApiData);
        response.put("metadata", Map.of(
            "totalCount", result.metadata().totalCount(),
            "currentPage", result.metadata().currentPage(),
            "pageSize", result.metadata().pageSize(),
            "totalPages", result.metadata().totalPages()
        ));
        return response;
    }

    // ==================== Include Resolution ====================

    /**
     * Parses the {@code include} query parameter into a list of relationship names.
     *
     * @param params the query parameters
     * @return list of include names, or empty list if not present
     */
    private List<String> parseIncludeParam(Map<String, String> params) {
        if (params == null) {
            return List.of();
        }
        String includeParam = params.get("include");
        if (includeParam == null || includeParam.isBlank()) {
            return List.of();
        }
        return Arrays.stream(includeParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Resolves included resources for JSON:API {@code ?include=} parameter.
     *
     * <p>Supports both direct and transitive (grandchild) includes. Direct includes
     * are child collections that have a field referencing the primary collection.
     * Transitive includes are grandchild collections that reference a direct child
     * collection rather than the primary collection itself.
     *
     * <p>Resolution proceeds in two passes:
     * <ol>
     *   <li><strong>Direct pass:</strong> Resolve includes that have a direct
     *       relationship to the primary collection.</li>
     *   <li><strong>Transitive pass:</strong> For any unresolved includes, check
     *       whether they reference an already-resolved direct child collection.
     *       If so, use the direct child records' IDs to query the grandchild
     *       collection.</li>
     * </ol>
     *
     * <p>Example: When fetching a {@code page-layouts} record with
     * {@code ?include=layout-sections,layout-fields,layout-related-lists}:
     * <ul>
     *   <li>{@code layout-sections} and {@code layout-related-lists} are resolved
     *       directly (both have {@code layoutId → page-layouts}).</li>
     *   <li>{@code layout-fields} is resolved transitively via {@code layout-sections}
     *       (it has {@code sectionId → layout-sections}).</li>
     * </ul>
     *
     * @param includeNames the requested include relationship names
     * @param primaryData the primary data records
     * @param primaryCollectionName the primary collection name
     * @param primaryDefinition the primary collection definition
     * @param request the HTTP servlet request
     * @return list of included resource objects in JSON:API format
     */
    private List<Map<String, Object>> resolveIncludes(
            List<String> includeNames,
            List<Map<String, Object>> primaryData,
            String primaryCollectionName,
            CollectionDefinition primaryDefinition,
            HttpServletRequest request) {

        // Collect all primary record IDs
        List<Object> parentIds = primaryData.stream()
                .map(record -> record.get("id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (parentIds.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> allIncluded = new ArrayList<>();

        // Track resolved collections and their raw data for transitive resolution
        List<String> unresolvedNames = new ArrayList<>();
        Map<String, List<Map<String, Object>>> resolvedChildData = new LinkedHashMap<>();
        Map<String, CollectionDefinition> resolvedChildDefs = new LinkedHashMap<>();

        // --- Pass 1: Direct includes ---
        for (String includeName : includeNames) {
            CollectionDefinition childDef = resolveCollection(includeName, request);
            if (childDef == null) {
                logger.debug("Include target collection '{}' not found, skipping", includeName);
                continue;
            }

            // Check has-many: child has FK pointing to primary
            Optional<SubResourceRelation> relation =
                    SubResourceResolver.resolve(primaryDefinition, childDef);
            if (relation.isPresent()) {
                String parentRefField = relation.get().parentRefFieldName();
                logger.debug("Resolving has-many include '{}': querying where {} IN {} parent IDs",
                        includeName, parentRefField, parentIds.size());

                List<Map<String, Object>> childRecords =
                        queryChildRecords(childDef, parentRefField, parentIds, request);

                for (Map<String, Object> childRecord : childRecords) {
                    allIncluded.add(toJsonApiResourceObject(childRecord, includeName, childDef));
                }
                resolvedChildData.put(includeName, childRecords);
                resolvedChildDefs.put(includeName, childDef);
                logger.debug("Resolved {} has-many included records for '{}'",
                        childRecords.size(), includeName);
                continue;
            }

            // Check belongs-to: primary has FK pointing to child.
            // Use resolveAll to collect FK values from ALL fields referencing the
            // target collection (e.g., both created_by and updated_by → users).
            List<SubResourceRelation> belongsToAll =
                    SubResourceResolver.resolveAll(childDef, primaryDefinition);
            if (!belongsToAll.isEmpty()) {
                // Collect FK values from all matching fields
                java.util.Set<Object> fkValueSet = new java.util.LinkedHashSet<>();
                List<String> fkFieldNames = new java.util.ArrayList<>();
                for (SubResourceRelation rel : belongsToAll) {
                    String fkField = rel.parentRefFieldName();
                    fkFieldNames.add(fkField);
                    for (Map<String, Object> record : primaryData) {
                        Object val = record.get(fkField);
                        if (val != null) {
                            fkValueSet.add(val);
                        }
                    }
                }
                List<Object> fkValues = new java.util.ArrayList<>(fkValueSet);

                if (!fkValues.isEmpty()) {
                    logger.debug("Resolving belongs-to include '{}': querying where id IN {} FK values from fields {}",
                            includeName, fkValues.size(), fkFieldNames);
                    List<Map<String, Object>> referencedRecords =
                            queryChildRecords(childDef, "id", fkValues, request);
                    for (Map<String, Object> rec : referencedRecords) {
                        allIncluded.add(toJsonApiResourceObject(rec, includeName, childDef));
                    }
                    resolvedChildData.put(includeName, referencedRecords);
                    resolvedChildDefs.put(includeName, childDef);
                    logger.debug("Resolved {} belongs-to included records for '{}'",
                            referencedRecords.size(), includeName);
                }
                continue;
            }

            // No direct relationship — defer to transitive pass
            unresolvedNames.add(includeName);
        }

        // --- Pass 2: Transitive (grandchild) includes ---
        // Supports two chain types via an already-resolved intermediate collection:
        //   Has-many chain:   primary → intermediate (has-many) → grandchild (has-many)
        //                     e.g., page-layouts → layout-sections → layout-fields
        //                     grandchild has FK pointing to intermediate
        //   Belongs-to chain: primary → intermediate (has-many) → grandchild (belongs-to)
        //                     e.g., orders → order_items → products
        //                     intermediate has FK pointing to grandchild
        for (String includeName : unresolvedNames) {
            CollectionDefinition grandchildDef = resolveCollection(includeName, request);
            if (grandchildDef == null) {
                continue;
            }

            boolean resolved = false;
            // Check each already-resolved direct child to see if it's the
            // intermediate parent for this grandchild
            for (Map.Entry<String, CollectionDefinition> entry : resolvedChildDefs.entrySet()) {
                String intermediateCollectionName = entry.getKey();
                CollectionDefinition intermediateDef = entry.getValue();

                List<Map<String, Object>> intermediateData =
                        resolvedChildData.get(intermediateCollectionName);

                // Try has-many direction: grandchild has FK pointing to intermediate
                // e.g., layout-fields has sectionId FK pointing to layout-sections
                Optional<SubResourceRelation> hasManyRelation =
                        SubResourceResolver.resolve(intermediateDef, grandchildDef);
                if (hasManyRelation.isPresent()) {
                    String intermediateRefField = hasManyRelation.get().parentRefFieldName();

                    List<Object> intermediateIds = intermediateData.stream()
                            .map(r -> r.get("id"))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    if (intermediateIds.isEmpty()) {
                        logger.debug("No intermediate '{}' records to resolve transitive include '{}'",
                                intermediateCollectionName, includeName);
                        resolved = true;
                        break;
                    }

                    logger.debug("Resolving transitive include '{}' via '{}' (has-many): "
                                    + "querying where {} IN {} IDs",
                            includeName, intermediateCollectionName, intermediateRefField,
                            intermediateIds.size());

                    List<Map<String, Object>> grandchildRecords =
                            queryChildRecords(grandchildDef, intermediateRefField, intermediateIds,
                                    request);

                    for (Map<String, Object> record : grandchildRecords) {
                        allIncluded.add(toJsonApiResourceObject(record, includeName, grandchildDef));
                    }
                    logger.debug("Resolved {} transitive included records for '{}' via '{}'",
                            grandchildRecords.size(), includeName, intermediateCollectionName);
                    resolved = true;
                    break;
                }

                // Try belongs-to direction: intermediate has FK pointing to grandchild
                // e.g., order_items has product FK pointing to products
                Optional<SubResourceRelation> belongsToRelation =
                        SubResourceResolver.resolve(grandchildDef, intermediateDef);
                if (belongsToRelation.isPresent()) {
                    String fkField = belongsToRelation.get().parentRefFieldName();

                    // Extract FK values from intermediate records to query grandchild by ID
                    List<Object> fkValues = intermediateData.stream()
                            .map(r -> r.get(fkField))
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList());

                    if (fkValues.isEmpty()) {
                        logger.debug("No FK values in '{}' for transitive include '{}'",
                                intermediateCollectionName, includeName);
                        resolved = true;
                        break;
                    }

                    logger.debug("Resolving transitive include '{}' via '{}' (belongs-to): "
                                    + "querying where id IN {} FK values from field '{}'",
                            includeName, intermediateCollectionName, fkValues.size(), fkField);

                    List<Map<String, Object>> grandchildRecords =
                            queryChildRecords(grandchildDef, "id", fkValues, request);

                    for (Map<String, Object> record : grandchildRecords) {
                        allIncluded.add(toJsonApiResourceObject(record, includeName, grandchildDef));
                    }
                    logger.debug("Resolved {} transitive included records for '{}' via '{}'",
                            grandchildRecords.size(), includeName, intermediateCollectionName);
                    resolved = true;
                    break;
                }
            }

            if (!resolved) {
                logger.debug("No direct or transitive relationship from '{}' to '{}', "
                        + "skipping include", primaryCollectionName, includeName);
            }
        }

        return allIncluded;
    }

    /**
     * Builds a deterministic hash string from a query request for use as a cache key.
     * Uses the record's toString() which includes all components (pagination, sorting,
     * fields, filters).
     *
     * @param queryRequest the query request
     * @return a deterministic string representation suitable for cache keys
     */
    private String buildQueryHash(QueryRequest queryRequest) {
        return queryRequest.toString();
    }

    /**
     * Evicts cached entries for a system collection after a write operation.
     * No-op if the collection is not a system collection or no cache is configured.
     *
     * @param definition the collection definition
     * @param request the HTTP servlet request (used to extract tenant ID)
     */
    private void evictSystemCollectionCache(CollectionDefinition definition, HttpServletRequest request) {
        if (!definition.systemCollection() || systemCollectionCache == null) {
            return;
        }
        String tenantId = request.getHeader("X-Tenant-ID");
        systemCollectionCache.evict(tenantId, definition.name());
        logger.debug("Evicted system collection cache for: {} (tenant={})", definition.name(), tenantId);
    }

    /**
     * Queries child records from a collection using an IN filter on the given
     * reference field.
     */
    private List<Map<String, Object>> queryChildRecords(
            CollectionDefinition childDef,
            String refField,
            List<Object> parentIds,
            HttpServletRequest request) {

        List<FilterCondition> filters = new ArrayList<>();
        filters.add(new FilterCondition(refField, FilterOperator.IN, parentIds));

        QueryRequest childQuery = new QueryRequest(
                new Pagination(1, 1000),
                List.of(),
                List.of(),
                filters
        );

        childQuery = injectTenantFilter(childQuery, childDef, request);

        try {
            QueryResult childResult = queryEngine.executeQuery(childDef, childQuery);
            return childResult.data();
        } catch (Exception e) {
            logger.warn("Failed to query child records for '{}': {}", childDef.name(),
                    e.getMessage());
            return List.of();
        }
    }

}
