package io.kelta.auth.controller;

import io.kelta.auth.federation.DynamicClientRegistrationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final ClientRegistrationRepository clientRegistrationRepository;

    public LoginController(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        // Set error model attributes from query params. The template uses model
        // attributes instead of Thymeleaf's ${param} to avoid false positives
        // with Spring Security's request parameter handling.
        if (request.getParameter("pending_activation") != null) {
            model.addAttribute("pendingActivation", true);
        }
        if (request.getParameter("federation") != null) {
            model.addAttribute("federationError", true);
        }

        String idpHint = request.getParameter("idp_hint");
        if (idpHint != null && !idpHint.isBlank()
                && clientRegistrationRepository instanceof DynamicClientRegistrationRepository dynamicRepo) {
            ClientRegistration hinted = dynamicRepo.findByRegistrationId(idpHint);
            if (hinted != null) {
                log.info("Auto-federating login via idp_hint registration={}", idpHint);
                return "redirect:/oauth2/authorization/"
                        + URLEncoder.encode(idpHint, StandardCharsets.UTF_8);
            }
            log.warn("Ignoring idp_hint={} — no matching client registration", idpHint);
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
