package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateServiceRequest;
import com.emf.controlplane.dto.UpdateServiceRequest;
import com.emf.controlplane.entity.Service;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.runtime.event.ChangeType;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.ServiceRepository;
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
     * Only returns active services.
     */
    @Transactional(readOnly = true)
    public Page<Service> listServices(String filter, String sort, Pageable pageable) {
        log.debug("Listing services with filter: {}, sort: {}, pageable: {}", filter, sort, pageable);
        
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
        log.info("Creating service with name: {}", request.getName());
        
        // Check for duplicate name
        if (serviceRepository.existsByNameAndActiveTrue(request.getName())) {
            throw new DuplicateResourceException("Service", "name", request.getName());
        }
        
        // Create the service entity
        Service service = new Service(request.getName(), request.getDescription());
        service.setDisplayName(request.getDisplayName() != null ? request.getDisplayName() : request.getName());
        service.setBasePath(request.getBasePath() != null ? request.getBasePath() : "/api");
        service.setEnvironment(request.getEnvironment());
        service.setDatabaseUrl(request.getDatabaseUrl());
        service.setActive(true);
        
        // Save the service
        service = serviceRepository.save(service);
        
        // Publish event
        publishServiceChangedEvent(service, ChangeType.CREATED);
        
        log.info("Created service with id: {}", service.getId());
        return service;
    }

    /**
     * Retrieves a service by its ID.
     * Only returns active services.
     */
    @Transactional(readOnly = true)
    public Service getService(String id) {
        log.debug("Fetching service: {}", id);
        
        return serviceRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", id));
    }

    /**
     * Updates an existing service.
     */
    @Transactional
    public Service updateService(String id, UpdateServiceRequest request) {
        log.info("Updating service with id: {}", id);
        
        Service service = serviceRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", id));
        
        // Check for duplicate name if name is being changed
        if (request.getName() != null && !request.getName().equals(service.getName())) {
            if (serviceRepository.existsByNameAndActiveTrue(request.getName())) {
                throw new DuplicateResourceException("Service", "name", request.getName());
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
     * The service is preserved in the database.
     */
    @Transactional
    public void deleteService(String id) {
        log.info("Deleting service with id: {}", id);
        
        Service service = serviceRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", id));
        
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
