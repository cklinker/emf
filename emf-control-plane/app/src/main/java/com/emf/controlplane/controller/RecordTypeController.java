package com.emf.controlplane.controller;

import com.emf.controlplane.dto.*;
import com.emf.controlplane.entity.RecordType;
import com.emf.controlplane.entity.RecordTypePicklist;
import com.emf.controlplane.service.RecordTypeService;
import com.emf.controlplane.tenant.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/control/collections/{collectionId}/record-types")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Record Types", description = "Record type management APIs")
public class RecordTypeController {

    private static final Logger log = LoggerFactory.getLogger(RecordTypeController.class);

    private final RecordTypeService recordTypeService;

    public RecordTypeController(RecordTypeService recordTypeService) {
        this.recordTypeService = recordTypeService;
    }

    @GetMapping
    @Operation(summary = "List record types for a collection")
    public ResponseEntity<List<RecordTypeDto>> listRecordTypes(
            @PathVariable String collectionId) {
        List<RecordType> recordTypes = recordTypeService.listRecordTypes(collectionId);
        List<RecordTypeDto> dtos = recordTypes.stream()
                .map(RecordTypeDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Create a record type")
    public ResponseEntity<RecordTypeDto> createRecordType(
            @PathVariable String collectionId,
            @Valid @RequestBody CreateRecordTypeRequest request) {
        log.info("REST request to create record type: {}", request.getName());
        String tenantId = TenantContextHolder.requireTenantId();
        RecordType created = recordTypeService.createRecordType(collectionId, tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RecordTypeDto.fromEntity(created));
    }

    @GetMapping("/{recordTypeId}")
    @Operation(summary = "Get a record type")
    public ResponseEntity<RecordTypeDto> getRecordType(
            @PathVariable String collectionId,
            @PathVariable String recordTypeId) {
        RecordType recordType = recordTypeService.getRecordType(recordTypeId);
        return ResponseEntity.ok(RecordTypeDto.fromEntity(recordType));
    }

    @PutMapping("/{recordTypeId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Update a record type")
    public ResponseEntity<RecordTypeDto> updateRecordType(
            @PathVariable String collectionId,
            @PathVariable String recordTypeId,
            @Valid @RequestBody UpdateRecordTypeRequest request) {
        log.info("REST request to update record type: {}", recordTypeId);
        RecordType updated = recordTypeService.updateRecordType(recordTypeId, request);
        return ResponseEntity.ok(RecordTypeDto.fromEntity(updated));
    }

    @DeleteMapping("/{recordTypeId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Delete a record type")
    public ResponseEntity<Void> deleteRecordType(
            @PathVariable String collectionId,
            @PathVariable String recordTypeId) {
        log.info("REST request to delete record type: {}", recordTypeId);
        recordTypeService.deleteRecordType(recordTypeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{recordTypeId}/picklists")
    @Operation(summary = "Get picklist overrides for a record type")
    public ResponseEntity<List<RecordTypePicklistDto>> getPicklistOverrides(
            @PathVariable String collectionId,
            @PathVariable String recordTypeId) {
        List<RecordTypePicklist> overrides = recordTypeService.getPicklistOverrides(recordTypeId);
        List<RecordTypePicklistDto> dtos = overrides.stream()
                .map(RecordTypePicklistDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{recordTypeId}/picklists/{fieldId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Set picklist override for a record type")
    public ResponseEntity<RecordTypePicklistDto> setPicklistOverride(
            @PathVariable String collectionId,
            @PathVariable String recordTypeId,
            @PathVariable String fieldId,
            @Valid @RequestBody SetPicklistOverrideRequest request) {
        log.info("REST request to set picklist override: recordType={}, field={}", recordTypeId, fieldId);
        RecordTypePicklist override = recordTypeService.setPicklistOverride(recordTypeId, fieldId, request);
        return ResponseEntity.ok(RecordTypePicklistDto.fromEntity(override));
    }

    @DeleteMapping("/{recordTypeId}/picklists/{fieldId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Remove picklist override for a record type")
    public ResponseEntity<Void> removePicklistOverride(
            @PathVariable String collectionId,
            @PathVariable String recordTypeId,
            @PathVariable String fieldId) {
        log.info("REST request to remove picklist override: recordType={}, field={}", recordTypeId, fieldId);
        recordTypeService.removePicklistOverride(recordTypeId, fieldId);
        return ResponseEntity.noContent().build();
    }
}
