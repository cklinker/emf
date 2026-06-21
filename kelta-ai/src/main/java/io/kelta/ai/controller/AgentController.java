package io.kelta.ai.controller;

import io.kelta.ai.model.AgentDefinition;
import io.kelta.ai.service.AgentService;
import io.kelta.ai.service.AgentUpsertRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD for governed agent definitions, scoped to the tenant from {@code X-Tenant-ID}. Routed through
 * the gateway's existing {@code /api/ai/**} static route. The orchestration runtime (slice 6b) runs
 * these agents; this controller manages their configuration only.
 */
@RestController
@RequestMapping("/api/ai/agents")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public List<AgentDefinition> list(@RequestHeader("X-Tenant-ID") String tenantId) {
        return agentService.list(tenantId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentDefinition> get(@RequestHeader("X-Tenant-ID") String tenantId,
                                               @PathVariable UUID id) {
        return agentService.get(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AgentDefinition> create(@RequestHeader("X-Tenant-ID") String tenantId,
                                                  @RequestHeader(value = "X-User-Id", required = false) String userId,
                                                  @RequestBody AgentUpsertRequest request) {
        AgentDefinition created = agentService.create(tenantId, userId, request);
        log.info("Created agent '{}' ({}) for tenant {}", created.name(), created.id(), tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgentDefinition> update(@RequestHeader("X-Tenant-ID") String tenantId,
                                                  @RequestHeader(value = "X-User-Id", required = false) String userId,
                                                  @PathVariable UUID id,
                                                  @RequestBody AgentUpsertRequest request) {
        return agentService.update(tenantId, userId, id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader("X-Tenant-ID") String tenantId,
                                       @PathVariable UUID id) {
        return agentService.delete(tenantId, id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "An agent with that name already exists"));
    }
}
