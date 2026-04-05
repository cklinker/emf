package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.event.ModuleChangeType;
import io.kelta.runtime.module.TenantModuleData;
import io.kelta.worker.module.ModuleConfigEventPublisher;
import io.kelta.worker.module.RuntimeModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for runtime module management.
 * <p>
 * Provides CRUD operations for installing, enabling, disabling,
 * and uninstalling tenant-scoped modules. Returns JSON:API format.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/modules")
@ConditionalOnBean(RuntimeModuleManager.class)
public class ModuleController {

    private static final Logger log = LoggerFactory.getLogger(ModuleController.class);

    private final RuntimeModuleManager runtimeModuleManager;
    private final ModuleConfigEventPublisher eventPublisher;

    public ModuleController(RuntimeModuleManager runtimeModuleManager,
                             ModuleConfigEventPublisher eventPublisher) {
        this.runtimeModuleManager = runtimeModuleManager;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Lists all installed modules for the current tenant.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listModules(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<TenantModuleData> modules = runtimeModuleManager.listModules(tenantId);
        List<Map<String, Object>> records = modules.stream()
                .map(this::moduleToMap)
                .toList();
        return ResponseEntity.ok(JsonApiResponseBuilder.collection("modules", records));
    }

    /**
     * Installs a module from its manifest.
     */
    @PostMapping("/install")
    public ResponseEntity<Map<String, Object>> installModule(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestBody Map<String, Object> body) {
        try {
            String manifestJson = (String) body.get("manifest");
            String sourceUrl = (String) body.getOrDefault("sourceUrl", "local://upload");
            String checksum = (String) body.getOrDefault("checksum", "none");
            Long jarSizeBytes = body.containsKey("jarSizeBytes")
                ? ((Number) body.get("jarSizeBytes")).longValue() : null;

            if (manifestJson == null || manifestJson.isBlank()) {
                return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Validation Error", "manifest is required"));
            }

            TenantModuleData installed = runtimeModuleManager.installModule(
                tenantId, manifestJson, sourceUrl, checksum, jarSizeBytes,
                userId != null ? userId : "system");

            eventPublisher.publishEvent(installed, ModuleChangeType.INSTALLED);

            return ResponseEntity.ok(
                    JsonApiResponseBuilder.single("modules", installed.id(), moduleToAttributes(installed)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(
                    JsonApiResponseBuilder.error("409", "Conflict", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to install module: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Bad Request", e.getMessage()));
        }
    }

    /**
     * Installs a module with its JAR file (multipart upload).
     * The manifest is extracted from the request part, and the JAR is stored in S3.
     */
    @PostMapping(value = "/install-jar", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> installModuleWithJar(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestPart("manifest") String manifestJson,
            @RequestPart("jar") MultipartFile jarFile) {
        try {
            if (manifestJson == null || manifestJson.isBlank()) {
                return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Validation Error", "manifest is required"));
            }
            if (jarFile == null || jarFile.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Validation Error", "jar file is required"));
            }

            byte[] jarBytes = jarFile.getBytes();
            TenantModuleData installed = runtimeModuleManager.installModuleWithJar(
                tenantId, manifestJson, jarBytes,
                userId != null ? userId : "system");

            eventPublisher.publishEvent(installed, ModuleChangeType.INSTALLED);

            return ResponseEntity.ok(
                    JsonApiResponseBuilder.single("modules", installed.id(), moduleToAttributes(installed)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(
                    JsonApiResponseBuilder.error("409", "Conflict", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to install module with JAR: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Bad Request", e.getMessage()));
        }
    }

    /**
     * Enables a module, registering its action handlers.
     */
    @PostMapping("/{moduleId}/enable")
    public ResponseEntity<Map<String, Object>> enableModule(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String moduleId) {
        try {
            runtimeModuleManager.enableModule(tenantId, moduleId);

            var module = runtimeModuleManager.listModules(tenantId).stream()
                .filter(m -> m.moduleId().equals(moduleId))
                .findFirst();
            module.ifPresent(m -> eventPublisher.publishEvent(m, ModuleChangeType.ENABLED));

            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("status", "enabled");
            attrs.put("moduleId", moduleId);
            return ResponseEntity.ok(JsonApiResponseBuilder.single("modules", moduleId, attrs));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to enable module '{}': {}", moduleId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error", e.getMessage()));
        }
    }

    /**
     * Disables a module, unregistering its action handlers.
     */
    @PostMapping("/{moduleId}/disable")
    public ResponseEntity<Map<String, Object>> disableModule(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String moduleId) {
        try {
            var module = runtimeModuleManager.listModules(tenantId).stream()
                .filter(m -> m.moduleId().equals(moduleId))
                .findFirst();

            runtimeModuleManager.disableModule(tenantId, moduleId);

            module.ifPresent(m -> eventPublisher.publishEvent(m, ModuleChangeType.DISABLED));

            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("status", "disabled");
            attrs.put("moduleId", moduleId);
            return ResponseEntity.ok(JsonApiResponseBuilder.single("modules", moduleId, attrs));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to disable module '{}': {}", moduleId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error", e.getMessage()));
        }
    }

    /**
     * Uninstalls a module entirely.
     */
    @DeleteMapping("/{moduleId}")
    public ResponseEntity<Map<String, Object>> uninstallModule(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String moduleId) {
        try {
            var module = runtimeModuleManager.listModules(tenantId).stream()
                .filter(m -> m.moduleId().equals(moduleId))
                .findFirst();

            runtimeModuleManager.uninstallModule(tenantId, moduleId);

            module.ifPresent(m -> eventPublisher.publishEvent(m, ModuleChangeType.UNINSTALLED));

            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("status", "uninstalled");
            attrs.put("moduleId", moduleId);
            return ResponseEntity.ok(JsonApiResponseBuilder.single("modules", moduleId, attrs));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to uninstall module '{}': {}", moduleId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error", e.getMessage()));
        }
    }

    /**
     * Gets the actions provided by a specific module.
     */
    @GetMapping("/{moduleId}/actions")
    public ResponseEntity<Map<String, Object>> getModuleActions(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String moduleId) {
        var module = runtimeModuleManager.listModules(tenantId).stream()
            .filter(m -> m.moduleId().equals(moduleId))
            .findFirst();

        if (module.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> actionRecords = module.get().actions().stream()
                .map(a -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", a.id());
                    map.put("actionKey", a.actionKey());
                    map.put("name", a.name());
                    map.put("category", a.category());
                    map.put("description", a.description());
                    return map;
                })
                .toList();
        return ResponseEntity.ok(JsonApiResponseBuilder.collection("module-actions", actionRecords));
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private Map<String, Object> moduleToMap(TenantModuleData m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.id());
        map.put("moduleId", m.moduleId());
        map.put("name", m.name());
        map.put("version", m.version());
        map.put("description", m.description());
        map.put("status", m.status());
        map.put("installedBy", m.installedBy());
        map.put("installedAt", m.installedAt() != null ? m.installedAt().toString() : null);
        map.put("updatedAt", m.updatedAt() != null ? m.updatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> moduleToAttributes(TenantModuleData m) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("moduleId", m.moduleId());
        attrs.put("name", m.name());
        attrs.put("version", m.version());
        attrs.put("description", m.description());
        attrs.put("status", m.status());
        attrs.put("installedBy", m.installedBy());
        attrs.put("installedAt", m.installedAt() != null ? m.installedAt().toString() : null);
        attrs.put("updatedAt", m.updatedAt() != null ? m.updatedAt().toString() : null);
        return attrs;
    }
}
