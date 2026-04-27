package io.kelta.mcp.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class McpAuthFilterTest {

    private static final String SLUG = "threadline-clothing";
    private static final String URL_USER = "/" + SLUG + "/mcp/user";
    private static final String URL_ADMIN = "/" + SLUG + "/mcp/admin";

    private final McpAuthFilter filter = new McpAuthFilter();

    @Test
    void rejectsRequestWithoutAuthorizationHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", URL_USER);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getHeader("WWW-Authenticate")).contains("missing_bearer");
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void rejectsBearerTokenWithoutKltPrefix() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", URL_USER);
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
        MockHttpServletRequest req = new MockHttpServletRequest("POST", URL_ADMIN);
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void rejectsMcpUrlWithoutSlugPrefix() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/user");
        req.addHeader("Authorization", "Bearer klt_abc");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        // Slug is required — pre-slug URL is treated as not-found.
        assertThat(res.getStatus()).isEqualTo(404);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void allowsValidKltBearerTokenWithSlugInUrl() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", URL_USER);
        req.addHeader("Authorization", "Bearer klt_abc123def456");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(res));
    }

    @Test
    void setsPatHolderAndSlugAttributeDuringChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", URL_USER);
        req.addHeader("Authorization", "Bearer klt_xyz_during_chain");
        MockHttpServletResponse res = new MockHttpServletResponse();

        String[] seenPat = new String[1];
        Object[] seenSlug = new Object[1];
        FilterChain chain = (request, response) -> {
            seenPat[0] = RequestPatHolder.get();
            seenSlug[0] = ((HttpServletRequest) request).getAttribute(McpAuthFilter.SLUG_ATTRIBUTE);
        };

        filter.doFilter(req, res, chain);

        assertThat(seenPat[0]).isEqualTo("klt_xyz_during_chain");
        assertThat(seenSlug[0]).isEqualTo(SLUG);
        assertThat(RequestPatHolder.get()).isNull();
    }

    @Test
    void clearsPatHolderEvenIfChainThrows() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", URL_USER);
        req.addHeader("Authorization", "Bearer klt_failing");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> {
            throw new RuntimeException("simulated downstream failure");
        };

        try {
            filter.doFilter(req, res, chain);
        } catch (Exception ignored) {
            // expected
        }
        assertThat(RequestPatHolder.get()).isNull();
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
    void rewritesRequestUriToCanonicalSdkPath() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", URL_USER + "/messages");
        req.addHeader("Authorization", "Bearer klt_abc");
        MockHttpServletResponse res = new MockHttpServletResponse();

        String[] seenUri = new String[1];
        FilterChain chain = (request, response) -> {
            seenUri[0] = ((HttpServletRequest) request).getRequestURI();
        };

        filter.doFilter(req, res, chain);

        assertThat(seenUri[0]).isEqualTo("/mcp/user/messages");
    }

    @Test
    void stripsClientSuppliedTenantHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", URL_USER);
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
