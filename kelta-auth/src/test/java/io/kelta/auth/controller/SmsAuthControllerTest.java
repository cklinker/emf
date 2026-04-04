package io.kelta.auth.controller;

import io.kelta.auth.service.SmsVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmsAuthController Tests")
class SmsAuthControllerTest {

    @Mock private SmsVerificationService smsService;
    private SmsAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new SmsAuthController(smsService);
    }

    @Nested
    @DisplayName("POST /auth/sms/send")
    class SendCode {
        @Test
        void shouldSendCodeSuccessfully() {
            when(smsService.sendCode("+15551234567", "tenant-1")).thenReturn(true);
            var body = Map.of("phoneNumber", "+15551234567", "tenantId", "tenant-1");
            var response = controller.sendCode(body, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(smsService).sendCode("+15551234567", "tenant-1");
        }

        @Test
        void shouldUseTenantIdFromHeader() {
            var body = new HashMap<String, String>();
            body.put("phoneNumber", "+15551234567");
            var response = controller.sendCode(body, "header-tenant");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(smsService).sendCode("+15551234567", "header-tenant");
        }

        @Test
        void shouldPreferBodyTenantIdOverHeader() {
            var body = Map.of("phoneNumber", "+15551234567", "tenantId", "body-tenant");
            var response = controller.sendCode(body, "header-tenant");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(smsService).sendCode("+15551234567", "body-tenant");
        }

        @Test
        void shouldReturnBadRequestWhenPhoneNumberMissing() {
            var body = Map.of("tenantId", "tenant-1");
            var response = controller.sendCode(body, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldReturnBadRequestWhenTenantIdMissing() {
            var body = new HashMap<String, String>();
            body.put("phoneNumber", "+15551234567");
            var response = controller.sendCode(body, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("POST /auth/sms/verify")
    class VerifyCode {
        @Test
        void shouldReturnVerifiedOnSuccess() {
            when(smsService.verifyCode("+15551234567", "123456", "tenant-1")).thenReturn(true);
            var body = Map.of("phoneNumber", "+15551234567", "code", "123456", "tenantId", "tenant-1");
            var response = controller.verifyCode(body, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var responseBody = (Map<String, String>) response.getBody();
            assertThat(responseBody).containsEntry("status", "verified");
        }

        @Test
        void shouldReturnFailedOnInvalidCode() {
            when(smsService.verifyCode("+15551234567", "000000", "tenant-1")).thenReturn(false);
            var body = Map.of("phoneNumber", "+15551234567", "code", "000000", "tenantId", "tenant-1");
            var response = controller.verifyCode(body, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var responseBody = (Map<String, Object>) response.getBody();
            assertThat(responseBody).containsEntry("status", "failed");
        }

        @Test
        void shouldReturnBadRequestWhenCodeMissing() {
            var body = Map.of("phoneNumber", "+15551234567", "tenantId", "tenant-1");
            var response = controller.verifyCode(body, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
