package com.emf.controlplane.config;

import com.emf.controlplane.entity.User;
import com.emf.controlplane.service.UserService;
import com.emf.controlplane.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoginTrackingFilter.
 *
 * Verifies that the filter correctly tracks user logins after JWT authentication,
 * including JIT provisioning, login history recording, and throttling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LoginTrackingFilter Tests")
class LoginTrackingFilterTest {

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private LoginTrackingFilter filter;

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-456";
    private static final String EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        filter = new LoginTrackingFilter(userService, null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    private Jwt createJwt(String email, String preferredUsername, String givenName, String familyName) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "sub-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        if (email != null) {
            builder.claim("email", email);
        }
        if (preferredUsername != null) {
            builder.claim("preferred_username", preferredUsername);
        }
        if (givenName != null) {
            builder.claim("given_name", givenName);
        }
        if (familyName != null) {
            builder.claim("family_name", familyName);
        }

        return builder.build();
    }

    private void setUpAuthentication(Jwt jwt) {
        JwtAuthenticationToken authToken = new JwtAuthenticationToken(jwt, Collections.emptyList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);
    }

    private User createUser() {
        User user = new User(EMAIL, "Test", "User");
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        return user;
    }

    @Nested
    @DisplayName("Authenticated Request Tests")
    class AuthenticatedRequestTests {

        @Test
        @DisplayName("Should provision user and record login on first authenticated request")
        void shouldProvisionAndRecordLoginOnFirstRequest() throws Exception {
            // Arrange
            Jwt jwt = createJwt(EMAIL, "testuser", "Test", "User");
            setUpAuthentication(jwt);
            TenantContextHolder.set(TENANT_ID, "default");

            User user = createUser();
            when(userService.provisionOrUpdate(TENANT_ID, EMAIL, "Test", "User", "testuser"))
                    .thenReturn(user);
            when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 Test Browser");

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(userService).provisionOrUpdate(TENANT_ID, EMAIL, "Test", "User", "testuser");
            verify(userService).recordLogin(eq(USER_ID), eq(TENANT_ID), eq("192.168.1.1"),
                    eq("OAUTH"), eq("SUCCESS"), eq("Mozilla/5.0 Test Browser"));
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should extract email from preferred_username when email claim is missing")
        void shouldExtractEmailFromPreferredUsername() throws Exception {
            // Arrange
            Jwt jwt = createJwt(null, "user@example.com", "First", "Last");
            setUpAuthentication(jwt);
            TenantContextHolder.set(TENANT_ID, "default");

            User user = createUser();
            when(userService.provisionOrUpdate(eq(TENANT_ID), eq("user@example.com"), any(), any(), any()))
                    .thenReturn(user);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(userService).provisionOrUpdate(TENANT_ID, "user@example.com", "First", "Last", "user@example.com");
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should use remote address when X-Forwarded-For is missing")
        void shouldUseRemoteAddrWhenNoForwardedHeader() throws Exception {
            // Arrange
            Jwt jwt = createJwt(EMAIL, "testuser", null, null);
            setUpAuthentication(jwt);
            TenantContextHolder.set(TENANT_ID, "default");

            User user = createUser();
            when(userService.provisionOrUpdate(eq(TENANT_ID), eq(EMAIL), any(), any(), any()))
                    .thenReturn(user);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("10.0.0.1");

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(userService).recordLogin(eq(USER_ID), eq(TENANT_ID), eq("10.0.0.1"),
                    eq("OAUTH"), eq("SUCCESS"), any());
        }

        @Test
        @DisplayName("Should extract first IP from X-Forwarded-For with multiple entries")
        void shouldExtractFirstIpFromForwardedHeader() throws Exception {
            // Arrange
            Jwt jwt = createJwt(EMAIL, "testuser", null, null);
            setUpAuthentication(jwt);
            TenantContextHolder.set(TENANT_ID, "default");

            User user = createUser();
            when(userService.provisionOrUpdate(eq(TENANT_ID), eq(EMAIL), any(), any(), any()))
                    .thenReturn(user);
            when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 70.41.3.18, 150.172.238.178");

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(userService).recordLogin(eq(USER_ID), eq(TENANT_ID), eq("203.0.113.1"),
                    eq("OAUTH"), eq("SUCCESS"), any());
        }
    }

    @Nested
    @DisplayName("Throttling Tests")
    class ThrottlingTests {

        @Test
        @DisplayName("Should not track login on subsequent requests within the throttle window")
        void shouldNotTrackWithinThrottleWindow() throws Exception {
            // Arrange
            Jwt jwt = createJwt(EMAIL, "testuser", "Test", "User");
            setUpAuthentication(jwt);
            TenantContextHolder.set(TENANT_ID, "default");

            User user = createUser();
            when(userService.provisionOrUpdate(eq(TENANT_ID), eq(EMAIL), any(), any(), any()))
                    .thenReturn(user);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            // Act - first request should be tracked
            filter.doFilterInternal(request, response, filterChain);

            // Act - second request should be throttled
            filter.doFilterInternal(request, response, filterChain);

            // Assert - provisionOrUpdate and recordLogin should only be called once
            verify(userService, times(1)).provisionOrUpdate(eq(TENANT_ID), eq(EMAIL), any(), any(), any());
            verify(userService, times(1)).recordLogin(any(), any(), any(), any(), any(), any());
            // But filterChain should be called twice
            verify(filterChain, times(2)).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Skip Conditions Tests")
    class SkipConditionsTests {

        @Test
        @DisplayName("Should skip when no authentication is present")
        void shouldSkipWhenNoAuthentication() throws Exception {
            // Arrange - no authentication set
            SecurityContextHolder.clearContext();

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(userService, never()).provisionOrUpdate(any(), any(), any(), any(), any());
            verify(userService, never()).recordLogin(any(), any(), any(), any(), any(), any());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should skip when tenant context is missing")
        void shouldSkipWhenNoTenantContext() throws Exception {
            // Arrange
            Jwt jwt = createJwt(EMAIL, "testuser", "Test", "User");
            setUpAuthentication(jwt);
            // No tenant context set

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(userService, never()).provisionOrUpdate(any(), any(), any(), any(), any());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should skip when email cannot be extracted from JWT")
        void shouldSkipWhenNoEmailInJwt() throws Exception {
            // Arrange - no email claim and preferred_username is not an email
            Jwt jwt = createJwt(null, "just-a-username", null, null);
            setUpAuthentication(jwt);
            TenantContextHolder.set(TENANT_ID, "default");

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(userService, never()).provisionOrUpdate(any(), any(), any(), any(), any());
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should not block request when login tracking throws exception")
        void shouldNotBlockRequestOnTrackingFailure() throws Exception {
            // Arrange
            Jwt jwt = createJwt(EMAIL, "testuser", "Test", "User");
            setUpAuthentication(jwt);
            TenantContextHolder.set(TENANT_ID, "default");

            when(userService.provisionOrUpdate(any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Database error"));
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert - request should still proceed
            verify(filterChain).doFilter(request, response);
        }
    }
}
