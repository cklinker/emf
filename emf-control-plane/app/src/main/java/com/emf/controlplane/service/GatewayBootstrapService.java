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

        // Fetch ALL active collections including system ones
        List<Collection> allCollections = collectionRepository.findAllActiveWithFields();

        log.debug("Found {} active collections (including system)", allCollections.size());

        // Build a map of collectionId -> workerBaseUrl from READY assignments
        Map<String, String> workerUrlByCollection = buildWorkerUrlMap();

        // Map only non-system collections to route DTOs (system collections like control-plane
        // are handled by RouteInitializer, not dynamic route creation)
        List<GatewayBootstrapConfigDto.CollectionDto> collectionDtos = allCollections.stream()
                .filter(c -> !c.isSystemCollection())
                .map(c -> mapCollectionToDto(c, workerUrlByCollection.get(c.getId())))
                .collect(Collectors.toList());

        GatewayBootstrapConfigDto dto = new GatewayBootstrapConfigDto(collectionDtos);

        log.info("Generated gateway bootstrap config with {} collections", collectionDtos.size());

        return dto;
    }

    /**
     * Maps a Collection entity to a CollectionDto, including the worker base URL if available.
     */
    private GatewayBootstrapConfigDto.CollectionDto mapCollectionToDto(Collection collection, String workerBaseUrl) {
        List<GatewayBootstrapConfigDto.FieldDto> fieldDtos = collection.getFields().stream()
                .filter(Field::isActive)
                .map(field -> new GatewayBootstrapConfigDto.FieldDto(field.getName(), field.getType()))
                .collect(Collectors.toList());

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

    private Map<String, String> buildWorkerUrlMap() {
        List<CollectionAssignment> readyAssignments = assignmentRepository.findByStatus("READY");

        List<String> workerIds = readyAssignments.stream()
                .map(CollectionAssignment::getWorkerId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, String> workerBaseUrls = workerRepository.findAllById(workerIds).stream()
                .collect(Collectors.toMap(Worker::getId, Worker::getBaseUrl));

        return readyAssignments.stream()
                .filter(a -> workerBaseUrls.containsKey(a.getWorkerId()))
                .collect(Collectors.toMap(
                        CollectionAssignment::getCollectionId,
                        a -> workerBaseUrls.get(a.getWorkerId()),
                        (url1, url2) -> url1
                ));
    }

    private String constructCollectionPath(Collection collection) {
        return "/api/" + collection.getName();
    }
}
