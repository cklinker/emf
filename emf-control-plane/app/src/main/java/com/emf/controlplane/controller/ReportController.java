package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateReportRequest;
import com.emf.controlplane.dto.ReportDto;
import com.emf.controlplane.entity.ReportFolder;
import com.emf.controlplane.service.ReportService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public List<ReportDto> listReports(
            @RequestParam(required = false) String userId) {
        String tenantId = TenantContextHolder.requireTenantId();
        if (userId != null) {
            return reportService.listReports(tenantId, userId).stream()
                    .map(ReportDto::fromEntity).toList();
        }
        return reportService.listAllReports(tenantId).stream()
                .map(ReportDto::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public ReportDto getReport(@PathVariable String id) {
        return ReportDto.fromEntity(reportService.getReport(id));
    }

    @PostMapping
    public ResponseEntity<ReportDto> createReport(
            @RequestParam String userId,
            @RequestBody CreateReportRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();
        var report = reportService.createReport(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportDto.fromEntity(report));
    }

    @PutMapping("/{id}")
    public ReportDto updateReport(
            @PathVariable String id,
            @RequestBody CreateReportRequest request) {
        return ReportDto.fromEntity(reportService.updateReport(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable String id) {
        reportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }

    // --- Folders ---

    @GetMapping("/folders")
    public List<ReportFolder> listFolders() {
        String tenantId = TenantContextHolder.requireTenantId();
        return reportService.listFolders(tenantId);
    }

    @PostMapping("/folders")
    public ResponseEntity<ReportFolder> createFolder(
            @RequestParam String userId,
            @RequestParam String name,
            @RequestParam(required = false) String accessLevel) {
        String tenantId = TenantContextHolder.requireTenantId();
        var folder = reportService.createFolder(tenantId, userId, name, accessLevel);
        return ResponseEntity.status(HttpStatus.CREATED).body(folder);
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String id) {
        reportService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }
}
