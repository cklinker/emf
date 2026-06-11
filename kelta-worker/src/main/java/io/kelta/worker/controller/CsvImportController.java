package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.worker.service.CsvImportService;
import io.kelta.worker.service.CsvImportService.ImportResult;
import io.kelta.worker.service.CsvImportService.RowError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accepts a CSV file upload and bulk-creates records in the named collection.
 *
 * <p>The CSV must have a header row with column names matching field names.
 * System fields (id, createdAt, updatedAt, createdBy, updatedBy) are ignored.
 * Rows that fail validation are reported per-row; successful rows are committed.
 */
@RestController
@RequestMapping("/api/collections/{collectionName}/import")
public class CsvImportController {

    private static final Logger log = LoggerFactory.getLogger(CsvImportController.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final CsvImportService csvImportService;

    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importCsv(
            @PathVariable String collectionName,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Empty file", "No file content received"));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "File too large",
                            "Maximum file size is 10 MB"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Invalid file type",
                            "Only CSV files are supported"));
        }

        ImportResult result;
        try {
            result = csvImportService.importCsv(collectionName, file.getInputStream());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Import failed", e.getMessage()));
        } catch (Exception e) {
            log.error("CSV import error: collection={}, tenant={}, error={}",
                    collectionName, tenantId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Import failed", e.getMessage()));
        }

        securityLog.info("security_event=CSV_IMPORT user={} tenant={} collection={} rows={} imported={}",
                userEmail, tenantId, collectionName, result.rowsProcessed(), result.rowsImported());

        log.info("CSV import completed: collection={}, tenant={}, rows={}, imported={}, errors={}",
                collectionName, tenantId, result.rowsProcessed(), result.rowsImported(),
                result.errors().size());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rowsProcessed", result.rowsProcessed());
        body.put("rowsImported", result.rowsImported());
        body.put("errorCount", result.errors().size());
        body.put("errors", result.errors().stream()
                .map(e -> Map.of("row", e.row(), "message", e.message()))
                .toList());

        return ResponseEntity.ok(body);
    }
}
