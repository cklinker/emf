package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateTenantRequest;
import com.emf.controlplane.dto.GovernorLimits;
import com.emf.controlplane.dto.UpdateTenantRequest;
import com.emf.controlplane.entity.Tenant;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.TenantRepository;
import com.emf.controlplane.tenant.TenantSchemaManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * Service for managing tenant lifecycle operations.
 * Handles CRUD, status transitions, and governor limits.
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private static final Set<String> VALID_EDITIONS = Set.of("FREE", "PROFESSIONAL", "ENTERPRISE", "UNLIMITED");

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;
    private final TenantSchemaManager tenantSchemaManager;

    public TenantService(
            TenantRepository tenantRepository,
            ObjectMapper objectMapper,
            @Nullable TenantSchemaManager tenantSchemaManager) {
        this.tenantRepository = tenantRepository;
        this.objectMapper = objectMapper;
        this.tenantSchemaManager = tenantSchemaManager;
    }

    /**
     * Creates a new tenant.
     * Validates slug uniqueness, sets initial status to PROVISIONING,
     * then transitions to ACTIVE.
     *
     * @param request The tenant creation request
     * @return The created tenant
     * @throws DuplicateResourceException if slug already exists
     */
    @Transactional
    public Tenant createTenant(CreateTenantRequest request) {
        log.info("Creating tenant with slug: {}", request.getSlug());

        if (tenantRepository.existsBySlug(request.getSlug())) {
            throw new DuplicateResourceException("Tenant", "slug", request.getSlug());
        }

        Tenant tenant = new Tenant(request.getSlug(), request.getName());

        if (request.getEdition() != null) {
            validateEdition(request.getEdition());
            tenant.setEdition(request.getEdition());
        }

        if (request.getSettings() != null) {
            tenant.setSettings(toJson(request.getSettings()));
        }

        if (request.getLimits() != null) {
            tenant.setLimits(toJson(request.getLimits()));
        }

        // Status starts as PROVISIONING (entity default)
        tenant = tenantRepository.save(tenant);

        // Provision default data for the new tenant
        if (tenantSchemaManager != null) {
            tenantSchemaManager.provisionTenant(tenant.getId(), tenant.getSlug());
        }

        // Transition to ACTIVE after provisioning
        tenant.setStatus("ACTIVE");
        tenant = tenantRepository.save(tenant);

        log.info("Created tenant with id: {}, slug: {}", tenant.getId(), tenant.getSlug());
        return tenant;
    }

    /**
     * Retrieves a tenant by ID.
     *
     * @param id The tenant ID
     * @return The tenant
     * @throws ResourceNotFoundException if the tenant does not exist
     */
    @Transactional(readOnly = true)
    public Tenant getTenant(String id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));
    }

    /**
     * Retrieves a tenant by slug.
     *
     * @param slug The tenant slug
     * @return The tenant
     * @throws ResourceNotFoundException if the tenant does not exist
     */
    @Transactional(readOnly = true)
    public Tenant getTenantBySlug(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant with slug: " + slug));
    }

    /**
     * Lists tenants with pagination.
     *
     * @param pageable Pagination parameters
     * @return Page of tenants
     */
    @Transactional(readOnly = true)
    public Page<Tenant> listTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable);
    }

    /**
     * Updates an existing tenant.
     *
     * @param id The tenant ID
     * @param request The update request
     * @return The updated tenant
     * @throws ResourceNotFoundException if the tenant does not exist
     */
    @Transactional
    public Tenant updateTenant(String id, UpdateTenantRequest request) {
        log.info("Updating tenant with id: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));

        if (request.getName() != null) {
            tenant.setName(request.getName());
        }

        if (request.getEdition() != null) {
            validateEdition(request.getEdition());
            tenant.setEdition(request.getEdition());
        }

        if (request.getSettings() != null) {
            tenant.setSettings(toJson(request.getSettings()));
        }

        if (request.getLimits() != null) {
            tenant.setLimits(toJson(request.getLimits()));
        }

        tenant = tenantRepository.save(tenant);

        log.info("Updated tenant with id: {}", id);
        return tenant;
    }

    /**
     * Suspends a tenant by setting status to SUSPENDED.
     *
     * @param id The tenant ID
     * @throws ResourceNotFoundException if the tenant does not exist
     */
    @Transactional
    public void suspendTenant(String id) {
        log.info("Suspending tenant with id: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));

        tenant.setStatus("SUSPENDED");
        tenantRepository.save(tenant);

        log.info("Suspended tenant with id: {}", id);
    }

    /**
     * Activates a tenant by setting status to ACTIVE.
     *
     * @param id The tenant ID
     * @throws ResourceNotFoundException if the tenant does not exist
     */
    @Transactional
    public void activateTenant(String id) {
        log.info("Activating tenant with id: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));

        tenant.setStatus("ACTIVE");
        tenantRepository.save(tenant);

        log.info("Activated tenant with id: {}", id);
    }

    /**
     * Parses the tenant's limits JSONB into GovernorLimits.
     *
     * @param tenantId The tenant ID
     * @return The parsed governor limits, or defaults if not set
     * @throws ResourceNotFoundException if the tenant does not exist
     */
    @Transactional(readOnly = true)
    public GovernorLimits getGovernorLimits(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        String limitsJson = tenant.getLimits();
        if (limitsJson == null || limitsJson.equals("{}")) {
            return GovernorLimits.defaults();
        }

        try {
            Map<String, Object> limitsMap = objectMapper.readValue(limitsJson, Map.class);
            return new GovernorLimits(
                    getIntOrDefault(limitsMap, "api_calls_per_day", 100_000),
                    getIntOrDefault(limitsMap, "storage_gb", 10),
                    getIntOrDefault(limitsMap, "max_users", 100),
                    getIntOrDefault(limitsMap, "max_collections", 200),
                    getIntOrDefault(limitsMap, "max_fields_per_collection", 500),
                    getIntOrDefault(limitsMap, "max_workflows", 50),
                    getIntOrDefault(limitsMap, "max_reports", 200)
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse governor limits for tenant {}, returning defaults", tenantId, e);
            return GovernorLimits.defaults();
        }
    }

    /**
     * Updates the governor limits for a tenant.
     *
     * @param tenantId The tenant ID
     * @param limits The new governor limits
     * @return The updated governor limits
     * @throws ResourceNotFoundException if the tenant does not exist
     */
    @Transactional
    public GovernorLimits updateGovernorLimits(String tenantId, GovernorLimits limits) {
        log.info("Updating governor limits for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Map<String, Object> limitsMap = Map.of(
                "api_calls_per_day", limits.apiCallsPerDay(),
                "storage_gb", limits.storageGb(),
                "max_users", limits.maxUsers(),
                "max_collections", limits.maxCollections(),
                "max_fields_per_collection", limits.maxFieldsPerCollection(),
                "max_workflows", limits.maxWorkflows(),
                "max_reports", limits.maxReports()
        );

        tenant.setLimits(toJson(limitsMap));
        tenantRepository.save(tenant);

        log.info("Updated governor limits for tenant: {}", tenantId);
        return limits;
    }

    private void validateEdition(String edition) {
        if (!VALID_EDITIONS.contains(edition)) {
            throw new IllegalArgumentException("Invalid edition: " + edition + ". Must be one of: " + VALID_EDITIONS);
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON content", e);
        }
    }

    private int getIntOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }
}
