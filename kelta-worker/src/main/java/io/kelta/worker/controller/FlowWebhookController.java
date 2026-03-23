package io.kelta.worker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.flow.FlowEngine;
import io.kelta.runtime.flow.InitialStateBuilder;
import io.kelta.worker.repository.FlowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Handles inbound webhook requests that trigger AUTOLAUNCHED flows,
 * and provides an endpoint for the UI to retrieve the webhook URL.
 *
 * <p>{@code POST /api/webhooks/{flowId}} receives webhook payloads from
 * external systems and triggers the corresponding flow execution.
 *
 * <p>{@code GET /api/flows/{flowId}/webhook-url} returns the publicly
 * accessible webhook URL for a given flow.
 *
 * @since 1.0.0
 */
@RestController
@ConditionalOnBean(FlowEngine.class)
public class FlowWebhookController {

    private static final Logger log = LoggerFactory.getLogger(FlowWebhookController.class);

    private final FlowEngine flowEngine;
    private final FlowRepository flowRepository;
    private final InitialStateBuilder initialStateBuilder;
    private final ObjectMapper objectMapper;
    private final String externalBaseUrl;

    public FlowWebhookController(FlowEngine flowEngine,
                                  FlowRepository flowRepository,
                                  InitialStateBuilder initialStateBuilder,
                                  ObjectMapper objectMapper,
                                  @Value("${kelta.external-base-url:http://localhost:8080}") String externalBaseUrl) {
        this.flowEngine = flowEngine;
        this.flowRepository = flowRepository;
        this.initialStateBuilder = initialStateBuilder;
        this.objectMapper = objectMapper;
        this.externalBaseUrl = externalBaseUrl;
    }

    /**
     * Receives a webhook request and triggers the corresponding flow.
     * This endpoint is unauthenticated — external callers do not have JWT tokens.
     */
    @PostMapping("/api/webhooks/{flowId}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> receiveWebhook(
            @PathVariable String flowId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        log.info("Webhook received for flowId={}", flowId);

        // Load the flow definition
        Optional<Map<String, Object>> flowOpt = flowRepository.findFlowById(flowId);
        if (flowOpt.isEmpty()) {
            log.warn("Webhook received for unknown flow: {}", flowId);
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> flow = flowOpt.get();

        // Verify it's an AUTOLAUNCHED flow
        String flowType = (String) flow.get("flow_type");
        if (!"AUTOLAUNCHED".equals(flowType)) {
            log.warn("Webhook received for non-AUTOLAUNCHED flow: flowId={}, type={}", flowId, flowType);
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Bad Request",
                            "Flow is not configured for webhook triggers"));
        }

        // Verify flow is active
        Boolean active = (Boolean) flow.get("active");
        if (!Boolean.TRUE.equals(active)) {
            log.warn("Webhook received for inactive flow: flowId={}", flowId);
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Bad Request", "Flow is not active"));
        }

        String definitionJson = (String) flow.get("definition");
        if (definitionJson == null || definitionJson.isBlank()) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Bad Request", "Flow has no definition"));
        }

        String tenantId = (String) flow.get("tenant_id");
        String executionId = UUID.randomUUID().toString();

        // Extract selected headers
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", request.getContentType() != null ? request.getContentType() : "");
        headers.put("user-agent", request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "");

        // Build initial state from webhook
        Map<String, Object> initialState = initialStateBuilder.buildFromWebhook(
                body != null ? body : Map.of(),
                headers, tenantId, flowId, executionId);

        String resultExecutionId = flowEngine.startExecution(
                tenantId, flowId, definitionJson, initialState, "webhook", false);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("flowId", flowId);
        attrs.put("status", "RUNNING");

        log.info("Started webhook-triggered flow execution: flowId={}, executionId={}", flowId, resultExecutionId);
        return ResponseEntity.ok(
                JsonApiResponseBuilder.single("flow-executions", resultExecutionId, attrs));
    }

    /**
     * Returns the webhook URL for a given flow.
     * Used by the UI to display the URL in the flow designer.
     */
    @GetMapping("/api/flows/{flowId}/webhook-url")
    public ResponseEntity<Map<String, Object>> getWebhookUrl(
            @PathVariable String flowId) {

        Optional<Map<String, Object>> flowOpt = flowRepository.findFlowById(flowId);
        if (flowOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> flow = flowOpt.get();
        String flowType = (String) flow.get("flow_type");
        if (!"AUTOLAUNCHED".equals(flowType)) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Bad Request",
                            "Webhook URLs are only available for AUTOLAUNCHED flows"));
        }

        String webhookUrl = externalBaseUrl + "/api/webhooks/" + flowId;

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("webhookUrl", webhookUrl);
        attrs.put("flowId", flowId);

        return ResponseEntity.ok(JsonApiResponseBuilder.single("webhook-urls", flowId, attrs));
    }
}
