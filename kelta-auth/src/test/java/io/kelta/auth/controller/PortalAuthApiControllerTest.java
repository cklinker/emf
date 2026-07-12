package io.kelta.auth.controller;

import io.kelta.auth.config.AuthProperties;
import io.kelta.auth.model.KeltaUserDetails;
import io.kelta.auth.service.AuthDomainResolver;
import io.kelta.auth.service.PortalLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PortalAuthApiController")
class PortalAuthApiControllerTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String ALLOWED = "https://portal.example.com/auth/callback";

    private PortalLoginService portalLoginService;
    private AuthDomainResolver domainResolver;
    private JwtEncoder jwtEncoder;
    private PortalAuthApiController controller;

    @BeforeEach
    void setUp() {
        portalLoginService = mock(PortalLoginService.class);
        domainResolver = mock(AuthDomainResolver.class);
        jwtEncoder = mock(JwtEncoder.class);
        AuthProperties authProperties = new AuthProperties();
        authProperties.setIssuerUri("https://auth.example.com");
        controller = new PortalAuthApiController(
                portalLoginService, domainResolver, authProperties, jwtEncoder);
        when(domainResolver.resolveTenantSlug(anyString())).thenReturn(Optional.empty());
    }

    private MockHttpServletRequest httpRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("auth.example.com");
        request.setRequestURI("/portal/api/login/request");
        return request;
    }

    @Test
    @DisplayName("request without an email is a 400")
    void requestNeedsEmail() {
        ResponseEntity<Map<String, String>> response = controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest(" ", "acme", null), httpRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(portalLoginService);
    }

    @Test
    @DisplayName("request with an allowlisted redirectUri issues the link against it")
    void requestWithAllowlistedRedirect() {
        when(portalLoginService.resolveTenantUuid("acme")).thenReturn(Optional.of(TENANT));
        when(portalLoginService.portalRedirectUris(TENANT)).thenReturn(List.of(ALLOWED));

        ResponseEntity<Map<String, String>> response = controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest("pat@example.com", "acme", ALLOWED),
                httpRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(portalLoginService).requestLink(TENANT, "pat@example.com", ALLOWED);
    }

    @Test
    @DisplayName("request with a non-allowlisted redirectUri is a 400 and issues nothing")
    void requestRejectsUnknownRedirect() {
        when(portalLoginService.resolveTenantUuid("acme")).thenReturn(Optional.of(TENANT));
        when(portalLoginService.portalRedirectUris(TENANT)).thenReturn(List.of(ALLOWED));

        ResponseEntity<Map<String, String>> response = controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest(
                        "pat@example.com", "acme", "https://evil.example.com/steal"),
                httpRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "redirect_uri_not_allowed");
        verify(portalLoginService, never()).requestLink(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("request for an unknown tenant with a redirectUri fails the same 400 — no tenant oracle")
    void requestUnknownTenantWithRedirect() {
        when(portalLoginService.resolveTenantUuid("ghost")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, String>> response = controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest("pat@example.com", "ghost", ALLOWED),
                httpRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "redirect_uri_not_allowed");
        verify(portalLoginService, never()).requestLink(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("request without a redirectUri stays 202 and links to the on-host verify page")
    void requestDefaultsToVerifyPage() {
        when(portalLoginService.resolveTenantUuid("acme")).thenReturn(Optional.of(TENANT));

        ResponseEntity<Map<String, String>> response = controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest("pat@example.com", "acme", null),
                httpRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        ArgumentCaptor<String> base = ArgumentCaptor.forClass(String.class);
        verify(portalLoginService).requestLink(eq(TENANT), eq("pat@example.com"), base.capture());
        assertThat(base.getValue()).isEqualTo("http://auth.example.com/portal/login/verify");
    }

    @Test
    @DisplayName("request for an unknown tenant without redirectUri is still a generic 202")
    void requestUnknownTenantSilent() {
        when(portalLoginService.resolveTenantUuid("ghost")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, String>> response = controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest("pat@example.com", "ghost", null),
                httpRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(portalLoginService, never()).requestLink(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("verify rejects unknown tokens with a generic 401")
    void verifyRejects() {
        when(portalLoginService.verify(anyString())).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.verify(
                new PortalAuthApiController.VerifyRequest("bogus"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).containsEntry("error", "invalid_or_expired_token");
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    @DisplayName("verify mints a bearer token carrying the portal user's claims incl. user_type")
    void verifyMintsToken() {
        KeltaUserDetails portalUser = new KeltaUserDetails(
                "u1", "pat@example.com", TENANT, "prof-1", "Portal User",
                "Pat Doe", "", true, false, false, "PORTAL");
        when(portalLoginService.verify("raw")).thenReturn(Optional.of(
                new PortalLoginService.PortalVerification(portalUser, "acme")));
        when(jwtEncoder.encode(any())).thenReturn(
                Jwt.withTokenValue("signed-jwt").header("alg", "RS256").claim("sub", "u1").build());

        ResponseEntity<Map<String, Object>> response = controller.verify(
                new PortalAuthApiController.VerifyRequest("raw"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .containsEntry("accessToken", "signed-jwt")
                .containsEntry("tokenType", "Bearer")
                .containsEntry("tenantSlug", "acme")
                .containsEntry("userId", "u1");

        ArgumentCaptor<JwtEncoderParameters> params =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(params.capture());
        Map<String, Object> claims = params.getValue().getClaims().getClaims();
        assertThat(claims)
                .containsEntry("tenant_id", TENANT)
                .containsEntry("user_type", "PORTAL")
                .containsEntry("email", "pat@example.com")
                .containsEntry("profile_id", "prof-1")
                .containsEntry("auth_method", "magic_link");
        assertThat(claims.get("aud")).isEqualTo(List.of("kelta-platform"));
    }
}
