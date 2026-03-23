package io.kelta.worker.service.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.runtime.module.integration.spi.EmailService;
import io.kelta.worker.repository.EmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("DefaultEmailService")
class DefaultEmailServiceTest {

    private EmailProvider emailProvider;
    private EmailRepository emailRepository;
    private DefaultEmailService service;

    @BeforeEach
    void setUp() {
        emailProvider = mock(EmailProvider.class);
        emailRepository = mock(EmailRepository.class);
        service = new DefaultEmailService(
                emailProvider, emailRepository,
                "noreply@kelta.io", "Kelta Platform", true);
    }

    @Nested
    @DisplayName("queueEmail")
    class QueueEmail {

        @Test
        @DisplayName("Should create log entry and delegate to provider with platform defaults")
        void shouldQueueWithPlatformDefaults() {
            when(emailRepository.createEmailLog("t1", "user@example.com", "Subject", "WORKFLOW", "flow-1"))
                    .thenReturn("log-123");
            when(emailRepository.getTenantSettings("t1")).thenReturn(null);

            String logId = service.queueEmail("t1", "user@example.com", "Subject", "<p>Body</p>", "WORKFLOW", "flow-1");

            assertThat(logId).isEqualTo("log-123");
            verify(emailRepository).createEmailLog("t1", "user@example.com", "Subject", "WORKFLOW", "flow-1");
        }

        @Test
        @DisplayName("Should resolve tenant settings when tenant has email overrides")
        void shouldResolveTenantSettings() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode settings = mapper.readTree("""
                    {"email": {"smtp": {"host": "smtp.tenant.com", "port": 587}, "fromAddress": "noreply@tenant.com"}}
                    """);

            when(emailRepository.createEmailLog(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("log-456");
            when(emailRepository.getTenantSettings("t2")).thenReturn(settings);

            service.queueEmail("t2", "user@test.com", "Hello", "<p>Hi</p>", "SYSTEM", null);

            verify(emailRepository).createEmailLog("t2", "user@test.com", "Hello", "SYSTEM", null);
        }

        @Test
        @DisplayName("Should return dummy ID and skip delivery when disabled")
        void shouldSkipWhenDisabled() {
            DefaultEmailService disabledService = new DefaultEmailService(
                    emailProvider, emailRepository,
                    "noreply@kelta.io", "Kelta Platform", false);

            String logId = disabledService.queueEmail("t1", "user@test.com", "Subject", "Body", "TEST", null);

            assertThat(logId).isNotNull();
            verify(emailProvider, never()).send(any(), any());
            verify(emailRepository, never()).createEmailLog(anyString(), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("sendAsync")
    class SendAsync {

        @Test
        @DisplayName("Should mark log as SENT on successful delivery")
        void shouldMarkSentOnSuccess() {
            service.sendAsync("log-1", "user@test.com", "Subject", "<p>Body</p>", null);

            verify(emailRepository).markSending("log-1");
            verify(emailProvider).send(any(EmailMessage.class), isNull());
            verify(emailRepository).markSent(eq("log-1"), anyString());
        }

        @Test
        @DisplayName("Should mark log as FAILED when provider throws")
        void shouldMarkFailedOnProviderError() {
            doThrow(new EmailDeliveryException("Connection refused"))
                    .when(emailProvider).send(any(), any());

            service.sendAsync("log-2", "user@test.com", "Subject", "<p>Body</p>", null);

            verify(emailRepository).markSending("log-2");
            verify(emailRepository).markFailed(eq("log-2"), eq("Connection refused"), anyString());
            verify(emailRepository, never()).markSent(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("getTemplate")
    class GetTemplate {

        @Test
        @DisplayName("Should return mapped template when found")
        void shouldReturnTemplateWhenFound() {
            io.kelta.runtime.context.TenantContext.set("t1");
            try {
                when(emailRepository.findTemplateByTenantAndId("t1", "tpl-1"))
                        .thenReturn(Optional.of(Map.of(
                                "subject", "Welcome",
                                "body_html", "<p>Hello</p>"
                        )));

                Optional<EmailService.EmailTemplate> result = service.getTemplate("tpl-1");

                assertThat(result).isPresent();
                assertThat(result.get().subject()).isEqualTo("Welcome");
                assertThat(result.get().bodyHtml()).isEqualTo("<p>Hello</p>");
            } finally {
                io.kelta.runtime.context.TenantContext.clear();
            }
        }

        @Test
        @DisplayName("Should return empty when template not found")
        void shouldReturnEmptyWhenNotFound() {
            io.kelta.runtime.context.TenantContext.set("t1");
            try {
                when(emailRepository.findTemplateByTenantAndId("t1", "missing"))
                        .thenReturn(Optional.empty());

                Optional<EmailService.EmailTemplate> result = service.getTemplate("missing");

                assertThat(result).isEmpty();
            } finally {
                io.kelta.runtime.context.TenantContext.clear();
            }
        }

        @Test
        @DisplayName("Should return empty when no tenant context")
        void shouldReturnEmptyWithoutTenantContext() {
            io.kelta.runtime.context.TenantContext.clear();

            Optional<EmailService.EmailTemplate> result = service.getTemplate("tpl-1");

            assertThat(result).isEmpty();
            verify(emailRepository, never()).findTemplateByTenantAndId(anyString(), anyString());
        }
    }
}
