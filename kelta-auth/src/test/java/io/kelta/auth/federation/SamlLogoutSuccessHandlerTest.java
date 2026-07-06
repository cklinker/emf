package io.kelta.auth.federation;

import io.kelta.auth.config.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SamlLogoutSuccessHandler + SamlLogoutSupport")
class SamlLogoutSuccessHandlerTest {

    private static final String ISSUER = "https://auth.kelta.example";

    private AuthProperties props() {
        AuthProperties p = new AuthProperties();
        p.setIssuerUri(ISSUER);
        return p;
    }

    private MockHttpServletRequest request(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("auth.kelta.example");
        request.setServerPort(443);
        if (session != null) {
            request.setSession(session);
        }
        return request;
    }

    @Test
    @DisplayName("redirects to the stashed same-origin post-logout target and clears it")
    void redirectsToStashedTarget() throws IOException {
        String target = ISSUER + "/connect/logout?id_token_hint=abc";
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SamlLogoutSupport.POST_LOGOUT_URI_ATTRIBUTE, target);
        MockHttpServletRequest request = request(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SamlLogoutSuccessHandler(props()).onLogoutSuccess(request, response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo(target);
        assertThat(session.getAttribute(SamlLogoutSupport.POST_LOGOUT_URI_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("falls back to the default when a foreign target was stashed")
    void rejectsForeignStashedTarget() throws IOException {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SamlLogoutSupport.POST_LOGOUT_URI_ATTRIBUTE, "https://evil.example/x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SamlLogoutSuccessHandler(props()).onLogoutSuccess(request(session), response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo(SamlLogoutSupport.DEFAULT_LOGOUT_SUCCESS_URL);
    }

    @Test
    @DisplayName("falls back to the default when no target was stashed")
    void defaultWhenNoTarget() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SamlLogoutSuccessHandler(props()).onLogoutSuccess(request(new MockHttpSession()), response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo(SamlLogoutSupport.DEFAULT_LOGOUT_SUCCESS_URL);
    }

    @Test
    @DisplayName("sanitizeRedirect accepts same-origin http(s) URLs only")
    void sanitizeRedirect() {
        String origin = "https://auth.kelta.example";
        assertThat(SamlLogoutSupport.sanitizeRedirect(origin + "/connect/logout?a=b", origin))
                .isEqualTo(origin + "/connect/logout?a=b");
        assertThat(SamlLogoutSupport.sanitizeRedirect("https://other.example/x", origin)).isNull();
        assertThat(SamlLogoutSupport.sanitizeRedirect("javascript:alert(1)", origin)).isNull();
        assertThat(SamlLogoutSupport.sanitizeRedirect("/relative/path", origin)).isNull();
        assertThat(SamlLogoutSupport.sanitizeRedirect(null, origin)).isNull();
    }

    @Test
    @DisplayName("allowedOrigin prefers the issuer origin over the request origin")
    void allowedOriginUsesIssuer() {
        String origin = SamlLogoutSupport.allowedOrigin(ISSUER, request(null));
        assertThat(origin).isEqualTo("https://auth.kelta.example");
        // Falls back to the request origin when the issuer is blank.
        assertThat(SamlLogoutSupport.allowedOrigin(null, request(null)))
                .isEqualTo("https://auth.kelta.example");
    }
}
