package com.emf.controlplane.service;

import com.emf.controlplane.dto.AuthorizationHintsDto;
import com.emf.controlplane.dto.FieldAuthorizationHintsDto;
import com.emf.controlplane.dto.FieldMetadataDto;
import com.emf.controlplane.dto.ResourceDiscoveryDto;
import com.emf.controlplane.dto.ResourceMetadataDto;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for resource discovery.
 * Provides metadata about all active collections including their schemas
 * and available operations.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>8.1: Return all active collections with their schemas</li>
 *   <li>8.2: Include field definitions, types, and constraints for each collection</li>
 *   <li>8.3: Include available operations for each collection</li>
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
    private final ObjectMapper objectMapper;

    public DiscoveryService(
            CollectionRepository collectionRepository,
            FieldRepository fieldRepository,
            ObjectMapper objectMapper) {
        this.collectionRepository = collectionRepository;
        this.fieldRepository = fieldRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Discovers all active resources (collections) with their complete metadata.
     * Returns information about schemas and available operations.
     *
     * @return ResourceDiscoveryDto containing metadata for all active collections
     *
     * Validates: Requirements 8.1, 8.2, 8.3
     */
    @Transactional(readOnly = true)
    public ResourceDiscoveryDto discoverResources() {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Discovering all active resources for tenant: {}", tenantId);

        List<Collection> activeCollections;
        if (tenantId != null) {
            activeCollections = collectionRepository.findByTenantIdAndActiveTrue(tenantId);
        } else {
            activeCollections = collectionRepository.findByActiveTrue();
        }

        List<ResourceMetadataDto> resources = activeCollections.stream()
                .map(this::buildResourceMetadata)
                .collect(Collectors.toList());

        log.info("Discovered {} active resources", resources.size());
        return new ResourceDiscoveryDto(resources);
    }

    /**
     * Builds complete metadata for a single collection.
     */
    private ResourceMetadataDto buildResourceMetadata(Collection collection) {
        log.debug("Building metadata for collection: {}", collection.getName());

        List<Field> activeFields = fieldRepository.findByCollectionIdAndActiveTrue(collection.getId());

        List<FieldMetadataDto> fieldMetadata = buildFieldMetadata(activeFields);

        // No authorization hints — authorization is handled at the gateway level
        AuthorizationHintsDto authorizationHints = new AuthorizationHintsDto(Collections.emptyMap());

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
     * Builds metadata for all fields in a collection.
     */
    private List<FieldMetadataDto> buildFieldMetadata(List<Field> fields) {
        return fields.stream()
                .map(field -> {
                    Map<String, Object> constraints = parseConstraints(field.getConstraints());
                    FieldAuthorizationHintsDto authHints = new FieldAuthorizationHintsDto(Collections.emptyMap());
                    return FieldMetadataDto.fromEntity(field, constraints, authHints);
                })
                .collect(Collectors.toList());
    }

    /**
     * Parses the constraints JSON string into a Map.
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
}
