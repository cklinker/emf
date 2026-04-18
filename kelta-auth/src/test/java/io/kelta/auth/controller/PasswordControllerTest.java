package io.kelta.auth.controller;

import io.kelta.auth.config.AuthProperties;
import io.kelta.auth.service.PasswordPolicyService;
import io.kelta.auth.service.WorkerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordController")
class PasswordControllerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private WorkerClient workerClient;

    @Mock
    private PasswordPolicyService policyService;

    @Mock
    private Authentication authentication;

    private PasswordController controller;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties();
        props.setUiBaseUrl("https://app.kelta.io");
        controller = new PasswordController(jdbcTemplate, passwordEncoder, workerClient, policyService, props);
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("returns 400 when current password is incorrect")
        void returns400WhenCurrentPasswordIncorrect() {
            when(authentication.getName()).thenReturn("user@test.com");
            when(jdbcTemplate.queryForObject(contains("password_hash"), eq(String.class), eq("user@test.com")))
                    .thenReturn("$2a$hashed");
            when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

            var request = new PasswordController.ChangePasswordRequest("wrong", "newpassword123");
            ResponseEntity<Map<String, String>> result = controller.changePassword(request, authentication);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(result.getBody().get("error")).contains("incorrect");
        }

        @Test
        @DisplayName("returns 400 when new password violates policy")
        void returns400WhenPolicyViolation() {
            when(authentication.getName()).thenReturn("user@test.com");
            when(jdbcTemplate.queryForObject(contains("password_hash"), eq(String.class), eq("user@test.com")))
                    .thenReturn("$2a$hashed");
            when(passwordEncoder.matches("current123", "$2a$hashed")).thenReturn(true);
            when(jdbcTemplate.queryForList(contains("platform_user"), eq("user@test.com")))
                    .thenReturn(List.of(Map.of("id", "user-1", "tenant_id", "t1", "first_name", "John", "last_name", "Doe")));
            when(policyService.validatePassword(eq("weak"), eq("user@test.com"), anyString(), eq("t1")))
                    .thenReturn(List.of("Password too short"));

            var request = new PasswordController.ChangePasswordRequest("current123", "weak");
            ResponseEntity<Map<String, String>> result = controller.changePassword(request, authentication);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(result.getBody().get("error")).contains("Password too short");
        }

        @Test
        @DisplayName("returns 400 when the user's credential row is missing (EmptyResult)")
        void returns400WhenCredentialRowMissing() {
            when(authentication.getName()).thenReturn("ghost@test.com");
            when(jdbcTemplate.queryForObject(contains("password_hash"), eq(String.class), eq("ghost@test.com")))
                    .thenThrow(new EmptyResultDataAccessException(1));

            var request = new PasswordController.ChangePasswordRequest("anything", "newpassword123");
            ResponseEntity<Map<String, String>> result = controller.changePassword(request, authentication);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            // Same generic message as a wrong password — don't leak account existence.
            assertThat(result.getBody().get("error")).contains("incorrect");
            // No subsequent DB work should have happened.
            verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("changes password successfully")
        void changesPasswordSuccessfully() {
            when(authentication.getName()).thenReturn("user@test.com");
            when(jdbcTemplate.queryForObject(contains("password_hash"), eq(String.class), eq("user@test.com")))
                    .thenReturn("$2a$hashed");
            when(passwordEncoder.matches("current123", "$2a$hashed")).thenReturn(true);
            when(jdbcTemplate.queryForList(contains("platform_user"), eq("user@test.com")))
                    .thenReturn(List.of(Map.of("id", "user-1", "tenant_id", "t1", "first_name", "John", "last_name", "Doe")));
            when(policyService.validatePassword(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(List.of());
            when(policyService.checkHistory("user-1", "newpassword123", "t1"))
                    .thenReturn(Optional.empty());
            when(passwordEncoder.encode("newpassword123")).thenReturn("$2a$newhash");

            var request = new PasswordController.ChangePasswordRequest("current123", "newpassword123");
            ResponseEntity<Map<String, String>> result = controller.changePassword(request, authentication);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jdbcTemplate).update(contains("UPDATE user_credential SET password_hash"),
                    eq("$2a$newhash"), any(Instant.class), any(Instant.class), eq("user@test.com"));
        }
    }

    @Nested
    @DisplayName("requestPasswordReset")
    class RequestPasswordReset {

        @Test
        @DisplayName("always returns success to prevent email enumeration")
        void alwaysReturnsSuccess() {
            when(jdbcTemplate.update(contains("reset_token = NULL"), anyString())).thenReturn(0);
            when(jdbcTemplate.update(contains("reset_token = ?"), anyString(), any(), any(), anyString())).thenReturn(0);

            var request = new PasswordController.ResetRequestBody("nonexistent@test.com");
            ResponseEntity<Map<String, String>> result = controller.requestPasswordReset(request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("returns 400 for invalid token")
        void returns400ForInvalidToken() {
            when(jdbcTemplate.queryForList(contains("reset_token"), eq("bad-token")))
                    .thenReturn(List.of());

            var request = new PasswordController.ResetPasswordRequest("bad-token", "newpassword123");
            ResponseEntity<Map<String, String>> result = controller.resetPassword(request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(result.getBody().get("error")).contains("expired");
        }

        @Test
        @DisplayName("returns 400 for expired token")
        void returns400ForExpiredToken() {
            when(jdbcTemplate.queryForList(contains("reset_token"), eq("expired-token")))
                    .thenReturn(List.of(Map.of(
                            "user_id", "user-1",
                            "reset_token_expires_at", Timestamp.from(Instant.now().minusSeconds(3600))
                    )));

            var request = new PasswordController.ResetPasswordRequest("expired-token", "newpassword123");
            ResponseEntity<Map<String, String>> result = controller.resetPassword(request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("returns 400 (no NPE) when stored expires_at is NULL")
        void returns400WhenExpiresAtNull() {
            // A reset_token row with null expires_at is a DB invariant violation;
            // we must treat it as invalid rather than NPE on the cast/.toInstant().
            Map<String, Object> row = new HashMap<>();
            row.put("user_id", "user-1");
            row.put("reset_token_expires_at", null);
            when(jdbcTemplate.queryForList(contains("reset_token"), eq("null-expires-token")))
                    .thenReturn(List.of(row));

            var request = new PasswordController.ResetPasswordRequest("null-expires-token", "newpassword123");
            ResponseEntity<Map<String, String>> result = controller.resetPassword(request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(result.getBody().get("error")).contains("expired");
            verify(jdbcTemplate, never()).update(contains("password_hash = ?"),
                    any(), any(), any(), any());
        }

        @Test
        @DisplayName("resets password with valid token")
        void resetsPasswordWithValidToken() {
            when(jdbcTemplate.queryForList(contains("reset_token"), eq("valid-token")))
                    .thenReturn(List.of(Map.of(
                            "user_id", "user-1",
                            "reset_token_expires_at", Timestamp.from(Instant.now().plusSeconds(3600))
                    )));
            when(passwordEncoder.encode("newpassword123")).thenReturn("$2a$newhash");

            var request = new PasswordController.ResetPasswordRequest("valid-token", "newpassword123");
            ResponseEntity<Map<String, String>> result = controller.resetPassword(request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jdbcTemplate).update(contains("password_hash = ?"),
                    eq("$2a$newhash"), any(Instant.class), any(Instant.class), eq("user-1"));
        }
    }
}
