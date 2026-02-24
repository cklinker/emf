package com.emf.runtime.module.integration.handlers;

import com.emf.runtime.module.integration.spi.EmailService;
import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("EmailAlertActionHandler")
class EmailAlertActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmailService emailService = mock(EmailService.class);
    private final EmailAlertActionHandler handler = new EmailAlertActionHandler(objectMapper, emailService);

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("EMAIL_ALERT", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should queue email with inline subject and body")
    void shouldQueueEmailInline() {
        when(emailService.queueEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn("email-123");

        String config = """
            {"to": "user@example.com", "subject": "Order Confirmed", "body": "Your order is ready."}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("email-123", result.outputData().get("emailLogId"));
        assertEquals("user@example.com", result.outputData().get("to"));
        assertEquals("QUEUED", result.outputData().get("status"));
    }

    @Test
    @DisplayName("Should use template when templateId provided")
    void shouldUseTemplate() {
        when(emailService.getTemplate("tmpl-1"))
            .thenReturn(Optional.of(new EmailService.EmailTemplate("Template Subject", "<h1>Body</h1>")));
        when(emailService.queueEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn("email-456");

        String config = """
            {"to": "user@example.com", "templateId": "tmpl-1"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("Template Subject", result.outputData().get("subject"));
    }

    @Test
    @DisplayName("Should fall back to config when template not found")
    void shouldFallbackToConfig() {
        when(emailService.getTemplate("missing"))
            .thenReturn(Optional.empty());
        when(emailService.queueEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn("email-789");

        String config = """
            {"to": "user@example.com", "templateId": "missing", "subject": "Fallback", "body": "text"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("Fallback", result.outputData().get("subject"));
    }

    @Test
    @DisplayName("Should fail when to address is missing")
    void shouldFailWhenToMissing() {
        String config = """
            {"subject": "Test", "body": "text"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("to"));
    }

    @Test
    @DisplayName("Should fail when subject is missing and no template")
    void shouldFailWhenSubjectMissing() {
        String config = """
            {"to": "user@example.com"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("subject"));
    }

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of()).userId("user-1")
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
