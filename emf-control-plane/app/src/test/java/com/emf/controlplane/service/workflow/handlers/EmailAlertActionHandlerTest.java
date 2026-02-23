package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.entity.EmailLog;
import com.emf.controlplane.entity.EmailTemplate;
import com.emf.controlplane.repository.EmailLogRepository;
import com.emf.controlplane.service.EmailTemplateService;
import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailAlertActionHandlerTest {

    private EmailAlertActionHandler handler;
    private EmailTemplateService emailTemplateService;
    private EmailLogRepository emailLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        emailTemplateService = mock(EmailTemplateService.class);
        emailLogRepository = mock(EmailLogRepository.class);
        when(emailLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler = new EmailAlertActionHandler(objectMapper, emailTemplateService, emailLogRepository);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("EMAIL_ALERT", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should send email with inline config")
    void shouldSendWithInlineConfig() {
        ActionContext ctx = createContext("""
            {"to": "user@example.com", "subject": "Test Subject", "body": "<p>Hello</p>"}""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("user@example.com", result.outputData().get("to"));
        assertEquals("Test Subject", result.outputData().get("subject"));
        assertEquals("QUEUED", result.outputData().get("status"));
        verify(emailLogRepository).save(any(EmailLog.class));
    }

    @Test
    @DisplayName("Should use template when templateId is provided")
    void shouldUseTemplate() {
        EmailTemplate template = new EmailTemplate();
        template.setSubject("Template Subject");
        template.setBodyHtml("<p>Template Body</p>");
        when(emailTemplateService.getTemplate("tmpl-1")).thenReturn(template);

        ActionContext ctx = createContext("""
            {"to": "user@example.com", "templateId": "tmpl-1"}""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("Template Subject", result.outputData().get("subject"));
    }

    @Test
    @DisplayName("Should fall back to inline config when template fails")
    void shouldFallBackOnTemplateError() {
        when(emailTemplateService.getTemplate("tmpl-bad"))
            .thenThrow(new RuntimeException("Not found"));

        ActionContext ctx = createContext("""
            {"to": "user@example.com", "templateId": "tmpl-bad", "subject": "Fallback", "body": "body"}""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("Fallback", result.outputData().get("subject"));
    }

    @Test
    @DisplayName("Should fail when 'to' is missing")
    void shouldFailWhenToMissing() {
        ActionContext ctx = createContext("""
            {"subject": "Test", "body": "Body"}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("Should fail when subject is missing and no template")
    void shouldFailWhenSubjectMissing() {
        ActionContext ctx = createContext("""
            {"to": "user@example.com"}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Validate should reject missing 'to'")
    void validateShouldRejectMissingTo() {
        assertThrows(IllegalArgumentException.class,
            () -> handler.validate("{\"subject\": \"Test\"}"));
    }

    @Test
    @DisplayName("Validate should accept valid config with template")
    void validateShouldAcceptTemplateConfig() {
        assertDoesNotThrow(() -> handler.validate(
            "{\"to\": \"user@example.com\", \"templateId\": \"tmpl-1\"}"));
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
