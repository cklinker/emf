package io.kelta.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantContextFilter Tests")
class TenantContextFilterTest {

    private TenantContextFilter filter;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;
    @Mock private HttpSession session;

    @BeforeEach
    void setUp() {
        filter = new TenantContextFilter();
    }

    @Test
    @DisplayName("should set tenant slug from redirect_uri on /oauth2/authorize")
    void shouldSetTenantSlugFromRedirectUri() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/oauth2/authorize");
        when(request.getParameter("redirect_uri"))
                .thenReturn("https://app.rzware.com/default/auth/callback");
        when(request.getSession()).thenReturn(session);

        filter.doFilterInternal(request, response, filterChain);

        verify(session).setAttribute("tenantId", "default");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should not set tenant when no redirect_uri parameter")
    void shouldNotSetTenantWhenNoRedirectUri() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/oauth2/authorize");
        when(request.getParameter("redirect_uri")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(session, never()).setAttribute(anyString(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should not set tenant when path does not match /{slug}/auth/callback")
    void shouldNotSetTenantWhenPathDoesNotMatch() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/oauth2/authorize");
        when(request.getParameter("redirect_uri"))
                .thenReturn("https://app.rzware.com/auth/callback");

        filter.doFilterInternal(request, response, filterChain);

        verify(session, never()).setAttribute(anyString(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should not set tenant on non-authorize requests")
    void shouldNotSetTenantOnNonAuthorizeRequest() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        lenient().when(request.getServletPath()).thenReturn("/login");

        filter.doFilterInternal(request, response, filterChain);

        verify(session, never()).setAttribute(anyString(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should always call filter chain")
    void shouldAlwaysCallFilterChain() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/oauth2/authorize");
        when(request.getParameter("redirect_uri")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should handle invalid redirect_uri without throwing")
    void shouldHandleInvalidRedirectUri() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/oauth2/authorize");
        when(request.getParameter("redirect_uri")).thenReturn("not a valid uri %%");

        filter.doFilterInternal(request, response, filterChain);

        verify(session, never()).setAttribute(anyString(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should clear Spring Security context when tenant slug changes")
    void shouldClearSecurityContextWhenTenantChanges() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/oauth2/authorize");
        when(request.getParameter("redirect_uri"))
                .thenReturn("https://app.rzware.com/default/auth/callback");
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("tenantId")).thenReturn("threadline-clothing");

        filter.doFilterInternal(request, response, filterChain);

        verify(session).removeAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        verify(session).setAttribute("tenantId", "default");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should not clear Spring Security context when tenant slug is unchanged")
    void shouldNotClearSecurityContextWhenTenantSame() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/oauth2/authorize");
        when(request.getParameter("redirect_uri"))
                .thenReturn("https://app.rzware.com/default/auth/callback");
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("tenantId")).thenReturn("default");

        filter.doFilterInternal(request, response, filterChain);

        verify(session, never()).removeAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        verify(session).setAttribute("tenantId", "default");
        verify(filterChain).doFilter(request, response);
    }
}
