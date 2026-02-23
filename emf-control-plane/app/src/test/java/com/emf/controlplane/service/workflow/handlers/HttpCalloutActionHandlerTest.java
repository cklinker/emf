package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpCalloutActionHandlerTest {

    private HttpCalloutActionHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        handler = new HttpCalloutActionHandler(objectMapper);
        handler.setRestTemplate(restTemplate);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("HTTP_CALLOUT", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should execute GET callout")
    void shouldExecuteGetCallout() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{\"result\":\"ok\"}", HttpStatus.OK));

        ActionContext ctx = createContext("""
            {
                "url": "https://api.example.com/data",
                "method": "GET"
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals(200, result.outputData().get("statusCode"));
        assertEquals("https://api.example.com/data", result.outputData().get("url"));
        assertEquals("GET", result.outputData().get("method"));
        assertNotNull(result.outputData().get("responseBody"));
    }

    @Test
    @DisplayName("Should execute POST callout with body")
    void shouldExecutePostCalloutWithBody() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{\"id\":\"123\"}", HttpStatus.CREATED));

        ActionContext ctx = createContext("""
            {
                "url": "https://api.example.com/items",
                "method": "POST",
                "body": {"name": "Test"},
                "headers": {"Authorization": "Bearer token123"}
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals(201, result.outputData().get("statusCode"));
    }

    @Test
    @DisplayName("Should capture response variable name")
    void shouldCaptureResponseVariable() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{\"score\":85}", HttpStatus.OK));

        ActionContext ctx = createContext("""
            {
                "url": "https://api.example.com/score",
                "responseVariable": "apiScore"
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("apiScore", result.outputData().get("responseVariable"));
    }

    @Test
    @DisplayName("Should default method to GET")
    void shouldDefaultMethodToGet() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));

        ActionContext ctx = createContext("{\"url\": \"https://api.example.com\"}");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
    }

    @Test
    @DisplayName("Should fail when URL missing")
    void shouldFailWhenUrlMissing() {
        ActionContext ctx = createContext("{\"method\": \"GET\"}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("URL is required"));
    }

    @Test
    @DisplayName("Should fail with invalid HTTP method")
    void shouldFailWithInvalidMethod() {
        ActionContext ctx = createContext("{\"url\": \"https://api.example.com\", \"method\": \"INVALID\"}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Invalid HTTP method"));
    }

    @Test
    @DisplayName("Should handle HTTP errors gracefully")
    void shouldHandleHttpErrors() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new org.springframework.web.client.HttpClientErrorException(HttpStatus.NOT_FOUND));

        ActionContext ctx = createContext("{\"url\": \"https://api.example.com/missing\"}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should parse JSON response data")
    void shouldParseJsonResponseData() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{\"key\":\"value\"}", HttpStatus.OK));

        ActionContext ctx = createContext("{\"url\": \"https://api.example.com\"}");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertNotNull(result.outputData().get("responseData"));
    }

    @Test
    @DisplayName("Validate should reject missing URL")
    void validateShouldRejectMissingUrl() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAcceptValid() {
        assertDoesNotThrow(() -> handler.validate("{\"url\": \"https://api.example.com\"}"));
    }

    private ActionContext createContext(String configJson) {
        return ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("id", "rec-1"))
            .previousData(Map.of())
            .changedFields(List.of())
            .userId("user-1")
            .actionConfigJson(configJson)
            .workflowRuleId("rule-1")
            .executionLogId("exec-1")
            .resolvedData(Map.of())
            .build();
    }
}
