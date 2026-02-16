package com.emf.runtime.router;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.query.QueryRequest;
import com.emf.runtime.query.QueryResult;
import com.emf.runtime.registry.CollectionOnDemandLoader;
import com.emf.runtime.registry.CollectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Dynamic REST controller for collection CRUD operations.
 * 
 * <p>This controller provides a unified API for all collections registered in the
 * {@link CollectionRegistry}. It dynamically routes requests based on the collection
 * name in the URL path.
 * 
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/collections/{collectionName} - List records with pagination, sorting, filtering</li>
 *   <li>GET /api/collections/{collectionName}/{id} - Get a single record by ID</li>
 *   <li>POST /api/collections/{collectionName} - Create a new record</li>
 *   <li>PUT /api/collections/{collectionName}/{id} - Update an existing record</li>
 *   <li>DELETE /api/collections/{collectionName}/{id} - Delete a record</li>
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
@RequestMapping("/api/collections")
public class DynamicCollectionRouter {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicCollectionRouter.class);
    
    private final CollectionRegistry registry;
    private final QueryEngine queryEngine;

    /**
     * Optional on-demand loader that fetches unknown collections from the
     * control plane. When present, the router will try to load a collection
     * before returning 404.
     */
    private CollectionOnDemandLoader onDemandLoader;

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
    
    /**
     * Lists records from a collection with pagination, sorting, and filtering.
     *
     * @param collectionName the collection name
     * @param params query parameters for pagination, sorting, filtering, and field selection
     * @param request the HTTP servlet request
     * @return the query result or 404 if collection not found
     */
    @GetMapping("/{collectionName}")
    public ResponseEntity<QueryResult> list(
            @PathVariable("collectionName") String collectionName,
            @RequestParam(required = false) Map<String, String> params,
            HttpServletRequest request) {

        logger.debug("List request for collection '{}' with params: {}", collectionName, params);

        CollectionDefinition definition = resolveCollection(collectionName, request);
        if (definition == null) {
            logger.debug("Collection '{}' not found", collectionName);
            return ResponseEntity.notFound().build();
        }

        QueryRequest queryRequest = QueryRequest.fromParams(params);
        QueryResult result = queryEngine.executeQuery(definition, queryRequest);

        return ResponseEntity.ok(result);
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
            HttpServletRequest request) {

        logger.debug("Get request for collection '{}', id '{}'", collectionName, id);

        CollectionDefinition definition = resolveCollection(collectionName, request);
        if (definition == null) {
            logger.debug("Collection '{}' not found", collectionName);
            return ResponseEntity.notFound().build();
        }

        Optional<Map<String, Object>> record = queryEngine.getById(definition, id);
        return record.map(r -> ResponseEntity.ok(toJsonApiResponse(r, collectionName)))
                     .orElse(ResponseEntity.notFound().build());
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

        CollectionDefinition definition = resolveCollection(collectionName, request);
        if (definition == null) {
            logger.debug("Collection '{}' not found", collectionName);
            return ResponseEntity.notFound().build();
        }

        // Extract attributes from JSON:API format
        Map<String, Object> attributes = extractAttributes(requestBody);

        // Extract relationships from JSON:API format
        Map<String, Object> relationships = extractRelationships(requestBody);

        // Merge attributes and relationships for storage
        Map<String, Object> data = new java.util.HashMap<>(attributes);
        data.putAll(relationships);

        // Inject audit fields from gateway-forwarded user ID
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            data.put("createdBy", userId);
            data.put("updatedBy", userId);
        }

        Map<String, Object> created = queryEngine.create(definition, data);
        
        // Return in JSON:API format
        return ResponseEntity.status(HttpStatus.CREATED).body(toJsonApiResponse(created, collectionName));
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

        CollectionDefinition definition = resolveCollection(collectionName, request);
        if (definition == null) {
            logger.debug("Collection '{}' not found", collectionName);
            return ResponseEntity.notFound().build();
        }

        // Extract attributes from JSON:API format
        Map<String, Object> attributes = extractAttributes(requestBody);

        // Extract relationships from JSON:API format
        Map<String, Object> relationships = extractRelationships(requestBody);

        // Merge attributes and relationships for storage
        Map<String, Object> data = new java.util.HashMap<>(attributes);
        data.putAll(relationships);

        // Inject audit field from gateway-forwarded user ID
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            data.put("updatedBy", userId);
        }

        Optional<Map<String, Object>> updated = queryEngine.update(definition, id, data);
        return updated.map(r -> ResponseEntity.ok(toJsonApiResponse(r, collectionName)))
                      .orElse(ResponseEntity.notFound().build());
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

        CollectionDefinition definition = resolveCollection(collectionName, request);
        if (definition == null) {
            logger.debug("Collection '{}' not found", collectionName);
            return ResponseEntity.notFound().build();
        }
        
        boolean deleted = queryEngine.delete(definition, id);
        return deleted ? ResponseEntity.noContent().build() 
                       : ResponseEntity.notFound().build();
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
     * Converts relationship references to foreign key fields.
     * 
     * @param requestBody the JSON:API request body
     * @return the relationships as foreign key fields, or empty map if not found
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRelationships(Map<String, Object> requestBody) {
        Map<String, Object> foreignKeys = new java.util.HashMap<>();
        
        if (requestBody.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
            if (data != null && data.containsKey("relationships")) {
                Object relationships = data.get("relationships");
                if (relationships instanceof Map) {
                    Map<String, Object> relationshipsMap = (Map<String, Object>) relationships;
                    
                    // Convert each relationship to a foreign key field
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
                                        // Convert relationship name to foreign key field name
                                        // e.g., "project" -> "project_id"
                                        String foreignKeyField = relationshipName + "_id";
                                        foreignKeys.put(foreignKeyField, relData.get("id"));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return foreignKeys;
    }
    
    /**
     * Converts a record to JSON:API response format.
     * 
     * @param record the record data
     * @param type the resource type (collection name)
     * @return the JSON:API formatted response
     */
    private Map<String, Object> toJsonApiResponse(Map<String, Object> record, String type) {
        Map<String, Object> response = new java.util.HashMap<>();
        Map<String, Object> data = new java.util.HashMap<>();
        
        data.put("type", type);
        data.put("id", record.get("id"));
        
        // Separate attributes from system fields and foreign keys
        Map<String, Object> attributes = new java.util.HashMap<>();
        Map<String, Object> relationships = new java.util.HashMap<>();
        
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Skip id (it's in the top level)
            if ("id".equals(key)) {
                continue;
            }
            
            // Check if it's a foreign key field (ends with _id)
            if (key.endsWith("_id")) {
                String relationshipName = key.substring(0, key.length() - 3);
                Map<String, Object> relationshipData = new java.util.HashMap<>();
                if (value != null) {
                    relationshipData.put("data", Map.of(
                        "type", relationshipName + "s", // Pluralize (simple approach)
                        "id", value
                    ));
                } else {
                    relationshipData.put("data", null);
                }
                relationships.put(relationshipName, relationshipData);
            } else {
                attributes.put(key, value);
            }
        }
        
        data.put("attributes", attributes);
        if (!relationships.isEmpty()) {
            data.put("relationships", relationships);
        }
        
        response.put("data", data);
        return response;
    }
}
