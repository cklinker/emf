package com.emf.controlplane.controller;

import com.emf.controlplane.dto.FieldHistoryDto;
import com.emf.controlplane.service.FieldHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for querying field-level change history.
 */
@RestController
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Field History", description = "Query field-level change history")
public class FieldHistoryController {

    private final FieldHistoryService fieldHistoryService;

    public FieldHistoryController(FieldHistoryService fieldHistoryService) {
        this.fieldHistoryService = fieldHistoryService;
    }

    @GetMapping("/control/collections/{collectionId}/records/{recordId}/history")
    @Operation(summary = "Get record history", description = "Get all field changes for a record")
    public Page<FieldHistoryDto> getRecordHistory(
            @PathVariable String collectionId,
            @PathVariable String recordId,
            @PageableDefault(size = 50, sort = "changedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return fieldHistoryService.getRecordHistory(collectionId, recordId, pageable)
                .map(FieldHistoryDto::fromEntity);
    }

    @GetMapping("/control/collections/{collectionId}/records/{recordId}/history/{fieldName}")
    @Operation(summary = "Get field history for record", description = "Get changes for a specific field on a record")
    public Page<FieldHistoryDto> getFieldHistory(
            @PathVariable String collectionId,
            @PathVariable String recordId,
            @PathVariable String fieldName,
            @PageableDefault(size = 50, sort = "changedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return fieldHistoryService.getFieldHistory(collectionId, recordId, fieldName, pageable)
                .map(FieldHistoryDto::fromEntity);
    }

    @GetMapping("/control/collections/{collectionId}/field-history/{fieldName}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('VIEW_ALL_DATA')")
    @Operation(summary = "Get field history across records", description = "Admin: view all changes to a field across all records")
    public Page<FieldHistoryDto> getFieldHistoryAcrossRecords(
            @PathVariable String collectionId,
            @PathVariable String fieldName,
            @PageableDefault(size = 50, sort = "changedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return fieldHistoryService.getFieldHistoryAcrossRecords(collectionId, fieldName, pageable)
                .map(FieldHistoryDto::fromEntity);
    }

    @GetMapping("/control/users/{userId}/field-history")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('VIEW_ALL_DATA')")
    @Operation(summary = "Get user field history", description = "Admin: view all field changes made by a user")
    public Page<FieldHistoryDto> getUserHistory(
            @PathVariable String userId,
            @PageableDefault(size = 50, sort = "changedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return fieldHistoryService.getUserHistory(userId, pageable)
                .map(FieldHistoryDto::fromEntity);
    }
}
