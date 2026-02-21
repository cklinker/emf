package com.emf.controlplane.controller;

import com.emf.controlplane.dto.BulkJobDto;
import com.emf.controlplane.dto.BulkJobResultDto;
import com.emf.controlplane.dto.CreateBulkJobRequest;
import com.emf.controlplane.service.BulkJobService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/bulk-jobs")
@PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_DATA')")
public class BulkJobController {

    private final BulkJobService bulkJobService;

    public BulkJobController(BulkJobService bulkJobService) {
        this.bulkJobService = bulkJobService;
    }

    @GetMapping
    public List<BulkJobDto> listJobs() {
        String tenantId = TenantContextHolder.requireTenantId();
        return bulkJobService.listJobs(tenantId).stream()
                .map(BulkJobDto::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public BulkJobDto getJob(@PathVariable String id) {
        return BulkJobDto.fromEntity(bulkJobService.getJob(id));
    }

    @PostMapping
    public ResponseEntity<BulkJobDto> createJob(
            @RequestParam String userId,
            @RequestBody CreateBulkJobRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();
        var job = bulkJobService.createJob(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BulkJobDto.fromEntity(job));
    }

    @PostMapping("/{id}/abort")
    public BulkJobDto abortJob(@PathVariable String id) {
        return BulkJobDto.fromEntity(bulkJobService.abortJob(id));
    }

    @GetMapping("/{id}/results")
    public List<BulkJobResultDto> getResults(@PathVariable String id) {
        return bulkJobService.getResults(id).stream()
                .map(BulkJobResultDto::fromEntity).toList();
    }

    @GetMapping("/{id}/errors")
    public List<BulkJobResultDto> getErrors(@PathVariable String id) {
        return bulkJobService.getErrorResults(id).stream()
                .map(BulkJobResultDto::fromEntity).toList();
    }
}
