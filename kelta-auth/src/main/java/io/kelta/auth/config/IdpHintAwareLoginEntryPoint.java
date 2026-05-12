package io.kelta.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class IdpHintAwareLoginEntryPoint extends LoginUrlAuthenticationEntryPoint {

    public IdpHintAwareLoginEntryPoint(String loginFormUrl) {
        super(loginFormUrl);
    }

    @Override
    protected String determineUrlToUseForThisRequest(HttpServletRequest request,
                                                      HttpServletResponse response,
                                                      AuthenticationException exception) {
        String base = super.determineUrlToUseForThisRequest(request, response, exception);
        String hint = request.getParameter("idp_hint");
        if (hint == null || hint.isBlank()) {
            return base;
        }
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "idp_hint=" + URLEncoder.encode(hint, StandardCharsets.UTF_8);
    }
}
