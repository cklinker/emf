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

@DisplayName("OutboundMessageActionHandler")
class OutboundMessageActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;
    private OutboundMessageActionHandler handler;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        handler = new OutboundMessageActionHandler(objectMapper, restTemplate);
    }

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("OUTBOUND_MESSAGE", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should send POST request with body template")
    void shouldSendPostWithBodyTemplate() {
        when(restTemplate.exchange(eq("https://webhook.example.com"), eq(HttpMethod.POST), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        String config = """
            {"url": "https://webhook.example.com", "bodyTemplate": "{\\"event\\": \\"test\\"}"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals(200, result.outputData().get("statusCode"));
    }

    @Test
    @DisplayName("Should default to POST method")
    void shouldDefaultToPost() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        String config = """
            {"url": "https://webhook.example.com"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("POST", result.outputData().get("method"));
    }

    @Test
    @DisplayName("Should send record data as default body when no template")
    void shouldSendRecordDataAsDefault() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        String config = """
            {"url": "https://webhook.example.com"}
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

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of("status", "Active")).userId("user-1")
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
