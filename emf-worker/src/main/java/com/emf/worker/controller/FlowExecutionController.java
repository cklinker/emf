package com.emf.worker.controller;

import com.emf.runtime.flow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST endpoints for flow execution management.
 * <p>
 * Provides endpoints to execute flows, cancel executions, view execution
 * details, and retrieve step-level logs.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/collections/flows")
@ConditionalOnBean(FlowEngine.class)
public class FlowExecutionController {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutionController.class);

    private static final String SELECT_FLOW_BY_ID = """
            SELECT id, tenant_id, name, definition, trigger_config, flow_type, active
            FROM flow WHERE id = ?
            """;

    private final FlowEngine flowEngine;
    private final FlowStore flowStore;
    private final InitialStateBuilder initialStateBuilder;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FlowExecutionController(FlowEngine flowEngine,
                                    FlowStore flowStore,
                                    InitialStateBuilder initialStateBuilder,
                                    JdbcTemplate jdbcTemplate,
                                    ObjectMapper objectMapper) {
        this.flowEngine = flowEngine;
        this.flowStore = flowStore;
        this.initialStateBuilder = initialStateBuilder;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes a flow with optional input payload.
     * Used for AUTOLAUNCHED flows and manual trigger from UI.
     *
     * @param flowId   the flow ID to execute
     * @param body     optional request body containing "input" map
     * @param tenantId tenant ID from request header
     * @param userId   user ID from request header
     * @return execution ID and status
     */
    @PostMapping("/{flowId}/execute")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> executeFlow(
            @PathVariable String flowId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {

        log.debug("Execute flow request: flowId={}, tenantId={}", flowId, tenantId);

        // Load the flow definition
        Map<String, Object> flow = loadFlow(flowId);
        if (flow == null) {
            return ResponseEntity.notFound().build();
        }

        String definitionJson = (String) flow.get("definition");
        if (definitionJson == null || definitionJson.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Flow has no definition");
            return ResponseEntity.badRequest().body(error);
        }

        // If no tenantId from header, use the flow's tenant
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = (String) flow.get("tenant_id");
        }

        // Build initial state
        Map<String, Object> inputPayload = Map.of();
        if (body != null && body.containsKey("input")) {
            Object input = body.get("input");
            if (input instanceof Map) {
                inputPayload = (Map<String, Object>) input;
            }
        }

        boolean isTest = body != null && Boolean.TRUE.equals(body.get("test"));

        String executionId = UUID.randomUUID().toString();
        Map<String, Object> initialState = initialStateBuilder.buildFromApiInvocation(
                inputPayload, tenantId, userId, flowId, executionId);

        String resultExecutionId = flowEngine.startExecution(
                tenantId, flowId, definitionJson, initialState, userId, isTest);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", resultExecutionId);
        response.put("flowId", flowId);
        response.put("status", "RUNNING");

        log.info("Started flow execution: flowId={}, executionId={}", flowId, resultExecutionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels a running flow execution.
     *
     * @param executionId the execution ID to cancel
     * @return cancellation status
     */
    @PostMapping("/executions/{executionId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelExecution(
            @PathVariable String executionId) {

        log.debug("Cancel execution request: executionId={}", executionId);

        Optional<FlowExecutionData> execution = flowStore.loadExecution(executionId);
        if (execution.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FlowExecutionData exec = execution.get();
        if (exec.isTerminal()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Execution is already in terminal state: " + exec.status());
            return ResponseEntity.badRequest().body(error);
        }

        flowEngine.cancelExecution(executionId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", executionId);
        response.put("status", "CANCELLED");

        log.info("Cancelled flow execution: executionId={}", executionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Gets execution details including state data.
     *
     * @param executionId the execution ID
     * @return execution details
     */
    @GetMapping("/executions/{executionId}")
    public ResponseEntity<Map<String, Object>> getExecution(
            @PathVariable String executionId) {

        log.debug("Get execution request: executionId={}", executionId);

        Optional<FlowExecutionData> execution = flowStore.loadExecution(executionId);
        if (execution.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(executionToMap(execution.get()));
    }

    /**
     * Gets step-level execution logs for an execution.
     *
     * @param executionId the execution ID
     * @return list of step logs
     */
    @GetMapping("/executions/{executionId}/steps")
    public ResponseEntity<Map<String, Object>> getExecutionSteps(
            @PathVariable String executionId) {

        log.debug("Get execution steps request: executionId={}", executionId);

        Optional<FlowExecutionData> execution = flowStore.loadExecution(executionId);
        if (execution.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<FlowStepLogData> steps = flowStore.loadStepLogs(executionId);

        List<Map<String, Object>> stepList = new ArrayList<>();
        for (FlowStepLogData step : steps) {
            Map<String, Object> stepMap = new LinkedHashMap<>();
            stepMap.put("id", step.id());
            stepMap.put("stateId", step.stateId());
            stepMap.put("stateName", step.stateName());
            stepMap.put("stateType", step.stateType());
            stepMap.put("status", step.status());
            stepMap.put("errorMessage", step.errorMessage());
            stepMap.put("errorCode", step.errorCode());
            stepMap.put("attemptNumber", step.attemptNumber());
            stepMap.put("durationMs", step.durationMs());
            stepMap.put("startedAt", step.startedAt() != null ? step.startedAt().toString() : null);
            stepMap.put("completedAt", step.completedAt() != null ? step.completedAt().toString() : null);
            stepList.add(stepMap);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", executionId);
        response.put("steps", stepList);
        response.put("totalSteps", stepList.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Lists executions for a specific flow.
     *
     * @param flowId the flow ID
     * @param limit  max results (default 50)
     * @param offset offset for pagination
     * @return list of executions
     */
    @GetMapping("/{flowId}/flow-executions")
    public ResponseEntity<Map<String, Object>> listExecutions(
            @PathVariable String flowId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        log.debug("List executions request: flowId={}, limit={}, offset={}", flowId, limit, offset);

        List<FlowExecutionData> executions = flowStore.findExecutionsByFlow(flowId, limit, offset);

        List<Map<String, Object>> executionList = new ArrayList<>();
        for (FlowExecutionData exec : executions) {
            executionList.add(executionToMap(exec));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("flowId", flowId);
        response.put("executions", executionList);
        response.put("count", executionList.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns all available flow task resources (built-in + runtime modules)
     * with their descriptors.
     *
     * @return list of resource descriptors
     */
    @GetMapping("/resources")
    public ResponseEntity<Map<String, Object>> getResources() {
        log.debug("Get flow resources request");

        // TODO: Iterate ActionHandlerRegistry and collect descriptors
        // For now, return empty list — will be populated when descriptors are added to handlers
        List<Map<String, Object>> resources = new ArrayList<>();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("resources", resources);
        response.put("count", resources.size());

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> loadFlow(String flowId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_FLOW_BY_ID, flowId);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    private Map<String, Object> executionToMap(FlowExecutionData exec) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", exec.id());
        map.put("flowId", exec.flowId());
        map.put("tenantId", exec.tenantId());
        map.put("status", exec.status());
        map.put("startedBy", exec.startedBy());
        map.put("currentNodeId", exec.currentNodeId());
        map.put("stepCount", exec.stepCount());
        map.put("durationMs", exec.durationMs());
        map.put("errorMessage", exec.errorMessage());
        map.put("isTest", exec.isTest());
        map.put("startedAt", exec.startedAt() != null ? exec.startedAt().toString() : null);
        map.put("completedAt", exec.completedAt() != null ? exec.completedAt().toString() : null);
        return map;
    }
}
