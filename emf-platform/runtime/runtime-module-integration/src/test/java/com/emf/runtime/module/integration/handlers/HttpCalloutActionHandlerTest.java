package com.emf.runtime.module.integration.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("HttpCalloutActionHandler")
class HttpCalloutActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;
    private HttpCalloutActionHandler handler;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        handler = new HttpCalloutActionHandler(objectMapper, restTemplate);
    }

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("HTTP_CALLOUT", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should execute GET request and capture response")
    void shouldExecuteGetRequest() {
        when(restTemplate.exchange(eq("https://api.example.com/data"), eq(HttpMethod.GET), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{\"status\":\"ok\"}", HttpStatus.OK));

        String config = """
            {"url": "https://api.example.com/data", "method": "GET"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals(200, result.outputData().get("statusCode"));
        assertEquals("https://api.example.com/data", result.outputData().get("url"));
    }

    @Test
    @DisplayName("Should default method to GET")
    void shouldDefaultToGet() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        String config = """
            {"url": "https://api.example.com"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
    }

    @Test
    @DisplayName("Should fail when URL is missing")
    void shouldFailWhenUrlMissing() {
        String config = "{}";
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("URL"));
    }

    @Test
    @DisplayName("Should fail on invalid HTTP method")
    void shouldFailOnInvalidMethod() {
        String config = """
            {"url": "https://api.example.com", "method": "INVALID"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Invalid HTTP method"));
    }

    @Test
    @DisplayName("Should capture response variable")
    void shouldCaptureResponseVariable() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));

        String config = """
            {"url": "https://api.example.com", "responseVariable": "apiResult"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("apiResult", result.outputData().get("responseVariable"));
    }

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of()).userId("user-1")
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
