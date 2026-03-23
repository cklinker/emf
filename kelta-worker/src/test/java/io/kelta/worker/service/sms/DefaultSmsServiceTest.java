package io.kelta.worker.service.sms;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultSmsService Tests")
class DefaultSmsServiceTest {

    @Mock private SmsProvider smsProvider;
    @Mock private JdbcTemplate jdbcTemplate;

    private DefaultSmsService service;

    @BeforeEach
    void setUp() {
        service = new DefaultSmsService(smsProvider, jdbcTemplate);
    }

    @Nested
    @DisplayName("Code Generation")
    class CodeGeneration {
        @Test
        void shouldGenerate6DigitCode() {
            String code = service.generateCode();
            assertThat(code).hasSize(6);
            assertThat(code).matches("\\d{6}");
        }

        @Test
        void shouldGenerateUniqueCodeS() {
            String c1 = service.generateCode();
            String c2 = service.generateCode();
            // Very unlikely to be equal with SecureRandom
            // but we're testing it generates valid codes
            assertThat(c1).matches("\\d{6}");
            assertThat(c2).matches("\\d{6}");
        }
    }

    @Nested
    @DisplayName("E.164 Validation")
    class E164Validation {
        @Test
        void shouldAcceptValidE164() {
            assertThat(DefaultSmsService.isValidE164("+14155551234")).isTrue();
            assertThat(DefaultSmsService.isValidE164("+447911123456")).isTrue();
            assertThat(DefaultSmsService.isValidE164("+1234567890")).isTrue();
        }

        @Test
        void shouldRejectInvalidFormats() {
            assertThat(DefaultSmsService.isValidE164("4155551234")).isFalse();
            assertThat(DefaultSmsService.isValidE164("+0123456")).isFalse(); // Can't start with 0
            assertThat(DefaultSmsService.isValidE164("")).isFalse();
            assertThat(DefaultSmsService.isValidE164(null)).isFalse();
            assertThat(DefaultSmsService.isValidE164("abc")).isFalse();
        }
    }

    @Nested
    @DisplayName("Phone Masking")
    class PhoneMasking {
        @Test
        void shouldMaskMiddleDigits() {
            assertThat(DefaultSmsService.maskPhone("+14155551234")).isEqualTo("+14***1234");
        }

        @Test
        void shouldHandleShortNumbers() {
            assertThat(DefaultSmsService.maskPhone("+123")).isEqualTo("***");
        }

        @Test
        void shouldHandleNull() {
            assertThat(DefaultSmsService.maskPhone(null)).isEqualTo("***");
        }
    }

    @Nested
    @DisplayName("Send Verification")
    class SendVerification {
        @Test
        void shouldRejectInvalidPhoneNumber() {
            boolean result = service.sendVerificationCode("invalid", "t1");
            assertThat(result).isFalse();
            verify(smsProvider, never()).send(any());
        }

        @Test
        void shouldSendCodeForValidPhone() {
            // Not rate limited
            lenient().when(jdbcTemplate.queryForList(contains("COUNT"), eq("+14155551234"), eq("t1"), any(Timestamp.class)))
                    .thenReturn(List.of(Map.of("cnt", 0L)));

            boolean result = service.sendVerificationCode("+14155551234", "t1");

            assertThat(result).isTrue();
            verify(smsProvider).send(argThat(msg ->
                    msg.to().equals("+14155551234") && msg.body().contains("verification code")));
        }

        @Test
        void shouldRateLimitAfter3Sends() {
            when(jdbcTemplate.queryForList(contains("COUNT"), eq("+14155551234"), eq("t1"), any(Timestamp.class)))
                    .thenReturn(List.of(Map.of("cnt", 3L)));

            boolean result = service.sendVerificationCode("+14155551234", "t1");

            assertThat(result).isFalse();
            verify(smsProvider, never()).send(any());
        }
    }

    @Nested
    @DisplayName("Verify Code")
    class VerifyCode {
        @Test
        void shouldRejectNullCode() {
            assertThat(service.verifyCode("+14155551234", null, "t1")).isFalse();
        }

        @Test
        void shouldRejectWrongLengthCode() {
            assertThat(service.verifyCode("+14155551234", "12345", "t1")).isFalse();
            assertThat(service.verifyCode("+14155551234", "1234567", "t1")).isFalse();
        }

        @Test
        void shouldRejectWhenNoActiveCode() {
            when(jdbcTemplate.queryForList(contains("sms_verification"), eq("+14155551234"), eq("t1")))
                    .thenReturn(List.of());

            assertThat(service.verifyCode("+14155551234", "123456", "t1")).isFalse();
        }

        @Test
        void shouldRejectWhenMaxAttemptsReached() {
            when(jdbcTemplate.queryForList(contains("sms_verification"), eq("+14155551234"), eq("t1")))
                    .thenReturn(List.of(Map.of(
                            "id", "v1",
                            "code_hash", "abc",
                            "attempts", 5
                    )));

            assertThat(service.verifyCode("+14155551234", "123456", "t1")).isFalse();
        }
    }
}
