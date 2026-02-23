package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionHandler;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Action handler that sends an outbound HTTP webhook request.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "url": "https://api.example.com/webhook",
 *   "method": "POST",
 *   "headers": {
 *     "Authorization": "Bearer token123",
 *     "Content-Type": "application/json"
 *   },
 *   "bodyTemplate": "{\"recordId\": \"{{id}}\", \"status\": \"{{status}}\"}"
 * }
 * </pre>
 * <p>
 * Supports GET, POST, PUT, PATCH, and DELETE methods.
 * The response status and body are captured in the {@link ActionResult#outputData()}.
 */
@Component
public class OutboundMessageActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(OutboundMessageActionHandler.class);
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private final ObjectMapper objectMapper;
    private RestTemplate restTemplate;

    public OutboundMessageActionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Sets the RestTemplate for testing purposes.
     */
    void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getActionTypeKey() {
        return "OUTBOUND_MESSAGE";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String url = (String) config.get("url");
            if (url == null || url.isBlank()) {
                return ActionResult.failure("URL is required for outbound message");
            }

            String method = (String) config.getOrDefault("method", "POST");

            // Build headers
            HttpHeaders headers = new HttpHeaders();
            @SuppressWarnings("unchecked")
            Map<String, String> configHeaders = (Map<String, String>) config.get("headers");
            if (configHeaders != null) {
                configHeaders.forEach(headers::set);
            }

            // Set default content type if not specified
            if (!headers.containsKey("Content-Type")) {
                headers.set("Content-Type", "application/json");
            }

            // Build body
            String body = null;
            Object bodyTemplate = config.get("bodyTemplate");
            if (bodyTemplate != null) {
                body = bodyTemplate.toString();
            } else {
                // Send record data as default body
                Map<String, Object> payload = new HashMap<>();
                payload.put("recordId", context.recordId());
                payload.put("collectionId", context.collectionId());
                payload.put("collectionName", context.collectionName());
                payload.put("data", context.data());
                payload.put("workflowRuleId", context.workflowRuleId());
                body = objectMapper.writeValueAsString(payload);
            }

            log.info("Outbound message: method={}, url={}, workflowRule={}",
                method, url, context.workflowRuleId());

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());

            ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, request, String.class);

            Map<String, Object> outputData = new HashMap<>();
            outputData.put("statusCode", response.getStatusCode().value());
            outputData.put("url", url);
            outputData.put("method", method);
            if (response.getBody() != null && response.getBody().length() <= 10_000) {
                outputData.put("responseBody", response.getBody());
            }

            log.info("Outbound message completed: url={}, status={}", url, response.getStatusCode().value());

            return ActionResult.success(outputData);
        } catch (Exception e) {
            log.error("Failed to execute outbound message action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("url") == null) {
                throw new IllegalArgumentException("Config must contain 'url'");
            }

            String method = (String) config.get("method");
            if (method != null && !ALLOWED_METHODS.contains(method.toUpperCase())) {
                throw new IllegalArgumentException("Invalid HTTP method: " + method
                    + ". Allowed methods: " + ALLOWED_METHODS);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
