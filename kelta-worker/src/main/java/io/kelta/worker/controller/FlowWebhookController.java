package io.kelta.worker.controller;

import tools.jackson.databind.ObjectMapper;
import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.flow.FlowEngine;
import io.kelta.worker.service.FlowActorResolver;
import io.kelta.runtime.flow.InitialStateBuilder;
import io.kelta.worker.repository.FlowRepository;
import io.kelta.worker.service.TenantSlugResolver;
import io.kelta.worker.util.TenantContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

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
    private final FlowActorResolver flowActorResolver;
    private final FlowRepository flowRepository;
    private final InitialStateBuilder initialStateBuilder;
    private final ObjectMapper objectMapper;
    private final TenantSlugResolver tenantSlugResolver;
    private final String externalBaseUrl;

    public FlowWebhookController(FlowEngine flowEngine,
                                  FlowRepository flowRepository,
                                  InitialStateBuilder initialStateBuilder,
                                  ObjectMapper objectMapper,
                                  TenantSlugResolver tenantSlugResolver,
                                  FlowActorResolver flowActorResolver,
                                  @Value("${kelta.external-base-url:http://localhost:8080}") String externalBaseUrl) {
        this.flowEngine = flowEngine;
        this.flowActorResolver = flowActorResolver;
        this.flowRepository = flowRepository;
        this.initialStateBuilder = initialStateBuilder;
        this.objectMapper = objectMapper;
        this.tenantSlugResolver = tenantSlugResolver;
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

        // Webhooks are unauthenticated and bypass the tenant filter, so the slug
        // is not yet bound. Resolve it before invoking the engine — the engine's
        // thread-pool propagation snapshots TenantContext at submit time, and
        // schema-per-tenant SQL inside the flow needs the slug to resolve to the
        // right schema.
        String tenantSlug = tenantSlugResolver.resolveSlug(tenantId).orElse(null);
        if (tenantSlug == null) {
            log.warn("Could not resolve slug for tenant {} on webhook for flow {} — flow execution will fall back to public schema",
                    tenantId, flowId);
        }
        AtomicReference<String> resultRef = new AtomicReference<>();
        try {
            // Actor (run-as user -> flow owner) replaces the legacy "webhook"
            // provenance marker so records the flow writes get audit users.
            String actor = flowActorResolver.resolve(tenantId, flowId, null);
            TenantContextUtils.withTenant(tenantId, tenantSlug, () ->
                    resultRef.set(flowEngine.startExecution(
                            tenantId, flowId, definitionJson, initialState, actor, null, false)));
        } catch (Exception e) {
            log.error("Failed to start webhook-triggered flow execution: flowId={}", flowId, e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to start flow execution"));
        }
        String resultExecutionId = resultRef.get();

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
