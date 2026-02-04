package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateServiceRequest;
import com.emf.controlplane.dto.ServiceDto;
import com.emf.controlplane.dto.UpdateServiceRequest;
import com.emf.controlplane.entity.Service;
import com.emf.controlplane.service.ServiceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing domain services.
 * Provides endpoints for CRUD operations on services.
 */
@RestController
@RequestMapping("/control/services")
public class ServiceController {

    private final ServiceService serviceService;

    public ServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    /**
     * Lists all active services with pagination and filtering.
     *
     * @param filter Optional filter string to search by name or description
     * @param sort Optional sort criteria
     * @param pageable Pagination parameters
     * @return Page of services
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ServiceDto>> listServices(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String sort,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<Service> services = serviceService.listServices(filter, sort, pageable);
        Page<ServiceDto> serviceDtos = services.map(ServiceDto::fromEntity);
        
        return ResponseEntity.ok(serviceDtos);
    }

    /**
     * Creates a new service.
     *
     * @param request The service creation request
     * @return The created service
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceDto> createService(@Valid @RequestBody CreateServiceRequest request) {
        Service service = serviceService.createService(request);
        ServiceDto serviceDto = ServiceDto.fromEntity(service);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceDto);
    }

    /**
     * Retrieves a service by ID.
     *
     * @param id The service ID
     * @return The service
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceDto> getService(@PathVariable String id) {
        Service service = serviceService.getService(id);
        ServiceDto serviceDto = ServiceDto.fromEntity(service);
        
        return ResponseEntity.ok(serviceDto);
    }

    /**
     * Updates an existing service.
     *
     * @param id The service ID
     * @param request The update request
     * @return The updated service
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceDto> updateService(
            @PathVariable String id,
            @Valid @RequestBody UpdateServiceRequest request) {
        
        Service service = serviceService.updateService(id, request);
        ServiceDto serviceDto = ServiceDto.fromEntity(service);
        
        return ResponseEntity.ok(serviceDto);
    }

    /**
     * Soft-deletes a service by marking it as inactive.
     *
     * @param id The service ID
     * @return No content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteService(@PathVariable String id) {
        serviceService.deleteService(id);
        return ResponseEntity.noContent().build();
    }
}
