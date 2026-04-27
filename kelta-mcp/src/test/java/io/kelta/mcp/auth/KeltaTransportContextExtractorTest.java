package io.kelta.mcp.auth;

import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class KeltaTransportContextExtractorTest {

    private final KeltaTransportContextExtractor extractor = new KeltaTransportContextExtractor();

    @Test
    void extractsBearerTokenFromAuthorizationHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/user");
        req.addHeader("Authorization", "Bearer klt_token_value");

        McpTransportContext context = extractor.extract(req);

        assertThat(context.get(KeltaTransportContextExtractor.PAT_KEY)).isEqualTo("klt_token_value");
    }

    @Test
    void returnsEmptyContextWhenAuthorizationMissing() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/user");

        McpTransportContext context = extractor.extract(req);

        assertThat(context).isSameAs(McpTransportContext.EMPTY);
    }

    @Test
    void returnsEmptyContextWhenSchemeIsNotBearer() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/user");
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        McpTransportContext context = extractor.extract(req);

        assertThat(context).isSameAs(McpTransportContext.EMPTY);
    }

    @Test
    void returnsEmptyContextWhenBearerTokenIsBlank() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/user");
        req.addHeader("Authorization", "Bearer ");

        McpTransportContext context = extractor.extract(req);

        assertThat(context).isSameAs(McpTransportContext.EMPTY);
    }

    @Test
    void includesTenantSlugFromRequestAttribute() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/user");
        req.addHeader("Authorization", "Bearer klt_token");
        req.setAttribute(McpAuthFilter.SLUG_ATTRIBUTE, "threadline-clothing");

        McpTransportContext context = extractor.extract(req);

        assertThat(context.get(KeltaTransportContextExtractor.PAT_KEY)).isEqualTo("klt_token");
        assertThat(context.get(KeltaTransportContextExtractor.TENANT_SLUG_KEY))
                .isEqualTo("threadline-clothing");
    }

    @Test
    void omitsSlugKeyWhenAttributeAbsent() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/user");
        req.addHeader("Authorization", "Bearer klt_token");

        McpTransportContext context = extractor.extract(req);

        assertThat(context.get(KeltaTransportContextExtractor.TENANT_SLUG_KEY)).isNull();
    }
}
