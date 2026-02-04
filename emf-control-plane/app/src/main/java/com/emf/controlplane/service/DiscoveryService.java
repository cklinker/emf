package com.emf.controlplane.service;

import com.emf.controlplane.dto.AuthorizationHintsDto;
import com.emf.controlplane.dto.FieldAuthorizationHintsDto;
import com.emf.controlplane.dto.FieldMetadataDto;
import com.emf.controlplane.dto.ResourceDiscoveryDto;
import com.emf.controlplane.dto.ResourceMetadataDto;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.FieldPolicy;
import com.emf.controlplane.entity.RoutePolicy;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldPolicyRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.repository.RoutePolicyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for resource discovery.
 * Provides metadata about all active collections including their schemas,
 * available operations, and authorization hints.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>8.1: Return all active collections with their schemas</li>
 *   <li>8.2: Include field definitions, types, and constraints for each collection</li>
 *   <li>8.3: Include available operations for each collection</li>
 *   <li>8.4: Include authorization hints for each collection and field</li>
 * </ul>
 */
@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    /**
     * Standard CRUD operations available for collections.
     */
    private static final List<String> STANDARD_OPERATIONS = Arrays.asList(
            "CREATE", "READ", "UPDATE", "DELETE", "LIST"
    );

    private final CollectionRepository collectionRepository;
    private final FieldRepository fieldRepository;
    private final RoutePolicyRepository routePolicyRepository;
    private final FieldPolicyRepository fieldPolicyRepository;
    private final ObjectMapper objectMapper;

    public DiscoveryService(
            CollectionRepository collectionRepository,
            FieldRepository fieldRepository,
            RoutePolicyRepository routePolicyRepository,
            FieldPolicyRepository fieldPolicyRepository,
            ObjectMapper objectMapper) {
        this.collectionRepository = collectionRepository;
        this.fieldRepository = fieldRepository;
        this.routePolicyRepository = routePolicyRepository;
        this.fieldPolicyRepository = fieldPolicyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Discovers all active resources (collections) with their complete metadata.
     * Returns information about schemas, available operations, and authorization hints.
     * 
     * @return ResourceDiscoveryDto containing metadata for all active collections
     * 
     * Validates: Requirements 8.1, 8.2, 8.3, 8.4
     */
    @Transactional(readOnly = true)
    public ResourceDiscoveryDto discoverResources() {
        log.debug("Discovering all active resources");

        List<Collection> activeCollections = collectionRepository.findByActiveTrue();
        
        List<ResourceMetadataDto> resources = activeCollections.stream()
                .map(this::buildResourceMetadata)
                .collect(Collectors.toList());

        log.info("Discovered {} active resources", resources.size());
        return new ResourceDiscoveryDto(resources);
    }

    /**
     * Builds complete metadata for a single collection.
     * Includes field definitions, types, constraints, available operations,
     * and authorization hints.
     * 
     * @param collection The collection to build metadata for
     * @return ResourceMetadataDto containing the collection's complete metadata
     * 
     * Validates: Requirements 8.1, 8.2, 8.3, 8.4
     */
    private ResourceMetadataDto buildResourceMetadata(Collection collection) {
        log.debug("Building metadata for collection: {}", collection.getName());

        // Get active fields for the collection
        List<Field> activeFields = fieldRepository.findByCollectionIdAndActiveTrue(collection.getId());
        
        // Get route policies for authorization hints
        List<RoutePolicy> routePolicies = routePolicyRepository.findByCollectionId(collection.getId());
        
        // Get field policies for field-level authorization hints
        List<FieldPolicy> fieldPolicies = fieldPolicyRepository.findByCollectionId(collection.getId());
        
        // Build field metadata with authorization hints
        List<FieldMetadataDto> fieldMetadata = buildFieldMetadata(activeFields, fieldPolicies);
        
        // Build collection-level authorization hints
        AuthorizationHintsDto authorizationHints = buildAuthorizationHints(routePolicies);
        
        return new ResourceMetadataDto(
                collection.getId(),
                collection.getName(),
                collection.getDescription(),
                collection.getCurrentVersion(),
                fieldMetadata,
                STANDARD_OPERATIONS,
                authorizationHints
        );
    }

    /**
     * Builds metadata for all fields in a collection, including authorization hints.
     * 
     * @param fields The list of active fields
     * @param fieldPolicies The list of field policies for the collection
     * @return List of FieldMetadataDto with complete field information
     * 
     * Validates: Requirements 8.2, 8.4
     */
    private List<FieldMetadataDto> buildFieldMetadata(List<Field> fields, List<FieldPolicy> fieldPolicies) {
        // Group field policies by field ID for efficient lookup
        Map<String, List<FieldPolicy>> policiesByFieldId = fieldPolicies.stream()
                .collect(Collectors.groupingBy(fp -> fp.getField().getId()));

        return fields.stream()
                .map(field -> {
                    Map<String, Object> constraints = parseConstraints(field.getConstraints());
                    FieldAuthorizationHintsDto authHints = buildFieldAuthorizationHints(
                            policiesByFieldId.getOrDefault(field.getId(), Collections.emptyList())
                    );
                    return FieldMetadataDto.fromEntity(field, constraints, authHints);
                })
                .collect(Collectors.toList());
    }

    /**
     * Parses the constraints JSON string into a Map.
     * 
     * @param constraintsJson The JSON string containing constraints
     * @return Map of constraint name to value, or null if no constraints
     * 
     * Validates: Requirement 8.2
     */
    private Map<String, Object> parseConstraints(String constraintsJson) {
        if (constraintsJson == null || constraintsJson.isBlank()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(constraintsJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse constraints JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Builds authorization hints for a collection based on its route policies.
     * 
     * @param routePolicies The list of route policies for the collection
     * @return AuthorizationHintsDto containing operation-to-policy mappings
     * 
     * Validates: Requirement 8.4
     */
    private AuthorizationHintsDto buildAuthorizationHints(List<RoutePolicy> routePolicies) {
        if (routePolicies.isEmpty()) {
            return new AuthorizationHintsDto(Collections.emptyMap());
        }

        // Group policies by operation
        Map<String, List<String>> operationPolicies = new HashMap<>();
        
        for (RoutePolicy routePolicy : routePolicies) {
            String operation = routePolicy.getOperation();
            String policyName = routePolicy.getPolicy().getName();
            
            operationPolicies.computeIfAbsent(operation, k -> new ArrayList<>()).add(policyName);
        }

        return new AuthorizationHintsDto(operationPolicies);
    }

    /**
     * Builds authorization hints for a field based on its field policies.
     * 
     * @param fieldPolicies The list of field policies for the field
     * @return FieldAuthorizationHintsDto containing operation-to-policy mappings
     * 
     * Validates: Requirement 8.4
     */
    private FieldAuthorizationHintsDto buildFieldAuthorizationHints(List<FieldPolicy> fieldPolicies) {
        if (fieldPolicies.isEmpty()) {
            return new FieldAuthorizationHintsDto(Collections.emptyMap());
        }

        // Group policies by operation
        Map<String, List<String>> operationPolicies = new HashMap<>();
        
        for (FieldPolicy fieldPolicy : fieldPolicies) {
            String operation = fieldPolicy.getOperation();
            String policyName = fieldPolicy.getPolicy().getName();
            
            operationPolicies.computeIfAbsent(operation, k -> new ArrayList<>()).add(policyName);
        }

        return new FieldAuthorizationHintsDto(operationPolicies);
    }
}
