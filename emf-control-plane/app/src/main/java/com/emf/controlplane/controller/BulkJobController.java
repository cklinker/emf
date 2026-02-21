package com.emf.controlplane.controller;

import com.emf.controlplane.dto.BulkJobDto;
import com.emf.controlplane.dto.BulkJobResultDto;
import com.emf.controlplane.dto.CreateBulkJobRequest;
import com.emf.controlplane.service.BulkJobService;
import com.emf.controlplane.service.SecurityAuditService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/control/bulk-jobs")
@PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_DATA')")
public class BulkJobController {

    private final BulkJobService bulkJobService;
    private final SecurityAuditService securityAuditService;

    public BulkJobController(BulkJobService bulkJobService,
                             @Nullable SecurityAuditService securityAuditService) {
        this.bulkJobService = bulkJobService;
        this.securityAuditService = securityAuditService;
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

        if (securityAuditService != null) {
            String operation = request.getOperation();
            String eventType = "DELETE".equalsIgnoreCase(operation) ? "BULK_DELETE" : "BULK_OPERATION";
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("operation", operation);
            details.put("collectionId", request.getCollectionId());
            details.put("totalRecords", job.getTotalRecords());
            securityAuditService.log(eventType, "DATA",
                    "BULK_JOB", job.getId(), operation, details);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(BulkJobDto.fromEntity(job));
    }

    @PostMapping("/{id}/abort")
    public BulkJobDto abortJob(@PathVariable String id) {
        var job = bulkJobService.abortJob(id);

        if (securityAuditService != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("operation", job.getOperation());
            details.put("collectionId", job.getCollectionId());
            details.put("processedRecords", job.getProcessedRecords());
            securityAuditService.log("BULK_JOB_ABORTED", "DATA",
                    "BULK_JOB", job.getId(), job.getOperation(), details);
        }

        return BulkJobDto.fromEntity(job);
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
