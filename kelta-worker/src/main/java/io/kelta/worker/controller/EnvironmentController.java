package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.SandboxEnvironmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentController.class);

    private final SandboxEnvironmentService environmentService;

    public EnvironmentController(SandboxEnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @GetMapping
    public ResponseEntity<?> listEnvironments() {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        List<Map<String, Object>> environments = environmentService.listEnvironments(tenantId);
        return ResponseEntity.ok(JsonApiResponseBuilder.collection("environments", environments));
    }

    @GetMapping("/{envId}")
    public ResponseEntity<?> getEnvironment(@PathVariable String envId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        var env = environmentService.getEnvironment(envId, tenantId);
        if (env.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var data = env.get();
        return ResponseEntity.ok(JsonApiResponseBuilder.single("environments", extractId(data), data));
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> createEnvironment(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        Map<String, Object> attrs = unwrapJsonApiBody(body);
        String name = (String) attrs.get("name");
        String description = (String) attrs.get("description");
        String type = (String) attrs.getOrDefault("type", "SANDBOX");
        String sourceEnvId = (String) attrs.get("sourceEnvironmentId");
        String createdBy = (String) attrs.get("createdBy");

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Environment name is required"));
        }

        String config = null;
        Object configObj = attrs.get("config");
        if (configObj instanceof Map) {
            try {
                config = new tools.jackson.databind.ObjectMapper().writeValueAsString(configObj);
            } catch (Exception e) {
                log.warn("Failed to serialize environment config", e);
            }
        }

        try {
            Map<String, Object> env;
            if ("STAGING".equals(type)) {
                env = environmentService.createStaging(tenantId, name, description, sourceEnvId, config, createdBy);
            } else {
                env = environmentService.createSandbox(tenantId, name, description, sourceEnvId, config, createdBy);
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(JsonApiResponseBuilder.single("environments", extractId(env), env));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{envId}")
    public ResponseEntity<?> updateEnvironment(@PathVariable String envId,
                                                @RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        Map<String, Object> attrs = unwrapJsonApiBody(body);
        String name = (String) attrs.get("name");
        String description = (String) attrs.get("description");

        String config = null;
        Object configObj = attrs.get("config");
        if (configObj instanceof Map) {
            try {
                config = new tools.jackson.databind.ObjectMapper().writeValueAsString(configObj);
            } catch (Exception e) {
                log.warn("Failed to serialize environment config", e);
            }
        }

        try {
            Map<String, Object> env = environmentService.updateEnvironment(envId, tenantId, name, description, config);
            return ResponseEntity.ok(JsonApiResponseBuilder.single("environments", extractId(env), env));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{envId}/archive")
    public ResponseEntity<?> archiveEnvironment(@PathVariable String envId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        try {
            environmentService.archiveEnvironment(envId, tenantId);
            return ResponseEntity.ok(Map.of("status", "archived"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{envId}/snapshots")
    public ResponseEntity<?> createSnapshot(@PathVariable String envId,
                                             @RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        Map<String, Object> attrs = unwrapJsonApiBody(body);
        String name = (String) attrs.getOrDefault("name", "Snapshot " + java.time.Instant.now());
        String createdBy = (String) attrs.get("createdBy");

        try {
            Map<String, Object> snapshot = environmentService.createSnapshot(tenantId, envId, name, createdBy);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(JsonApiResponseBuilder.single("snapshots", extractId(snapshot), snapshot));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{envId}/snapshots")
    public ResponseEntity<?> listSnapshots(@PathVariable String envId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        List<Map<String, Object>> snapshots = environmentService.listSnapshots(tenantId, envId);
        return ResponseEntity.ok(JsonApiResponseBuilder.collection("snapshots", snapshots));
    }

    @PostMapping("/compare")
    public ResponseEntity<?> compareEnvironments(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        Map<String, Object> attrs = unwrapJsonApiBody(body);
        String sourceEnvId = (String) attrs.get("sourceEnvironmentId");
        String targetEnvId = (String) attrs.get("targetEnvironmentId");

        if (sourceEnvId == null || targetEnvId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Both sourceEnvironmentId and targetEnvironmentId are required"));
        }

        try {
            Map<String, Object> diff = environmentService.compareEnvironments(tenantId, sourceEnvId, targetEnvId);
            return ResponseEntity.ok(diff);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String extractId(Map<String, Object> data) {
        Object id = data.get("id");
        return id != null ? id.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapJsonApiBody(Map<String, Object> body) {
        Object dataObj = body.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            Object attrObj = data.get("attributes");
            if (attrObj instanceof Map<?, ?> attributes) {
                return new LinkedHashMap<>((Map<String, Object>) attributes);
            }
        }
        return body;
    }
}
