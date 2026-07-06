package io.kelta.auth.federation;

import io.kelta.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import java.io.IOException;

/**
 * Completes the SP-initiated SAML Single Logout round-trip. After the IdP posts its
 * {@code LogoutResponse} back to {@code /logout/saml2/slo/{registrationId}} and
 * Spring Security's {@code Saml2LogoutResponseFilter} validates it, this handler runs
 * as the logout success handler for the SAML security chain.
 *
 * <p>It redirects the browser to the {@code post_logout_redirect_uri} the initiator
 * stashed in the session ({@link SamlLogoutSupport#POST_LOGOUT_URI_ATTRIBUTE}) — in
 * practice the platform's own OIDC {@code end_session_endpoint}, which then terminates
 * the Authorization Server session and returns to the app. The target is re-validated
 * (same-origin) before use; anything missing or foreign falls back to
 * {@link SamlLogoutSupport#DEFAULT_LOGOUT_SUCCESS_URL}.
 */
public class SamlLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(SamlLogoutSuccessHandler.class);

    private final AuthProperties authProperties;

    public SamlLogoutSuccessHandler(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        String target = SamlLogoutSupport.DEFAULT_LOGOUT_SUCCESS_URL;

        HttpSession session = request.getSession(false);
        if (session != null) {
            Object stashed = session.getAttribute(SamlLogoutSupport.POST_LOGOUT_URI_ATTRIBUTE);
            session.removeAttribute(SamlLogoutSupport.POST_LOGOUT_URI_ATTRIBUTE);
            if (stashed instanceof String candidate) {
                String allowedOrigin = SamlLogoutSupport.allowedOrigin(authProperties.getIssuerUri(), request);
                String sanitized = SamlLogoutSupport.sanitizeRedirect(candidate, allowedOrigin);
                if (sanitized != null) {
                    target = sanitized;
                } else {
                    log.warn("Rejected non-same-origin SAML post-logout redirect target: {}", candidate);
                }
            }
        }

        log.debug("SAML Single Logout complete — redirecting to {}", target);
        response.sendRedirect(target);
    }
}
