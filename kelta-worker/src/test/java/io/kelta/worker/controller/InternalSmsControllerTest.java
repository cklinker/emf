package io.kelta.worker.controller;

import io.kelta.worker.service.sms.DefaultSmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("InternalSmsController Tests")
class InternalSmsControllerTest {

    private DefaultSmsService smsService;
    private InternalSmsController controller;

    @BeforeEach
    void setUp() {
        smsService = mock(DefaultSmsService.class);
        controller = new InternalSmsController(smsService);
    }

    @Nested
    @DisplayName("sendCode")
    class SendCode {

        @Test
        void shouldReturnOkOnSuccess() {
            when(smsService.sendVerificationCode("+14155551234", "tenant-1")).thenReturn(true);

            var body = new HashMap<String, String>();
            body.put("phoneNumber", "+14155551234");
            body.put("tenantId", "tenant-1");

            var response = controller.sendCode(body);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        void shouldReturn429OnRateLimit() {
            when(smsService.sendVerificationCode("+14155551234", "tenant-1")).thenReturn(false);

            var body = new HashMap<String, String>();
            body.put("phoneNumber", "+14155551234");
            body.put("tenantId", "tenant-1");

            var response = controller.sendCode(body);
            assertEquals(429, response.getStatusCode().value());
        }

        @Test
        void shouldReturnBadRequestWhenPhoneNumberMissing() {
            var body = new HashMap<String, String>();
            body.put("tenantId", "tenant-1");

            var response = controller.sendCode(body);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        void shouldReturnBadRequestWhenTenantIdMissing() {
            var body = new HashMap<String, String>();
            body.put("phoneNumber", "+14155551234");

            var response = controller.sendCode(body);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("verifyCode")
    class VerifyCode {

        @Test
        void shouldReturnVerifiedTrue() {
            when(smsService.verifyCode("+14155551234", "1234", "tenant-1")).thenReturn(true);

            var body = new HashMap<String, String>();
            body.put("phoneNumber", "+14155551234");
            body.put("code", "1234");
            body.put("tenantId", "tenant-1");

            var response = controller.verifyCode(body);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            assertEquals(true, responseBody.get("verified"));
        }

        @Test
        void shouldReturnVerifiedFalse() {
            when(smsService.verifyCode("+14155551234", "0000", "tenant-1")).thenReturn(false);

            var body = new HashMap<String, String>();
            body.put("phoneNumber", "+14155551234");
            body.put("code", "0000");
            body.put("tenantId", "tenant-1");

            var response = controller.verifyCode(body);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            assertEquals(false, responseBody.get("verified"));
        }

        @Test
        void shouldReturnBadRequestWhenCodeMissing() {
            var body = new HashMap<String, String>();
            body.put("phoneNumber", "+14155551234");
            body.put("tenantId", "tenant-1");

            var response = controller.verifyCode(body);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }
}
