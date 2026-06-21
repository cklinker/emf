package io.kelta.ai.controller;

import io.kelta.ai.model.AgentDefinition;
import io.kelta.ai.model.AgentExecution;
import io.kelta.ai.service.AgentService;
import io.kelta.ai.service.AgentUpsertRequest;
import io.kelta.ai.service.agent.AgentExecutionException;
import io.kelta.ai.service.agent.AgentExecutionService;
import io.kelta.ai.service.agent.AgentRunRequest;
import io.kelta.ai.service.agent.AgentRunResult;
import io.kelta.ai.service.agent.AgentRuntimeService;
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
 * CRUD and execution for governed agent definitions, scoped to the tenant from {@code X-Tenant-ID}.
 * Routed through the gateway's existing {@code /api/ai/**} static route. {@code POST /{id}/run}
 * drives the bounded tool-use loop via {@link AgentRuntimeService}.
 */
@RestController
@RequestMapping("/api/ai/agents")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;
    private final AgentRuntimeService agentRuntimeService;
    private final AgentExecutionService agentExecutionService;

    public AgentController(AgentService agentService, AgentRuntimeService agentRuntimeService,
                           AgentExecutionService agentExecutionService) {
        this.agentService = agentService;
        this.agentRuntimeService = agentRuntimeService;
        this.agentExecutionService = agentExecutionService;
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

    @PostMapping("/{id}/run")
    public ResponseEntity<AgentRunResult> run(@RequestHeader("X-Tenant-ID") String tenantId,
                                              @RequestHeader(value = "X-User-Id", required = false) String userId,
                                              @PathVariable UUID id,
                                              @RequestBody AgentRunRequest request) {
        if (request == null || request.input() == null || request.input().isBlank()) {
            throw new IllegalArgumentException("'input' is required");
        }
        // A run executes tools as this user, so the worker's per-record Cerbos checks have a
        // principal — never let an agent act without one.
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("'X-User-Id' is required to run an agent");
        }
        AgentDefinition agent = agentService.get(tenantId, id).orElse(null);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        AgentRunResult result = agentRuntimeService.run(tenantId, userId, agent, request.input());
        log.info("Ran agent '{}' ({}) for tenant {}: {} iterations, {} tool calls",
                agent.name(), id, tenantId, result.iterations(), result.toolCalls().size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<List<AgentExecution>> executions(@RequestHeader("X-Tenant-ID") String tenantId,
                                                           @PathVariable UUID id,
                                                           @RequestParam(value = "limit", required = false) Integer limit) {
        if (agentService.get(tenantId, id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(agentExecutionService.list(tenantId, id, limit));
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

    @ExceptionHandler(AgentExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleExecution(AgentExecutionException e) {
        HttpStatus status = e.reason() == AgentExecutionException.Reason.TOKEN_LIMIT_EXCEEDED
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
    }
}
