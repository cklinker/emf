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
 * Action handler that makes a generic HTTP request with response capture.
 * <p>
 * Similar to {@link OutboundMessageActionHandler} but with additional features:
 * response variable capture and data payload support.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "url": "https://api.example.com/endpoint",
 *   "method": "POST",
 *   "headers": {
 *     "Authorization": "Bearer token123"
 *   },
 *   "body": "{\"key\": \"value\"}",
 *   "responseVariable": "apiResponse"
 * }
 * </pre>
 * <p>
 * The response body is captured and available via {@code responseVariable}
 * for downstream actions in the workflow.
 */
@Component
public class HttpCalloutActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpCalloutActionHandler.class);
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_RESPONSE_SIZE = 50_000;

    private final ObjectMapper objectMapper;
    private RestTemplate restTemplate;

    public HttpCalloutActionHandler(ObjectMapper objectMapper) {
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
        return "HTTP_CALLOUT";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String url = (String) config.get("url");
            if (url == null || url.isBlank()) {
                return ActionResult.failure("URL is required for HTTP callout");
            }

            String method = (String) config.getOrDefault("method", "GET");
            if (!ALLOWED_METHODS.contains(method.toUpperCase())) {
                return ActionResult.failure("Invalid HTTP method: " + method);
            }

            // Build headers
            HttpHeaders headers = new HttpHeaders();
            @SuppressWarnings("unchecked")
            Map<String, String> configHeaders = (Map<String, String>) config.get("headers");
            if (configHeaders != null) {
                configHeaders.forEach(headers::set);
            }

            if (!headers.containsKey("Content-Type")) {
                headers.set("Content-Type", "application/json");
            }

            // Build body
            String body = null;
            Object bodyConfig = config.get("body");
            if (bodyConfig != null) {
                body = bodyConfig instanceof String
                    ? (String) bodyConfig
                    : objectMapper.writeValueAsString(bodyConfig);
            }

            log.info("HTTP callout: method={}, url={}, workflowRule={}",
                method, url, context.workflowRuleId());

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());

            ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, request, String.class);

            Map<String, Object> outputData = new HashMap<>();
            outputData.put("statusCode", response.getStatusCode().value());
            outputData.put("url", url);
            outputData.put("method", method);

            if (response.getBody() != null) {
                String responseBody = response.getBody().length() <= MAX_RESPONSE_SIZE
                    ? response.getBody()
                    : response.getBody().substring(0, MAX_RESPONSE_SIZE) + "... [truncated]";
                outputData.put("responseBody", responseBody);

                // Try to parse response as JSON for structured access
                try {
                    Object parsedBody = objectMapper.readValue(response.getBody(), Object.class);
                    outputData.put("responseData", parsedBody);
                } catch (Exception e) {
                    // Response is not JSON, that's OK
                }
            }

            String responseVariable = (String) config.get("responseVariable");
            if (responseVariable != null && !responseVariable.isBlank()) {
                outputData.put("responseVariable", responseVariable);
            }

            log.info("HTTP callout completed: url={}, status={}", url, response.getStatusCode().value());

            return ActionResult.success(outputData);
        } catch (Exception e) {
            log.error("Failed to execute HTTP callout action: {}", e.getMessage(), e);
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
