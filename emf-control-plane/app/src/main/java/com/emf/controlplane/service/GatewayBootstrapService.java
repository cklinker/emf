package com.emf.controlplane.service;

import com.emf.controlplane.config.ControlPlaneProperties;
import com.emf.controlplane.dto.GatewayBootstrapConfigDto;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.CollectionAssignment;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.Worker;
import com.emf.controlplane.repository.CollectionAssignmentRepository;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for generating gateway bootstrap configuration.
 */
@org.springframework.stereotype.Service
public class GatewayBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(GatewayBootstrapService.class);

    private final CollectionRepository collectionRepository;
    private final CollectionAssignmentRepository assignmentRepository;
    private final WorkerRepository workerRepository;
    private final ControlPlaneProperties properties;

    public GatewayBootstrapService(CollectionRepository collectionRepository,
                                  CollectionAssignmentRepository assignmentRepository,
                                  WorkerRepository workerRepository,
                                  ControlPlaneProperties properties) {
        this.collectionRepository = collectionRepository;
        this.assignmentRepository = assignmentRepository;
        this.workerRepository = workerRepository;
        this.properties = properties;
    }

    /**
     * Gets the bootstrap configuration for the API Gateway.
     * This method is transactional to ensure lazy-loaded relationships are accessible.
     */
    @Transactional(readOnly = true)
    public GatewayBootstrapConfigDto getBootstrapConfig() {
        log.debug("Generating gateway bootstrap configuration");

        // Fetch all active collections with fields eagerly loaded
        List<Collection> collections = collectionRepository.findByActiveTrueWithFields();

        log.debug("Found {} active collections", collections.size());

        // Build a map of collectionId → workerBaseUrl from READY assignments
        Map<String, String> workerUrlByCollection = buildWorkerUrlMap();

        // Map collections to DTOs
        List<GatewayBootstrapConfigDto.CollectionDto> collectionDtos = collections.stream()
                .map(c -> mapCollectionToDto(c, workerUrlByCollection.get(c.getId())))
                .collect(Collectors.toList());

        // Create empty authorization config (to be implemented later)
        GatewayBootstrapConfigDto.AuthorizationDto authorization = new GatewayBootstrapConfigDto.AuthorizationDto(
                new ArrayList<>(),  // roles
                new ArrayList<>(),  // policies
                new ArrayList<>(),  // routePolicies
                new ArrayList<>()   // fieldPolicies
        );

        GatewayBootstrapConfigDto dto = new GatewayBootstrapConfigDto(
                collectionDtos,
                authorization
        );

        log.info("Generated gateway bootstrap config with {} collections", collectionDtos.size());

        return dto;
    }

    /**
     * Maps a Collection entity to a CollectionDto, including the worker base URL if available.
     */
    private GatewayBootstrapConfigDto.CollectionDto mapCollectionToDto(Collection collection, String workerBaseUrl) {
        // Map fields
        List<GatewayBootstrapConfigDto.FieldDto> fieldDtos = collection.getFields().stream()
                .filter(Field::isActive)
                .map(field -> new GatewayBootstrapConfigDto.FieldDto(field.getName(), field.getType()))
                .collect(Collectors.toList());

        // Use stored path if available, otherwise construct from default base path and collection name
        String path = collection.getPath();
        if (path == null || path.isEmpty()) {
            path = constructCollectionPath(collection);
        }

        return new GatewayBootstrapConfigDto.CollectionDto(
                collection.getId(),
                collection.getName(),
                path,
                workerBaseUrl,
                fieldDtos
        );
    }

    /**
     * Builds a map of collectionId → workerBaseUrl from READY assignments.
     * This allows the gateway to route requests directly to the worker hosting each collection.
     */
    private Map<String, String> buildWorkerUrlMap() {
        List<CollectionAssignment> readyAssignments = assignmentRepository.findByStatus("READY");

        // Build a set of worker IDs we need
        List<String> workerIds = readyAssignments.stream()
                .map(CollectionAssignment::getWorkerId)
                .distinct()
                .collect(Collectors.toList());

        // Batch-fetch workers
        Map<String, String> workerBaseUrls = workerRepository.findAllById(workerIds).stream()
                .collect(Collectors.toMap(Worker::getId, Worker::getBaseUrl));

        // Map collectionId → workerBaseUrl
        return readyAssignments.stream()
                .filter(a -> workerBaseUrls.containsKey(a.getWorkerId()))
                .collect(Collectors.toMap(
                        CollectionAssignment::getCollectionId,
                        a -> workerBaseUrls.get(a.getWorkerId()),
                        (url1, url2) -> url1  // If multiple assignments, use the first
                ));
    }

    /**
     * Constructs the path for a collection.
     */
    private String constructCollectionPath(Collection collection) {
        return "/api/" + collection.getName();
    }
}
