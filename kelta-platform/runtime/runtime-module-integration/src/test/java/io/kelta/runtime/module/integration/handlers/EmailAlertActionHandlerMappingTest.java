package io.kelta.runtime.module.integration.handlers;

import io.kelta.runtime.flow.StateDataResolver;
import io.kelta.runtime.module.integration.mapping.PayloadMapperService;
import io.kelta.runtime.module.integration.spi.EmailService;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EmailAlertActionHandler · payload mapper integration (PR 5)")
class EmailAlertActionHandlerMappingTest {

    private ObjectMapper objectMapper;
    private EmailService emailService;
    private EmailAlertActionHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        emailService = mock(EmailService.class);
        when(emailService.queueEmail(anyString(), anyString(), anyString(), anyString(),
            anyString(), any())).thenReturn("email-log-1");
        when(emailService.getTemplate(anyString())).thenReturn(Optional.empty());

        PayloadMapperService mapper = new PayloadMapperService(new StateDataResolver(objectMapper));
        handler = new EmailAlertActionHandler(objectMapper, emailService, mapper);
    }

    @Test
    @DisplayName("Resolves ${$.path} placeholders in subject + body before sending")
    void resolvesDollarPath() throws Exception {
        Map<String, Object> config = Map.of(
            "to", "manager@example.com",
            "subject", "Order ${$.record.data.orderNumber} approved",
            "body", "Hi ${$.record.data.customer.name}, your order is approved.");

        ActionContext ctx = ActionContext.builder()
            .tenantId("t-1")
            .actionConfigJson(objectMapper.writeValueAsString(config))
            .resolvedData(Map.of(
                "record", Map.of("data", Map.of(
                    "orderNumber", "O-100",
                    "customer", Map.of("name", "Alex")))))
            .build();

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful(), result.errorMessage());
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).queueEmail(eq("t-1"), eq("manager@example.com"),
            subjectCaptor.capture(), bodyCaptor.capture(), eq("WORKFLOW"), any());
        assertEquals("Order O-100 approved", subjectCaptor.getValue());
        assertEquals("Hi Alex, your order is approved.", bodyCaptor.getValue());
    }

    @Test
    @DisplayName("Evaluates =jsonata expressions in subject")
    void evaluatesJsonata() throws Exception {
        Map<String, Object> config = Map.of(
            "to", "team@example.com",
            "subject", "=$uppercase(record.data.tag) & ' alert'",
            "body", "n/a");

        ActionContext ctx = ActionContext.builder()
            .tenantId("t-1")
            .actionConfigJson(objectMapper.writeValueAsString(config))
            .resolvedData(Map.of("record", Map.of("data", Map.of("tag", "p1"))))
            .build();

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful(), result.errorMessage());
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).queueEmail(eq("t-1"), anyString(), subjectCaptor.capture(),
            anyString(), eq("WORKFLOW"), any());
        assertEquals("P1 alert", subjectCaptor.getValue());
    }

    @Test
    @DisplayName("Surfaces mapper failures as Mapper.Failure instead of crashing")
    void mapperFailureSurfaces() throws Exception {
        Map<String, Object> config = Map.of(
            "to", "team@example.com",
            "subject", "=this is not valid jsonata $$$",
            "body", "x");

        ActionContext ctx = ActionContext.builder()
            .tenantId("t-1")
            .actionConfigJson(objectMapper.writeValueAsString(config))
            .resolvedData(Map.of())
            .build();

        ActionResult result = handler.execute(ctx);

        assertTrue(!result.successful());
        assertTrue(result.errorMessage().startsWith("Mapper.Failure"),
            "got: " + result.errorMessage());
    }
}
