package io.kelta.auth.controller;

import io.kelta.auth.config.AuthProperties;
import io.kelta.auth.model.KeltaUserDetails;
import io.kelta.auth.service.AuthDomainResolver;
import io.kelta.auth.service.PortalLoginService;
import io.kelta.auth.service.botchallenge.BotChallengeVerifier;
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
    private BotChallengeVerifier botChallengeVerifier;
    private PortalAuthApiController controller;

    @BeforeEach
    void setUp() {
        portalLoginService = mock(PortalLoginService.class);
        domainResolver = mock(AuthDomainResolver.class);
        jwtEncoder = mock(JwtEncoder.class);
        botChallengeVerifier = mock(BotChallengeVerifier.class);
        AuthProperties authProperties = new AuthProperties();
        authProperties.setIssuerUri("https://auth.example.com");
        controller = new PortalAuthApiController(
                portalLoginService, domainResolver, authProperties, jwtEncoder,
                botChallengeVerifier);
        when(domainResolver.resolveTenantSlug(anyString())).thenReturn(Optional.empty());
        // Default to the challenge being switched off, so every test that is not
        // about the challenge exercises the same path it did before it existed.
        when(botChallengeVerifier.verify(any())).thenReturn(BotChallengeVerifier.Result.DISABLED);
    }

    private MockHttpServletRequest httpRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("auth.example.com");
        request.setRequestURI("/portal/api/login/request");
        return request;
    }

    private ResponseEntity<Map<String, String>> requestLink(String challenge, String website) {
        return controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest(
                        "pat@example.com", "acme", null, challenge, website),
                httpRequest());
    }

    @Test
    @DisplayName("request without an email is a 400")
    void requestNeedsEmail() {
        ResponseEntity<Map<String, String>> response = controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest(" ", "acme", null, null, null),
                httpRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(portalLoginService);
    }

    @Test
    @DisplayName("request with an allowlisted redirectUri issues the link against it")
    void requestWithAllowlistedRedirect() {
        when(portalLoginService.resolveTenantUuid("acme")).thenReturn(Optional.of(TENANT));
        when(portalLoginService.portalRedirectUris(TENANT)).thenReturn(List.of(ALLOWED));

        ResponseEntity<Map<String, String>> response = controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest(
                        "pat@example.com", "acme", ALLOWED, null, null),
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
                        "pat@example.com", "acme", "https://evil.example.com/steal", null, null),
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
                new PortalAuthApiController.LoginLinkRequest(
                        "pat@example.com", "ghost", ALLOWED, null, null),
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
                new PortalAuthApiController.LoginLinkRequest(
                        "pat@example.com", "acme", null, null, null),
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
                new PortalAuthApiController.LoginLinkRequest(
                        "pat@example.com", "ghost", null, null, null),
                httpRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(portalLoginService, never()).requestLink(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a filled honeypot answers 202 and requests no link")
    void honeypotRequestsNoLink() {
        // 202 rather than a 4xx on purpose: an error response would tell the
        // script exactly which field to stop filling in, turning the trap into a
        // one-request tutorial.
        ResponseEntity<Map<String, String>> response = requestLink(null, "http://spam.test");

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verifyNoInteractions(portalLoginService, domainResolver);
    }

    @Test
    @DisplayName("the honeypot answer is indistinguishable from an ordinary success")
    void honeypotMatchesSuccessResponse() {
        when(portalLoginService.resolveTenantUuid("acme")).thenReturn(Optional.of(TENANT));

        ResponseEntity<Map<String, String>> genuine = requestLink(null, null);
        ResponseEntity<Map<String, String>> trapped = requestLink(null, "http://spam.test");

        assertThat(trapped.getStatusCode()).isEqualTo(genuine.getStatusCode());
        assertThat(trapped.getBody()).isEqualTo(genuine.getBody());
    }

    @Test
    @DisplayName("a blank honeypot is treated as untouched and proceeds")
    void blankHoneypotProceeds() {
        when(portalLoginService.resolveTenantUuid("acme")).thenReturn(Optional.of(TENANT));

        assertThat(requestLink(null, "   ").getStatusCode().value()).isEqualTo(202);
        verify(portalLoginService).requestLink(eq(TENANT), eq("pat@example.com"), anyString());
    }

    @Test
    @DisplayName("the honeypot short-circuits before the bot challenge is even consulted")
    void honeypotShortCircuitsBeforeChallenge() {
        requestLink("solved", "http://spam.test");

        verifyNoInteractions(botChallengeVerifier);
    }

    @Test
    @DisplayName("an INVALID bot challenge is a 400 bot_challenge_failed and requests no link")
    void invalidChallengeIs400() {
        when(botChallengeVerifier.verify(any())).thenReturn(BotChallengeVerifier.Result.INVALID);

        ResponseEntity<Map<String, String>> response = requestLink("forged", null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "bot_challenge_failed");
        verify(portalLoginService, never()).requestLink(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a MISSING bot challenge is a 400 bot_challenge_failed and requests no link")
    void missingChallengeIs400() {
        when(botChallengeVerifier.verify(any())).thenReturn(BotChallengeVerifier.Result.MISSING);

        ResponseEntity<Map<String, String>> response = requestLink(null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "bot_challenge_failed");
        verify(portalLoginService, never()).requestLink(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a VALID bot challenge proceeds and is the payload that was verified")
    void validChallengeProceeds() {
        when(botChallengeVerifier.verify(any())).thenReturn(BotChallengeVerifier.Result.VALID);
        when(portalLoginService.resolveTenantUuid("acme")).thenReturn(Optional.of(TENANT));

        assertThat(requestLink("solved", null).getStatusCode().value()).isEqualTo(202);
        verify(botChallengeVerifier).verify("solved");
        verify(portalLoginService).requestLink(eq(TENANT), eq("pat@example.com"), anyString());
    }

    @Test
    @DisplayName("a DISABLED verifier proceeds, so deployments with the challenge off are unaffected")
    void disabledChallengeProceeds() {
        when(botChallengeVerifier.verify(any())).thenReturn(BotChallengeVerifier.Result.DISABLED);
        when(portalLoginService.resolveTenantUuid("acme")).thenReturn(Optional.of(TENANT));

        assertThat(requestLink(null, null).getStatusCode().value()).isEqualTo(202);
        verify(portalLoginService).requestLink(eq(TENANT), eq("pat@example.com"), anyString());
    }

    @Test
    @DisplayName("a rejected challenge never reaches tenant resolution or the user lookup")
    void rejectionHappensBeforeAnyLookup() {
        // The enumeration-safety property: a 400 here must be decided from the
        // submitted solution alone, so it cannot differ by whether the email or
        // tenant exists.
        when(botChallengeVerifier.verify(any())).thenReturn(BotChallengeVerifier.Result.INVALID);

        requestLink("forged", null);

        verifyNoInteractions(portalLoginService, domainResolver);
    }

    @Test
    @DisplayName("a rejected challenge answers identically for a known and an unknown tenant")
    void rejectionIsIdenticalAcrossTenants() {
        when(botChallengeVerifier.verify(any())).thenReturn(BotChallengeVerifier.Result.INVALID);

        ResponseEntity<Map<String, String>> known = controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest(
                        "pat@example.com", "acme", null, "forged", null),
                httpRequest());
        ResponseEntity<Map<String, String>> unknown = controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest(
                        "pat@example.com", "ghost", null, "forged", null),
                httpRequest());

        assertThat(unknown.getStatusCode()).isEqualTo(known.getStatusCode());
        assertThat(unknown.getBody()).isEqualTo(known.getBody());
    }

    @Test
    @DisplayName("a blank email is rejected before the challenge is consulted")
    void blankEmailShortCircuitsBeforeChallenge() {
        controller.requestLink(
                new PortalAuthApiController.LoginLinkRequest(" ", "acme", null, "solved", null),
                httpRequest());

        verifyNoInteractions(botChallengeVerifier);
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
    @DisplayName("verify is not gated on the bot challenge — the token is the credential")
    void verifyIgnoresChallenge() {
        when(botChallengeVerifier.verify(any())).thenReturn(BotChallengeVerifier.Result.INVALID);
        when(portalLoginService.verify(anyString())).thenReturn(Optional.empty());

        controller.verify(new PortalAuthApiController.VerifyRequest("bogus"));

        verifyNoInteractions(botChallengeVerifier);
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
