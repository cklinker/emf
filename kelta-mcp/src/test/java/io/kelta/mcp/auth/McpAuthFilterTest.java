package io.kelta.mcp.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class McpAuthFilterTest {

    private final McpAuthFilter filter = new McpAuthFilter();

    @Test
    void rejectsRequestWithoutAuthorizationHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/user");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getHeader("WWW-Authenticate")).contains("missing_bearer");
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void rejectsBearerTokenWithoutKltPrefix() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/user");
        req.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.something.signature");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getHeader("WWW-Authenticate")).contains("invalid_token_prefix");
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void rejectsAuthorizationWithoutBearerScheme() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/admin");
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void allowsValidKltBearerToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/user");
        req.addHeader("Authorization", "Bearer klt_abc123def456");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(res));
    }

    @Test
    void skipsFilterForNonMcpPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void stripsClientSuppliedTenantHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/user");
        req.addHeader("Authorization", "Bearer klt_abc");
        req.addHeader("X-Tenant-ID", "00000000-0000-0000-0000-000000000999");
        req.addHeader("X-Tenant-Slug", "evil-tenant");
        req.addHeader("X-User-Id", "attacker@example.com");
        req.addHeader("X-Trace-Id", "preserved");
        MockHttpServletResponse res = new MockHttpServletResponse();

        ArgumentCaptorChain chain = new ArgumentCaptorChain();

        filter.doFilter(req, res, chain);

        HttpServletRequest forwarded = chain.captured;
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeader("X-Tenant-ID")).isNull();
        assertThat(forwarded.getHeader("X-Tenant-Slug")).isNull();
        assertThat(forwarded.getHeader("X-User-Id")).isNull();
        assertThat(forwarded.getHeader("Authorization")).isEqualTo("Bearer klt_abc");
        assertThat(forwarded.getHeader("X-Trace-Id")).isEqualTo("preserved");
        assertThat(Collections.list(forwarded.getHeaderNames()))
                .doesNotContain("X-Tenant-ID", "X-Tenant-Slug", "X-User-Id");
    }

    private static final class ArgumentCaptorChain implements FilterChain {
        HttpServletRequest captured;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            this.captured = (HttpServletRequest) request;
        }
    }
}
