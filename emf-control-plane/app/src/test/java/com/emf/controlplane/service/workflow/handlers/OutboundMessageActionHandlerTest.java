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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboundMessageActionHandlerTest {

    private OutboundMessageActionHandler handler;
    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        handler = new OutboundMessageActionHandler(objectMapper);
        handler.setRestTemplate(restTemplate);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("OUTBOUND_MESSAGE", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should send POST request successfully")
    void shouldSendPostRequest() {
        when(restTemplate.exchange(eq("https://api.example.com/webhook"), eq(HttpMethod.POST),
            any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{\"ok\":true}", HttpStatus.OK));

        ActionContext ctx = createContext("""
            {
                "url": "https://api.example.com/webhook",
                "method": "POST",
                "headers": {"Authorization": "Bearer token123"},
                "bodyTemplate": "{\\"recordId\\": \\"rec-1\\"}"
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals(200, result.outputData().get("statusCode"));
        assertEquals("https://api.example.com/webhook", result.outputData().get("url"));
    }

    @Test
    @DisplayName("Should default to POST method")
    void shouldDefaultToPost() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        ActionContext ctx = createContext("""
            {"url": "https://api.example.com/webhook"}""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Should send default payload when no body template")
    void shouldSendDefaultPayload() {
        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        ActionContext ctx = createContext("""
            {"url": "https://api.example.com/webhook"}""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
    }

    @Test
    @DisplayName("Should fail when URL is missing")
    void shouldFailWhenUrlMissing() {
        ActionContext ctx = createContext("""
            {"method": "POST"}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should handle HTTP error gracefully")
    void shouldHandleHttpError() {
        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RestClientException("Connection refused"));

        ActionContext ctx = createContext("""
            {"url": "https://api.example.com/webhook"}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("Validate should reject missing URL")
    void validateShouldRejectMissingUrl() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Validate should reject invalid method")
    void validateShouldRejectInvalidMethod() {
        assertThrows(IllegalArgumentException.class,
            () -> handler.validate("{\"url\": \"https://example.com\", \"method\": \"INVALID\"}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAcceptValid() {
        assertDoesNotThrow(() -> handler.validate(
            "{\"url\": \"https://example.com\", \"method\": \"POST\"}"));
    }

    private ActionContext createContext(String configJson) {
        return ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("id", "rec-1", "status", "Active"))
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
