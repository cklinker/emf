package io.kelta.worker.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CerbosPermissionResolver Tests")
class CerbosPermissionResolverTest {

    private CerbosPermissionResolver resolver;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        resolver = new CerbosPermissionResolver();
        request = mock(HttpServletRequest.class);
    }

    @Nested
    @DisplayName("Header Extraction")
    class HeaderExtraction {

        @Test
        void shouldGetEmail() {
            when(request.getHeader("X-User-Email")).thenReturn("user@example.com");
            assertEquals("user@example.com", resolver.getEmail(request));
        }

        @Test
        void shouldGetProfileId() {
            when(request.getHeader("X-User-Profile-Id")).thenReturn("prof-123");
            assertEquals("prof-123", resolver.getProfileId(request));
        }

        @Test
        void shouldGetProfileName() {
            when(request.getHeader("X-User-Profile-Name")).thenReturn("Admin");
            assertEquals("Admin", resolver.getProfileName(request));
        }

        @Test
        void shouldGetTenantId() {
            when(request.getHeader("X-Cerbos-Scope")).thenReturn("tenant-1");
            assertEquals("tenant-1", resolver.getTenantId(request));
        }

        @Test
        void shouldReturnNullForMissingHeaders() {
            assertNull(resolver.getEmail(request));
            assertNull(resolver.getProfileId(request));
            assertNull(resolver.getProfileName(request));
            assertNull(resolver.getTenantId(request));
        }
    }

    @Nested
    @DisplayName("hasIdentity")
    class HasIdentity {

        @Test
        void shouldReturnTrueWhenAllPresent() {
            when(request.getHeader("X-User-Email")).thenReturn("user@example.com");
            when(request.getHeader("X-User-Profile-Id")).thenReturn("prof-123");
            when(request.getHeader("X-Cerbos-Scope")).thenReturn("tenant-1");
            assertTrue(resolver.hasIdentity(request));
        }

        @Test
        void shouldReturnFalseWhenEmailMissing() {
            when(request.getHeader("X-User-Profile-Id")).thenReturn("prof-123");
            when(request.getHeader("X-Cerbos-Scope")).thenReturn("tenant-1");
            assertFalse(resolver.hasIdentity(request));
        }

        @Test
        void shouldReturnFalseWhenProfileIdMissing() {
            when(request.getHeader("X-User-Email")).thenReturn("user@example.com");
            when(request.getHeader("X-Cerbos-Scope")).thenReturn("tenant-1");
            assertFalse(resolver.hasIdentity(request));
        }

        @Test
        void shouldReturnFalseWhenTenantIdMissing() {
            when(request.getHeader("X-User-Email")).thenReturn("user@example.com");
            when(request.getHeader("X-User-Profile-Id")).thenReturn("prof-123");
            assertFalse(resolver.hasIdentity(request));
        }

        @Test
        void shouldReturnFalseWhenEmailEmpty() {
            when(request.getHeader("X-User-Email")).thenReturn("");
            when(request.getHeader("X-User-Profile-Id")).thenReturn("prof-123");
            when(request.getHeader("X-Cerbos-Scope")).thenReturn("tenant-1");
            assertFalse(resolver.hasIdentity(request));
        }

        @Test
        void shouldReturnFalseWhenProfileIdEmpty() {
            when(request.getHeader("X-User-Email")).thenReturn("user@example.com");
            when(request.getHeader("X-User-Profile-Id")).thenReturn("");
            when(request.getHeader("X-Cerbos-Scope")).thenReturn("tenant-1");
            assertFalse(resolver.hasIdentity(request));
        }

        @Test
        void shouldReturnFalseWhenTenantIdEmpty() {
            when(request.getHeader("X-User-Email")).thenReturn("user@example.com");
            when(request.getHeader("X-User-Profile-Id")).thenReturn("prof-123");
            when(request.getHeader("X-Cerbos-Scope")).thenReturn("");
            assertFalse(resolver.hasIdentity(request));
        }
    }
}
