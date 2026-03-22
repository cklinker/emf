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
