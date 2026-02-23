package com.emf.controlplane.controller;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.service.CollectionService;
import com.emf.runtime.datapath.DataPath;
import com.emf.runtime.datapath.DataPathValidationResult;
import com.emf.runtime.datapath.DataPathValidator;
import com.emf.runtime.datapath.CollectionDefinitionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for DataPath discovery and validation.
 * <p>
 * Provides endpoints for:
 * <ul>
 *   <li>Discovering traversable fields in a collection (expanding relationship fields)</li>
 *   <li>Validating DataPath expressions against collection schemas</li>
 * </ul>
 * <p>
 * Used by the UI's DataPathPicker component to build data path expressions
 * for workflow actions, email templates, and other features.
 */
@RestController
@RequestMapping("/control")
@PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_WORKFLOWS')")
public class DataPathController {

    private static final Logger log = LoggerFactory.getLogger(DataPathController.class);
    private static final int DEFAULT_DEPTH = 2;
    private static final int MAX_DEPTH = 5;

    private final CollectionService collectionService;
    private final FieldRepository fieldRepository;
    private final CollectionDefinitionProvider collectionDefinitionProvider;

    public DataPathController(CollectionService collectionService,
                               FieldRepository fieldRepository,
                               @Nullable CollectionDefinitionProvider collectionDefinitionProvider) {
        this.collectionService = collectionService;
        this.fieldRepository = fieldRepository;
        this.collectionDefinitionProvider = collectionDefinitionProvider;
    }

    /**
     * Returns the field tree for a collection, expanding relationship fields
     * to the specified depth.
     * <p>
     * Each field node includes:
     * <ul>
     *   <li>{@code name} — field name</li>
     *   <li>{@code displayName} — human-readable label</li>
     *   <li>{@code type} — field type (string, number, reference, etc.)</li>
     *   <li>{@code isRelationship} — whether this field references another collection</li>
     *   <li>{@code targetCollectionId} — target collection ID (for relationship fields)</li>
     *   <li>{@code children} — expanded child fields (for relationship fields, up to depth)</li>
     * </ul>
     *
     * @param collectionId the collection ID to explore
     * @param depth        maximum expansion depth (default 2, max 5)
     * @return nested tree of field nodes
     */
    @GetMapping("/collections/{collectionId}/data-paths")
    public List<Map<String, Object>> getDataPaths(
            @PathVariable String collectionId,
            @RequestParam(required = false, defaultValue = "2") int depth) {
        int clampedDepth = Math.min(Math.max(depth, 1), MAX_DEPTH);
        log.debug("Getting data paths for collection {} with depth {}", collectionId, clampedDepth);

        Collection collection = collectionService.getCollection(collectionId);
        List<Field> fields = fieldRepository.findByCollectionIdAndActiveTrue(collectionId);

        return buildFieldTree(fields, clampedDepth, 0, new HashSet<>());
    }

    /**
     * Validates a DataPath expression against a root collection's schema.
     *
     * @param request the validation request containing rootCollectionId and expression
     * @return validation result with terminal field metadata
     */
    @PostMapping("/data-paths/validate")
    public Map<String, Object> validateDataPath(@RequestBody DataPathValidateRequest request) {
        log.debug("Validating data path: rootCollectionId={}, expression={}",
            request.rootCollectionId, request.expression);

        if (collectionDefinitionProvider == null) {
            return Map.of(
                "valid", false,
                "errorMessage", "DataPath validation is not available (no CollectionDefinitionProvider configured)"
            );
        }

        try {
            // Look up collection name from ID
            Collection collection = collectionService.getCollection(request.rootCollectionId);
            String collectionName = collection.getName();

            DataPath path = DataPath.parse(request.expression, collectionName);
            DataPathValidator validator = new DataPathValidator(collectionDefinitionProvider);
            DataPathValidationResult result = validator.validate(path);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("valid", result.valid());
            if (result.errorMessage() != null) {
                response.put("errorMessage", result.errorMessage());
            }
            if (result.terminalFieldName() != null) {
                response.put("terminalFieldName", result.terminalFieldName());
            }
            if (result.terminalFieldType() != null) {
                response.put("terminalFieldType", result.terminalFieldType().name());
            }
            if (result.terminalCollectionName() != null) {
                response.put("terminalCollectionName", result.terminalCollectionName());
            }
            return response;
        } catch (Exception e) {
            return Map.of(
                "valid", false,
                "errorMessage", e.getMessage() != null ? e.getMessage() : "Invalid data path expression"
            );
        }
    }

    /**
     * Recursively builds a field tree, expanding relationship fields up to the
     * specified depth. Uses a visited set to prevent circular reference loops.
     */
    private List<Map<String, Object>> buildFieldTree(List<Field> fields, int maxDepth,
                                                      int currentDepth, Set<String> visited) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Field field : fields) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", field.getName());
            node.put("displayName", field.getDisplayName() != null ? field.getDisplayName() : field.getName());
            node.put("type", field.getType());

            boolean isRelationship = "reference".equals(field.getType())
                || "lookup".equalsIgnoreCase(field.getType())
                || "master_detail".equalsIgnoreCase(field.getType());
            node.put("isRelationship", isRelationship);

            if (isRelationship && field.getReferenceCollectionId() != null) {
                node.put("targetCollectionId", field.getReferenceCollectionId());

                // Expand children if within depth limit and not circular
                if (currentDepth < maxDepth - 1
                        && !visited.contains(field.getReferenceCollectionId())) {
                    Set<String> newVisited = new HashSet<>(visited);
                    newVisited.add(field.getReferenceCollectionId());

                    try {
                        List<Field> childFields = fieldRepository.findByCollectionIdAndActiveTrue(
                            field.getReferenceCollectionId());
                        List<Map<String, Object>> children = buildFieldTree(
                            childFields, maxDepth, currentDepth + 1, newVisited);
                        node.put("children", children);
                    } catch (Exception e) {
                        log.warn("Failed to expand relationship field {}: {}",
                            field.getName(), e.getMessage());
                        node.put("children", List.of());
                    }
                }
            }

            result.add(node);
        }

        return result;
    }

    /**
     * Request body for the data path validation endpoint.
     */
    public static class DataPathValidateRequest {
        private String rootCollectionId;
        private String expression;

        public String getRootCollectionId() { return rootCollectionId; }
        public void setRootCollectionId(String rootCollectionId) { this.rootCollectionId = rootCollectionId; }
        public String getExpression() { return expression; }
        public void setExpression(String expression) { this.expression = expression; }
    }
}
