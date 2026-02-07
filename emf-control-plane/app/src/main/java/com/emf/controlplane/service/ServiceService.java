package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateServiceRequest;
import com.emf.controlplane.dto.UpdateServiceRequest;
import com.emf.controlplane.entity.Service;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.runtime.event.ChangeType;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.ServiceRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing domain service definitions.
 * Handles CRUD operations with event publishing.
 */
@Component
public class ServiceService {

    private static final Logger log = LoggerFactory.getLogger(ServiceService.class);

    private final ServiceRepository serviceRepository;
    private final ConfigEventPublisher eventPublisher;

    public ServiceService(
            ServiceRepository serviceRepository,
            @Nullable ConfigEventPublisher eventPublisher) {
        this.serviceRepository = serviceRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Lists services with pagination, filtering, and sorting support.
     * Only returns active services for the current tenant.
     */
    @Transactional(readOnly = true)
    public Page<Service> listServices(String filter, String sort, Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Listing services for tenant: {} with filter: {}", tenantId, filter);

        if (tenantId != null) {
            if (filter != null && !filter.isBlank()) {
                return serviceRepository.findByTenantIdAndActiveAndSearchTerm(tenantId, filter.trim(), pageable);
            }
            return serviceRepository.findByTenantIdAndActiveTrue(tenantId, pageable);
        }

        // Fallback for platform-level operations without tenant context
        if (filter != null && !filter.isBlank()) {
            return serviceRepository.findByActiveAndSearchTerm(filter.trim(), pageable);
        }
        return serviceRepository.findByActiveTrue(pageable);
    }

    /**
     * Creates a new service with the given request data.
     */
    @Transactional
    public Service createService(CreateServiceRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Creating service with name: {} for tenant: {}", request.getName(), tenantId);

        // Check for duplicate name within tenant
        if (tenantId != null) {
            if (serviceRepository.existsByTenantIdAndNameAndActiveTrue(tenantId, request.getName())) {
                throw new DuplicateResourceException("Service", "name", request.getName());
            }
        } else {
            if (serviceRepository.existsByNameAndActiveTrue(request.getName())) {
                throw new DuplicateResourceException("Service", "name", request.getName());
            }
        }

        // Create the service entity
        Service service = new Service(request.getName(), request.getDescription());
        service.setDisplayName(request.getDisplayName() != null ? request.getDisplayName() : request.getName());
        service.setBasePath(request.getBasePath() != null ? request.getBasePath() : "/api");
        service.setEnvironment(request.getEnvironment());
        service.setDatabaseUrl(request.getDatabaseUrl());
        service.setActive(true);

        if (tenantId != null) {
            service.setTenantId(tenantId);
        }

        // Save the service
        service = serviceRepository.save(service);

        // Publish event
        publishServiceChangedEvent(service, ChangeType.CREATED);

        log.info("Created service with id: {}", service.getId());
        return service;
    }

    /**
     * Retrieves a service by its ID.
     * Only returns active services within the current tenant.
     */
    @Transactional(readOnly = true)
    public Service getService(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Fetching service: {} for tenant: {}", id, tenantId);

        if (tenantId != null) {
            return serviceRepository.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service", id));
        }
        return serviceRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", id));
    }

    /**
     * Updates an existing service.
     */
    @Transactional
    public Service updateService(String id, UpdateServiceRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Updating service with id: {} for tenant: {}", id, tenantId);

        Service service;
        if (tenantId != null) {
            service = serviceRepository.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service", id));
        } else {
            service = serviceRepository.findByIdAndActiveTrue(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Service", id));
        }

        // Check for duplicate name if name is being changed
        if (request.getName() != null && !request.getName().equals(service.getName())) {
            if (tenantId != null) {
                if (serviceRepository.existsByTenantIdAndNameAndActiveTrue(tenantId, request.getName())) {
                    throw new DuplicateResourceException("Service", "name", request.getName());
                }
            } else {
                if (serviceRepository.existsByNameAndActiveTrue(request.getName())) {
                    throw new DuplicateResourceException("Service", "name", request.getName());
                }
            }
            service.setName(request.getName());
        }

        // Update fields if provided
        if (request.getDisplayName() != null) {
            service.setDisplayName(request.getDisplayName());
        }
        if (request.getDescription() != null) {
            service.setDescription(request.getDescription());
        }
        if (request.getBasePath() != null) {
            service.setBasePath(request.getBasePath());
        }
        if (request.getEnvironment() != null) {
            service.setEnvironment(request.getEnvironment());
        }
        if (request.getDatabaseUrl() != null) {
            service.setDatabaseUrl(request.getDatabaseUrl());
        }

        // Save the updated service
        service = serviceRepository.save(service);

        // Publish event
        publishServiceChangedEvent(service, ChangeType.UPDATED);

        log.info("Updated service with id: {}", id);
        return service;
    }

    /**
     * Soft-deletes a service by marking it as inactive.
     */
    @Transactional
    public void deleteService(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Deleting service with id: {} for tenant: {}", id, tenantId);

        Service service;
        if (tenantId != null) {
            service = serviceRepository.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service", id));
        } else {
            service = serviceRepository.findByIdAndActiveTrue(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Service", id));
        }

        // Soft delete - mark as inactive
        service.setActive(false);
        serviceRepository.save(service);

        // Publish event
        publishServiceChangedEvent(service, ChangeType.DELETED);

        log.info("Soft-deleted service with id: {}", id);
    }

    /**
     * Publishes a service changed event to Kafka.
     */
    private void publishServiceChangedEvent(Service service, ChangeType changeType) {
        if (eventPublisher != null) {
            eventPublisher.publishServiceChanged(service, changeType);
        } else {
            log.debug("Event publishing disabled - service changed: {}", service.getId());
        }
    }
}
