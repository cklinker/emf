package io.kelta.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmsVerificationService Tests")
class SmsVerificationServiceTest {

    @Mock private WorkerClient workerClient;
    private SmsVerificationService service;

    @BeforeEach
    void setUp() {
        service = new SmsVerificationService(workerClient);
    }

    @Nested
    @DisplayName("sendCode")
    class SendCode {
        @Test
        void shouldDelegateToWorkerClient() {
            when(workerClient.sendSmsCode("+15551234567", "tenant-1")).thenReturn(true);
            assertThat(service.sendCode("+15551234567", "tenant-1")).isTrue();
        }

        @Test
        void shouldReturnFalseWhenWorkerClientFails() {
            when(workerClient.sendSmsCode("+15551234567", "tenant-1")).thenReturn(false);
            assertThat(service.sendCode("+15551234567", "tenant-1")).isFalse();
        }

        @Test
        void shouldReturnFalseOnException() {
            when(workerClient.sendSmsCode("+15551234567", "tenant-1"))
                    .thenThrow(new RuntimeException("connection refused"));
            assertThat(service.sendCode("+15551234567", "tenant-1")).isFalse();
        }
    }

    @Nested
    @DisplayName("verifyCode")
    class VerifyCode {
        @Test
        void shouldReturnTrueWhenCodeMatches() {
            when(workerClient.verifySmsCode("+15551234567", "123456", "tenant-1")).thenReturn(true);
            assertThat(service.verifyCode("+15551234567", "123456", "tenant-1")).isTrue();
        }

        @Test
        void shouldReturnFalseWhenCodeDoesNotMatch() {
            when(workerClient.verifySmsCode("+15551234567", "000000", "tenant-1")).thenReturn(false);
            assertThat(service.verifyCode("+15551234567", "000000", "tenant-1")).isFalse();
        }

        @Test
        void shouldReturnFalseOnException() {
            when(workerClient.verifySmsCode("+15551234567", "123456", "tenant-1"))
                    .thenThrow(new RuntimeException("timeout"));
            assertThat(service.verifyCode("+15551234567", "123456", "tenant-1")).isFalse();
        }
    }
}
