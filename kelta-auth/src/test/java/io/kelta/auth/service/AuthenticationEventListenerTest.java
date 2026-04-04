package io.kelta.auth.service;

import io.kelta.auth.model.KeltaUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationEventListener Tests")
class AuthenticationEventListenerTest {

    @Mock private PasswordPolicyService policyService;
    @Mock private JdbcTemplate jdbcTemplate;
    private AuthenticationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuthenticationEventListener(policyService, jdbcTemplate);
    }

    @Nested
    @DisplayName("onSuccess")
    class OnSuccess {
        @Test
        void shouldResetFailedAttemptsAndCheckExpiration() {
            KeltaUserDetails userDetails = new KeltaUserDetails(
                    "user-1", "user@test.com", "tenant-1", "profile-1",
                    "Standard User", "Test User", "hash", true, false, false);
            Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null);
            AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth);

            listener.onSuccess(event);

            verify(policyService).resetFailedAttempts("user-1");
            verify(policyService).checkPasswordExpiration("user-1", "tenant-1");
        }

        @Test
        void shouldDoNothingForNonKeltaPrincipal() {
            Authentication auth = new UsernamePasswordAuthenticationToken("plain-user", "password");
            AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth);

            listener.onSuccess(event);

            verifyNoInteractions(policyService);
        }
    }

    @Nested
    @DisplayName("onFailure")
    class OnFailure {
        @Test
        void shouldIncrementFailedAttemptsForKnownUser() {
            Authentication auth = new UsernamePasswordAuthenticationToken("user@test.com", "bad-password");
            AuthenticationFailureBadCredentialsEvent event =
                    new AuthenticationFailureBadCredentialsEvent(auth, new org.springframework.security.authentication.BadCredentialsException("bad"));

            when(jdbcTemplate.queryForList(anyString(), eq("user@test.com")))
                    .thenReturn(List.of(Map.of("id", "user-1", "tenant_id", "tenant-1")));

            listener.onFailure(event);

            verify(policyService).incrementFailedAttempts("user-1", "tenant-1");
        }

        @Test
        void shouldDoNothingForUnknownUser() {
            Authentication auth = new UsernamePasswordAuthenticationToken("unknown@test.com", "password");
            AuthenticationFailureBadCredentialsEvent event =
                    new AuthenticationFailureBadCredentialsEvent(auth, new org.springframework.security.authentication.BadCredentialsException("bad"));

            when(jdbcTemplate.queryForList(anyString(), eq("unknown@test.com")))
                    .thenReturn(List.of());

            listener.onFailure(event);

            verifyNoInteractions(policyService);
        }
    }
}
