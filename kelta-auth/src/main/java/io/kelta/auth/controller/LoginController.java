package io.kelta.auth.controller;

import io.kelta.auth.federation.DynamicClientRegistrationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class LoginController {

    private final ClientRegistrationRepository clientRegistrationRepository;

    public LoginController(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        // Convert error query params into session flash attributes and redirect
        // to clean URL. This ensures error messages display exactly once.
        String queryString = request.getQueryString();
        if (queryString != null
                && (queryString.contains("pending_activation") || queryString.contains("federation"))) {
            if (queryString.contains("pending_activation")) {
                request.getSession().setAttribute("login_error_pending_activation", true);
            }
            if (queryString.contains("federation")) {
                request.getSession().setAttribute("login_error_federation", true);
            }
            return "redirect:/login";
        }

        // Consume flash-like session attributes (show once, then remove)
        var session = request.getSession(false);
        if (session != null) {
            if (Boolean.TRUE.equals(session.getAttribute("login_error_pending_activation"))) {
                model.addAttribute("pendingActivation", true);
                session.removeAttribute("login_error_pending_activation");
            }
            if (Boolean.TRUE.equals(session.getAttribute("login_error_federation"))) {
                model.addAttribute("federationError", true);
                session.removeAttribute("login_error_federation");
            }
        }

        // Resolve tenant from the request to load SSO providers
        String tenantId = resolveTenantId(request);

        if (tenantId != null && clientRegistrationRepository instanceof DynamicClientRegistrationRepository dynamicRepo) {
            List<ClientRegistration> registrations = dynamicRepo.findByTenantId(tenantId);
            List<SsoProviderInfo> providers = registrations.stream()
                    .map(reg -> new SsoProviderInfo(reg.getRegistrationId(), reg.getClientName()))
                    .toList();
            model.addAttribute("ssoProviders", providers);
        }

        return "login";
    }

    private String resolveTenantId(HttpServletRequest request) {
        // Try tenant from session (set during OAuth2 authorize redirect)
        Object tenantId = request.getSession().getAttribute("tenantId");
        if (tenantId instanceof String str && !str.isBlank()) {
            return str;
        }

        // Try tenant from query parameter
        String param = request.getParameter("tenant");
        if (param != null && !param.isBlank()) {
            request.getSession().setAttribute("tenantId", param);
            return param;
        }

        return null;
    }

    public record SsoProviderInfo(String registrationId, String name) {}
}
