package com.emf.controlplane.controller;

import com.emf.controlplane.service.ExportService;
import com.emf.controlplane.service.SecurityAuditService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/control/export")
@PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_DATA')")
public class ExportController {

    private final ExportService exportService;
    private final SecurityAuditService auditService;

    public ExportController(ExportService exportService,
                            @org.springframework.lang.Nullable SecurityAuditService auditService) {
        this.exportService = exportService;
        this.auditService = auditService;
    }

    @PostMapping("/csv")
    public ResponseEntity<byte[]> exportCsv(@RequestBody ExportRequest request) {
        byte[] data = exportService.exportToCsv(request.columns(), request.rows());
        if (auditService != null) {
            auditService.logBulkExport(request.filename(), request.rows() != null ? request.rows().size() : 0, "CSV");
        }
        String filename = (request.filename() != null ? request.filename() : "export") + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    @PostMapping("/xlsx")
    public ResponseEntity<byte[]> exportXlsx(@RequestBody ExportRequest request) throws IOException {
        String sheetName = request.filename() != null ? request.filename() : "Export";
        byte[] data = exportService.exportToXlsx(sheetName, request.columns(), request.rows());
        if (auditService != null) {
            auditService.logBulkExport(request.filename(), request.rows() != null ? request.rows().size() : 0, "XLSX");
        }
        String filename = (request.filename() != null ? request.filename() : "export") + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    public record ExportRequest(
            String filename,
            List<String> columns,
            List<Map<String, Object>> rows
    ) {}
}
