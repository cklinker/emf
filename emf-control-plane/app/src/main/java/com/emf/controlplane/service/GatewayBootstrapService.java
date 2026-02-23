package com.emf.controlplane.service;

import com.emf.controlplane.config.CacheConfig;
import com.emf.controlplane.config.ControlPlaneProperties;
import com.emf.controlplane.dto.GovernorLimits;
import com.emf.controlplane.dto.GatewayBootstrapConfigDto;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.Tenant;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for generating gateway bootstrap configuration.
 *
 * <p>All workers serve all collections, so the gateway routes to the K8s worker
 * service URL (configured via {@code emf.control-plane.worker-service-url})
 * for every collection. There is no per-collection worker assignment lookup.
 */
@org.springframework.stereotype.Service
public class GatewayBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(GatewayBootstrapService.class);

    private final CollectionRepository collectionRepository;
    private final TenantRepository tenantRepository;
    private final TenantService tenantService;
    private final ControlPlaneProperties properties;

    public GatewayBootstrapService(CollectionRepository collectionRepository,
                                  TenantRepository tenantRepository,
                                  TenantService tenantService,
                                  ControlPlaneProperties properties) {
        this.collectionRepository = collectionRepository;
        this.tenantRepository = tenantRepository;
        this.tenantService = tenantService;
        this.properties = properties;
    }

    /**
     * Gets the bootstrap configuration for the API Gateway.
     * Cached with event-driven invalidation via {@code SystemCollectionCacheListener}
     * when collections or tenant records change.
     */
    @Cacheable(value = CacheConfig.BOOTSTRAP_CACHE, key = "'gateway-bootstrap'")
    @Transactional(readOnly = true)
    public GatewayBootstrapConfigDto getBootstrapConfig() {
        log.debug("Generating gateway bootstrap configuration");

        // Fetch ALL active collections including system ones
        List<Collection> allCollections = collectionRepository.findAllActiveWithFields();

        log.debug("Found {} active collections (including system)", allCollections.size());

        // All workers serve all collections — use the configured K8s worker service URL
        String workerBaseUrl = properties.getWorkerServiceUrl();

        // Map all active collections to route DTOs.
        // System collections (users, profiles, etc.) are included so the gateway creates
        // routes for them, pointing to the worker. The __control-plane collection is
        // excluded since it has a static route managed by RouteInitializer.
        List<GatewayBootstrapConfigDto.CollectionDto> collectionDtos = allCollections.stream()
                .filter(c -> !"__control-plane".equals(c.getName()))
                .map(c -> mapCollectionToDto(c, workerBaseUrl))
                .collect(Collectors.toList());

        // Build per-tenant governor limits for gateway rate limiting
        Map<String, GatewayBootstrapConfigDto.GovernorLimitDto> governorLimits = buildGovernorLimitsMap();

        GatewayBootstrapConfigDto dto = new GatewayBootstrapConfigDto(collectionDtos, governorLimits);

        log.info("Generated gateway bootstrap config with {} collections and {} tenant governor limits",
                collectionDtos.size(), governorLimits.size());

        return dto;
    }

    /**
     * Maps a Collection entity to a CollectionDto, including the worker base URL.
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
                fieldDtos,
                collection.isSystemCollection()
        );
    }

    /**
     * Builds a map of tenant ID -> governor limit DTO for all active tenants.
     * The gateway uses this to apply per-tenant rate limiting based on apiCallsPerDay.
     */
    private Map<String, GatewayBootstrapConfigDto.GovernorLimitDto> buildGovernorLimitsMap() {
        List<Tenant> activeTenants = tenantRepository.findByStatus("ACTIVE");
        Map<String, GatewayBootstrapConfigDto.GovernorLimitDto> limitsMap = new HashMap<>();

        for (Tenant tenant : activeTenants) {
            try {
                GovernorLimits limits = tenantService.getGovernorLimits(tenant.getId());
                limitsMap.put(tenant.getId(),
                        new GatewayBootstrapConfigDto.GovernorLimitDto(limits.apiCallsPerDay()));
            } catch (Exception e) {
                log.warn("Failed to get governor limits for tenant {}, using defaults", tenant.getId(), e);
                limitsMap.put(tenant.getId(),
                        new GatewayBootstrapConfigDto.GovernorLimitDto(GovernorLimits.defaults().apiCallsPerDay()));
            }
        }

        return limitsMap;
    }

    private String constructCollectionPath(Collection collection) {
        return "/api/" + collection.getName();
    }
}
