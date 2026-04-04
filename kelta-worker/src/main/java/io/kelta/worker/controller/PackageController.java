package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.PackageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/packages")
public class PackageController {

    private static final Logger log = LoggerFactory.getLogger(PackageController.class);

    private final PackageService packageService;
    private final ObjectMapper objectMapper;

    public PackageController(PackageService packageService, ObjectMapper objectMapper) {
        this.packageService = packageService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory() {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        List<Map<String, Object>> history = packageService.getHistory(tenantId);
        return ResponseEntity.ok(JsonApiResponseBuilder.collection("packages", history));
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportPackage(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        // Unwrap JSON:API envelope if present
        Map<String, Object> options = unwrapJsonApiBody(body);

        String name = (String) options.get("name");
        String version = (String) options.get("version");
        if (name == null || name.isBlank() || version == null || version.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Package name and version are required"));
        }

        Map<String, Object> pkg = packageService.exportPackage(tenantId, options);

        // Return as downloadable JSON
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(pkg);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "-" + version + ".json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBytes);
        } catch (Exception e) {
            log.error("Failed to serialize package", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to serialize package"));
        }
    }

    @PostMapping("/import/preview")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> previewImport(@RequestParam("file") MultipartFile file) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
        }

        try {
            Map<String, Object> pkg = objectMapper.readValue(file.getInputStream(), Map.class);
            Map<String, Object> preview = packageService.previewImport(tenantId, pkg);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            log.error("Failed to parse package file for preview", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid package file: " + e.getMessage()));
        }
    }

    @PostMapping("/import")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> importPackage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
        }

        try {
            Map<String, Object> pkg = objectMapper.readValue(file.getInputStream(), Map.class);
            Map<String, Object> result = packageService.importPackage(tenantId, pkg, dryRun);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to import package", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Import failed: " + e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapJsonApiBody(Map<String, Object> body) {
        // Check if wrapped in JSON:API format: { data: { type: "...", attributes: { ... } } }
        Object dataObj = body.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            Object attrObj = data.get("attributes");
            if (attrObj instanceof Map<?, ?> attributes) {
                return new LinkedHashMap<>((Map<String, Object>) attributes);
            }
        }
        // Already unwrapped
        return body;
    }
}
