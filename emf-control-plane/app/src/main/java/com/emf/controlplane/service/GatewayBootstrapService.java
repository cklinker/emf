package com.emf.controlplane.service;

import com.emf.controlplane.config.ControlPlaneProperties;
import com.emf.controlplane.dto.GatewayBootstrapConfigDto;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating gateway bootstrap configuration.
 */
@org.springframework.stereotype.Service
public class GatewayBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(GatewayBootstrapService.class);

    private final ServiceRepository serviceRepository;
    private final CollectionRepository collectionRepository;
    private final ControlPlaneProperties properties;

    public GatewayBootstrapService(ServiceRepository serviceRepository,
                                  CollectionRepository collectionRepository,
                                  ControlPlaneProperties properties) {
        this.serviceRepository = serviceRepository;
        this.collectionRepository = collectionRepository;
        this.properties = properties;
    }

    /**
     * Gets the bootstrap configuration for the API Gateway.
     * This method is transactional to ensure lazy-loaded relationships are accessible.
     */
    @Transactional(readOnly = true)
    public GatewayBootstrapConfigDto getBootstrapConfig() {
        log.debug("Generating gateway bootstrap configuration");

        // Fetch all active services
        List<com.emf.controlplane.entity.Service> services = serviceRepository.findAll().stream()
                .filter(com.emf.controlplane.entity.Service::isActive)
                .collect(Collectors.toList());

        log.debug("Found {} active services", services.size());

        // Fetch all active collections with fields eagerly loaded
        List<Collection> collections = collectionRepository.findByActiveTrueWithFields();

        log.debug("Found {} active collections", collections.size());

        // Map services to DTOs
        List<GatewayBootstrapConfigDto.ServiceDto> serviceDtos = services.stream()
                .map(this::mapServiceToDto)
                .collect(Collectors.toList());

        // Map collections to DTOs
        List<GatewayBootstrapConfigDto.CollectionDto> collectionDtos = collections.stream()
                .map(this::mapCollectionToDto)
                .collect(Collectors.toList());

        // Create empty authorization config (to be implemented later)
        GatewayBootstrapConfigDto.AuthorizationDto authorization = new GatewayBootstrapConfigDto.AuthorizationDto(
                new ArrayList<>(),  // roles
                new ArrayList<>(),  // policies
                new ArrayList<>(),  // routePolicies
                new ArrayList<>()   // fieldPolicies
        );

        GatewayBootstrapConfigDto dto = new GatewayBootstrapConfigDto(
                serviceDtos,
                collectionDtos,
                authorization
        );

        log.info("Generated gateway bootstrap config with {} services and {} collections",
                serviceDtos.size(), collectionDtos.size());

        return dto;
    }

    /**
     * Maps a Service entity to a ServiceDto.
     */
    private GatewayBootstrapConfigDto.ServiceDto mapServiceToDto(com.emf.controlplane.entity.Service service) {
        String baseUrl = constructServiceBaseUrl(service.getName());
        return new GatewayBootstrapConfigDto.ServiceDto(
                service.getId(),
                service.getName(),
                baseUrl
        );
    }

    /**
     * Maps a Collection entity to a CollectionDto.
     */
    private GatewayBootstrapConfigDto.CollectionDto mapCollectionToDto(Collection collection) {
        // Map fields
        List<GatewayBootstrapConfigDto.FieldDto> fieldDtos = collection.getFields().stream()
                .filter(Field::isActive)
                .map(field -> new GatewayBootstrapConfigDto.FieldDto(field.getName(), field.getType()))
                .collect(Collectors.toList());

        // Use stored path if available, otherwise construct from service base path and collection name
        String path = collection.getPath();
        if (path == null || path.isEmpty()) {
            path = constructCollectionPath(collection);
        }

        return new GatewayBootstrapConfigDto.CollectionDto(
                collection.getId(),
                collection.getName(),
                collection.getService().getId(),
                path,
                fieldDtos
        );
    }

    /**
     * Returns the worker service URL for routing data API requests.
     * All collection data is served by the worker service.
     */
    private String constructServiceBaseUrl(String serviceName) {
        return properties.getWorkerServiceUrl();
    }

    /**
     * Constructs the path for a collection.
     */
    private String constructCollectionPath(Collection collection) {
        String basePath = collection.getService().getBasePath();
        if (basePath == null || basePath.isEmpty()) {
            basePath = "/api";
        }

        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }

        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }

        return basePath + "/" + collection.getName();
    }
}
