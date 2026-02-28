package com.emf.worker.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link LoginTrackingFilter}.
 */
class LoginTrackingFilterTest {

    private JdbcTemplate jdbcTemplate;
    private Map<String, Long> throttleCache;
    private LoginTrackingFilter filter;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        throttleCache = new ConcurrentHashMap<>();
        filter = new LoginTrackingFilter(jdbcTemplate, throttleCache);
    }

    @Test
    void shouldTrackLoginWhenValidHeadersAndUserFound() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/accounts");
        request.addHeader("X-User-Id", "alice@example.com");
        request.addHeader("X-Tenant-ID", "tenant-123");
        request.setRemoteAddr("192.168.1.1");
        request.addHeader("User-Agent", "Mozilla/5.0");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // Mock user lookup
        when(jdbcTemplate.queryForObject(
                eq("SELECT id FROM platform_user WHERE tenant_id = ? AND email = ? LIMIT 1"),
                eq(String.class), eq("tenant-123"), eq("alice@example.com")))
                .thenReturn("user-uuid-1");

        filter.doFilterInternal(request, response, chain);

        // Filter chain always continues
        verify(chain).doFilter(request, response);

        // platform_user should be updated (last_login_at + login_count)
        verify(jdbcTemplate).update(
                contains("UPDATE platform_user SET last_login_at"),
                any(), any(), eq("user-uuid-1"));

        // login_history row should be inserted
        verify(jdbcTemplate).update(
                contains("INSERT INTO login_history"),
                any(String.class), eq("user-uuid-1"), eq("tenant-123"),
                any(), eq("192.168.1.1"), eq("OAUTH"), eq("SUCCESS"),
                eq("Mozilla/5.0"), any(), any());

        // security_audit_log row should be inserted
        verify(jdbcTemplate).update(
                contains("INSERT INTO security_audit_log"),
                any(String.class), eq("tenant-123"), eq("LOGIN_SUCCESS"), eq("AUTH"),
                eq("user-uuid-1"), eq("alice@example.com"),
                eq("USER"), eq("user-uuid-1"), eq("alice@example.com"),
                any(String.class), eq("192.168.1.1"), eq("Mozilla/5.0"), any());

        // Throttle cache should be populated
        assertTrue(throttleCache.containsKey("tenant-123:alice@example.com"));
    }

    @Test
    void shouldSkipTrackingWhenUserIdHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/accounts");
        request.addHeader("X-Tenant-ID", "tenant-123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void shouldSkipTrackingWhenTenantIdHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/accounts");
        request.addHeader("X-User-Id", "alice@example.com");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void shouldSkipTrackingWhenUserNotFoundInDatabase() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/accounts");
        request.addHeader("X-User-Id", "unknown@example.com");
        request.addHeader("X-Tenant-ID", "tenant-123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(), any()))
                .thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        // Only the SELECT query should run, no inserts/updates
        verify(jdbcTemplate).queryForObject(anyString(), eq(String.class), any(), any());
        verify(jdbcTemplate, never()).update(anyString(), (Object[]) any());
    }

    @Test
    void shouldThrottleWithin30MinuteInterval() throws ServletException, IOException {
        // Pre-populate the throttle cache as if user was tracked recently
        long recentTime = java.time.Instant.now().getEpochSecond() - 60; // 1 minute ago
        throttleCache.put("tenant-123:alice@example.com", recentTime);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/accounts");
        request.addHeader("X-User-Id", "alice@example.com");
        request.addHeader("X-Tenant-ID", "tenant-123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        // Should NOT execute any DB operations due to throttling
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldAllowTrackingAfterIntervalExpires() throws ServletException, IOException {
        // Pre-populate the throttle cache with an old timestamp (31 minutes ago)
        long oldTime = java.time.Instant.now().getEpochSecond()
                - LoginTrackingFilter.TRACKING_INTERVAL_SECONDS - 60;
        throttleCache.put("tenant-123:alice@example.com", oldTime);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/accounts");
        request.addHeader("X-User-Id", "alice@example.com");
        request.addHeader("X-Tenant-ID", "tenant-123");
        request.setRemoteAddr("10.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(), any()))
                .thenReturn("user-uuid-1");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        // DB operations should execute because the throttle interval has expired
        verify(jdbcTemplate).update(contains("UPDATE platform_user"), any(), any(), any());
        verify(jdbcTemplate).update(contains("INSERT INTO login_history"),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldNeverBlockFilterChainOnException() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/accounts");
        request.addHeader("X-User-Id", "alice@example.com");
        request.addHeader("X-Tenant-ID", "tenant-123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // Make the DB lookup throw an exception
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(), any()))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Should NOT throw — the filter chain must continue
        assertDoesNotThrow(() -> filter.doFilterInternal(request, response, chain));
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldExtractClientIpFromXForwardedForHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18, 150.172.238.178");
        request.setRemoteAddr("127.0.0.1");

        String ip = LoginTrackingFilter.extractClientIp(request);
        assertEquals("203.0.113.50", ip);
    }

    @Test
    void shouldFallBackToRemoteAddrWhenNoForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        String ip = LoginTrackingFilter.extractClientIp(request);
        assertEquals("192.168.1.100", ip);
    }

    @Test
    void shouldTruncateUserAgentExceeding500Chars() {
        String longAgent = "A".repeat(600);
        String truncated = LoginTrackingFilter.truncateUserAgent(longAgent);
        assertNotNull(truncated);
        assertEquals(500, truncated.length());
    }

    @Test
    void shouldReturnNullForNullUserAgent() {
        assertNull(LoginTrackingFilter.truncateUserAgent(null));
    }

    @Test
    void shouldNotTruncateShortUserAgent() {
        String agent = "Mozilla/5.0";
        assertEquals("Mozilla/5.0", LoginTrackingFilter.truncateUserAgent(agent));
    }

    @Test
    void shouldSkipTrackingWhenUserIdLooksLikeUuid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/accounts");
        request.addHeader("X-User-Id", "a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        request.addHeader("X-Tenant-ID", "tenant-123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldTrackDifferentUsersIndependently() throws ServletException, IOException {
        FilterChain chain = mock(FilterChain.class);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-123"), eq("alice@example.com")))
                .thenReturn("user-1");
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-123"), eq("bob@example.com")))
                .thenReturn("user-2");

        // Track alice
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/collections/accounts");
        req1.addHeader("X-User-Id", "alice@example.com");
        req1.addHeader("X-Tenant-ID", "tenant-123");
        filter.doFilterInternal(req1, new MockHttpServletResponse(), chain);

        // Track bob
        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/collections/accounts");
        req2.addHeader("X-User-Id", "bob@example.com");
        req2.addHeader("X-Tenant-ID", "tenant-123");
        filter.doFilterInternal(req2, new MockHttpServletResponse(), chain);

        // Both should have been tracked
        assertTrue(throttleCache.containsKey("tenant-123:alice@example.com"));
        assertTrue(throttleCache.containsKey("tenant-123:bob@example.com"));

        // Two UPDATE queries (one per user)
        verify(jdbcTemplate, times(2)).update(contains("UPDATE platform_user"), any(), any(), any());
    }
}
