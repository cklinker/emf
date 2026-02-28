package com.emf.worker.controller;

import com.emf.runtime.event.ModuleChangeType;
import com.emf.runtime.module.TenantModuleData;
import com.emf.worker.module.ModuleConfigEventPublisher;
import com.emf.worker.module.RuntimeModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for runtime module management.
 * <p>
 * Provides CRUD operations for installing, enabling, disabling,
 * and uninstalling tenant-scoped modules.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/collections/modules")
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
    public ResponseEntity<List<TenantModuleData>> listModules(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(runtimeModuleManager.listModules(tenantId));
    }

    /**
     * Installs a module from its manifest.
     */
    @PostMapping("/install")
    public ResponseEntity<?> installModule(
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
                    Map.of("error", "manifest is required"));
            }

            TenantModuleData installed = runtimeModuleManager.installModule(
                tenantId, manifestJson, sourceUrl, checksum, jarSizeBytes,
                userId != null ? userId : "system");

            eventPublisher.publishEvent(installed, ModuleChangeType.INSTALLED);

            return ResponseEntity.ok(installed);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to install module: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Enables a module, registering its action handlers.
     */
    @PostMapping("/{moduleId}/enable")
    public ResponseEntity<?> enableModule(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String moduleId) {
        try {
            runtimeModuleManager.enableModule(tenantId, moduleId);

            var module = runtimeModuleManager.listModules(tenantId).stream()
                .filter(m -> m.moduleId().equals(moduleId))
                .findFirst();
            module.ifPresent(m -> eventPublisher.publishEvent(m, ModuleChangeType.ENABLED));

            return ResponseEntity.ok(Map.of("status", "enabled", "moduleId", moduleId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to enable module '{}': {}", moduleId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Disables a module, unregistering its action handlers.
     */
    @PostMapping("/{moduleId}/disable")
    public ResponseEntity<?> disableModule(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String moduleId) {
        try {
            var module = runtimeModuleManager.listModules(tenantId).stream()
                .filter(m -> m.moduleId().equals(moduleId))
                .findFirst();

            runtimeModuleManager.disableModule(tenantId, moduleId);

            module.ifPresent(m -> eventPublisher.publishEvent(m, ModuleChangeType.DISABLED));

            return ResponseEntity.ok(Map.of("status", "disabled", "moduleId", moduleId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to disable module '{}': {}", moduleId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Uninstalls a module entirely.
     */
    @DeleteMapping("/{moduleId}")
    public ResponseEntity<?> uninstallModule(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String moduleId) {
        try {
            var module = runtimeModuleManager.listModules(tenantId).stream()
                .filter(m -> m.moduleId().equals(moduleId))
                .findFirst();

            runtimeModuleManager.uninstallModule(tenantId, moduleId);

            module.ifPresent(m -> eventPublisher.publishEvent(m, ModuleChangeType.UNINSTALLED));

            return ResponseEntity.ok(Map.of("status", "uninstalled", "moduleId", moduleId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to uninstall module '{}': {}", moduleId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Gets the actions provided by a specific module.
     */
    @GetMapping("/{moduleId}/actions")
    public ResponseEntity<?> getModuleActions(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String moduleId) {
        var module = runtimeModuleManager.listModules(tenantId).stream()
            .filter(m -> m.moduleId().equals(moduleId))
            .findFirst();

        if (module.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(module.get().actions());
    }
}
