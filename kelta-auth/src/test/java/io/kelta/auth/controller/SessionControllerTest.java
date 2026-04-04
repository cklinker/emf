package io.kelta.auth.controller;

import io.kelta.auth.config.AuthProperties;
import io.kelta.auth.model.KeltaSession;
import io.kelta.auth.service.ExternalTokenValidator;
import io.kelta.auth.service.SessionService;
import io.kelta.auth.service.WorkerClient;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionController")
class SessionControllerTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private ExternalTokenValidator tokenValidator;

    @Mock
    private WorkerClient workerClient;

    @Mock
    private HttpServletResponse response;

    private SessionController controller;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties();
        props.setCookieDomain("localhost");
        controller = new SessionController(sessionService, tokenValidator, workerClient, props);
    }

    @Nested
    @DisplayName("createSession")
    class CreateSession {

        @Test
        @DisplayName("returns 400 for invalid auth header")
        void returns400ForInvalidAuthHeader() {
            ResponseEntity<Map<String, String>> result =
                    controller.createSession("InvalidHeader", null, null, response);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("returns 401 for invalid token")
        void returns401ForInvalidToken() {
            when(tokenValidator.validate("bad-token")).thenReturn(Optional.empty());

            ResponseEntity<Map<String, String>> result =
                    controller.createSession("Bearer bad-token", null, null, response);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("creates session and sets cookie on valid token")
        void createsSessionOnValidToken() {
            var tokenInfo = new ExternalTokenValidator.ValidatedToken("user@test.com", "tenant-1", "John Doe", "sub-123", "https://accounts.google.com");
            when(tokenValidator.validate("valid-token")).thenReturn(Optional.of(tokenInfo));
            when(workerClient.findUserIdentity("user@test.com", "tenant-1")).thenReturn(Optional.empty());
            when(sessionService.createSession(any(KeltaSession.class))).thenReturn("session-abc");

            ResponseEntity<Map<String, String>> result =
                    controller.createSession("Bearer valid-token", "tenant-1", "acme", response);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);

            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            Cookie cookie = cookieCaptor.getValue();
            assertThat(cookie.getName()).isEqualTo("kelta_session");
            assertThat(cookie.getValue()).isEqualTo("session-abc");
            assertThat(cookie.isHttpOnly()).isTrue();
        }
    }

    @Nested
    @DisplayName("deleteSession")
    class DeleteSession {

        @Test
        @DisplayName("deletes session and clears cookie")
        void deletesSessionAndClearsCookie() {
            ResponseEntity<Void> result = controller.deleteSession("session-abc", response);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(sessionService).deleteSession("session-abc");

            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            Cookie cookie = cookieCaptor.getValue();
            assertThat(cookie.getMaxAge()).isEqualTo(0);
        }

        @Test
        @DisplayName("clears cookie even without session ID")
        void clearsCookieWithoutSession() {
            ResponseEntity<Void> result = controller.deleteSession(null, response);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(sessionService, never()).deleteSession(anyString());
            verify(response).addCookie(any(Cookie.class));
        }
    }
}
