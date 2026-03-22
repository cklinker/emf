package io.kelta.worker.controller;

import io.kelta.worker.service.email.DefaultEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("InternalEmailController")
class InternalEmailControllerTest {

    private DefaultEmailService emailService;
    private InternalEmailController controller;

    @BeforeEach
    void setUp() {
        emailService = mock(DefaultEmailService.class);
        controller = new InternalEmailController(emailService, "test-secret-token");
    }

    @Test
    @DisplayName("Should accept request with valid internal token")
    void shouldAcceptWithValidToken() {
        when(emailService.queueEmail("t1", "user@test.com", "Subject", "<p>Body</p>", "TEST", null))
                .thenReturn("log-123");

        var request = new InternalEmailController.SendEmailRequest(
                "t1", "user@test.com", "Subject", "<p>Body</p>", "TEST", null);

        var response = controller.sendEmail("test-secret-token", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("emailLogId", "log-123");
        assertThat(response.getBody()).containsEntry("status", "QUEUED");
    }

    @Test
    @DisplayName("Should reject request with missing token")
    void shouldRejectMissingToken() {
        var request = new InternalEmailController.SendEmailRequest(
                "t1", "user@test.com", "Subject", "<p>Body</p>", "TEST", null);

        var response = controller.sendEmail(null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(emailService, never()).queueEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should reject request with invalid token")
    void shouldRejectInvalidToken() {
        var request = new InternalEmailController.SendEmailRequest(
                "t1", "user@test.com", "Subject", "<p>Body</p>", "TEST", null);

        var response = controller.sendEmail("wrong-token", request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(emailService, never()).queueEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should reject when internal token not configured")
    void shouldRejectWhenTokenNotConfigured() {
        InternalEmailController noTokenController = new InternalEmailController(emailService, "");

        var request = new InternalEmailController.SendEmailRequest(
                "t1", "user@test.com", "Subject", "<p>Body</p>", "TEST", null);

        var response = noTokenController.sendEmail("any-token", request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }
}
