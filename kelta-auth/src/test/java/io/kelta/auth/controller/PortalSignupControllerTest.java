package io.kelta.auth.controller;

import io.kelta.auth.service.AuthDomainResolver;
import io.kelta.auth.service.PortalLoginService;
import io.kelta.auth.service.WorkerClient;
import io.kelta.auth.service.botchallenge.BotChallengeVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Signup is an unauthenticated endpoint that creates accounts, so the properties
 * under test are mostly about what it <em>refuses to reveal</em>.
 */
@DisplayName("PortalSignupController Tests")
class PortalSignupControllerTest {

    private static final String TENANT = "tenant-uuid-1";
    private static final String ALLOWED = "https://app.example.com/welcome";

    private PortalLoginService portalLoginService;
    private AuthDomainResolver domainResolver;
    private WorkerClient workerClient;
    private BotChallengeVerifier botChallengeVerifier;
    private PortalSignupController controller;

    @BeforeEach
    void setUp() {
        portalLoginService = mock(PortalLoginService.class);
        domainResolver = mock(AuthDomainResolver.class);
        workerClient = mock(WorkerClient.class);
        botChallengeVerifier = mock(BotChallengeVerifier.class);
        controller = new PortalSignupController(
                portalLoginService, domainResolver, workerClient, botChallengeVerifier);

        when(domainResolver.resolveTenantSlug(anyString())).thenReturn(Optional.empty());
        when(portalLoginService.resolveTenantUuid("acme")).thenReturn(Optional.of(TENANT));
        when(portalLoginService.portalRedirectUris(TENANT)).thenReturn(List.of(ALLOWED));
        when(portalLoginService.selfSignupEnabled(TENANT)).thenReturn(true);
        when(workerClient.portalSignup(anyString(), anyString(), any(), any()))
                .thenReturn("CREATED");
        // Default to the challenge being switched off, so every test that is not
        // about the challenge exercises the same path it did before it existed.
        when(botChallengeVerifier.verify(any())).thenReturn(BotChallengeVerifier.Result.DISABLED);
    }

    private MockHttpServletRequest httpRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("auth.example.com");
        request.setRequestURI("/portal/api/signup");
        return request;
    }

    private ResponseEntity<Map<String, String>> signup(String email, String tenant,
                                                       String redirectUri) {
        return controller.signup(
                new PortalSignupController.SignupRequest(
                        email, tenant, redirectUri, null, null, null, null),
                httpRequest());
    }

    private ResponseEntity<Map<String, String>> signupWith(String challenge, String website) {
        return controller.signup(
                new PortalSignupController.SignupRequest(
                        "a@example.com", "acme", null, null, null, challenge, website),
                httpRequest());
    }

    @Nested
    @DisplayName("Enumeration safety")
    class EnumerationSafety {

        @Test
        @DisplayName("a successful signup answers 202")
        void successIs202() {
            assertThat(signup("new@example.com", "acme", null).getStatusCode().value())
                    .isEqualTo(202);
            verify(workerClient).portalSignup(TENANT, "new@example.com", null, null);
        }

        @Test
        @DisplayName("an unknown tenant answers the same 202 and does nothing")
        void unknownTenantIs202() {
            when(portalLoginService.resolveTenantUuid("nope")).thenReturn(Optional.empty());

            assertThat(signup("a@example.com", "nope", null).getStatusCode().value())
                    .isEqualTo(202);
            verify(workerClient, never()).portalSignup(anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("an existing account answers the same 202 — no 'already exists'")
        void existingAccountIs202() {
            // The worker distinguishes outcomes for its audit log; the client
            // must not be able to tell.
            when(workerClient.portalSignup(anyString(), anyString(), any(), any()))
                    .thenReturn("REINVITED");

            assertThat(signup("known@example.com", "acme", null).getStatusCode().value())
                    .isEqualTo(202);
        }

        @Test
        @DisplayName("a staff-owned address answers the same 202")
        void staffAddressIs202() {
            when(workerClient.portalSignup(anyString(), anyString(), any(), any()))
                    .thenReturn("STAFF_ACCOUNT_EXISTS");

            assertThat(signup("admin@example.com", "acme", null).getStatusCode().value())
                    .isEqualTo(202);
        }

        @Test
        @DisplayName("a worker outage still answers 202 rather than leaking a 5xx")
        void workerOutageIs202() {
            // A 500 here would tell an attacker their probe reached the account
            // path at all; it also differs from the unknown-tenant response.
            when(workerClient.portalSignup(anyString(), anyString(), any(), any()))
                    .thenReturn(null);

            assertThat(signup("a@example.com", "acme", null).getStatusCode().value())
                    .isEqualTo(202);
        }

        @Test
        @DisplayName("a seat-limit refusal answers the same 202")
        void seatLimitIs202() {
            when(workerClient.portalSignup(anyString(), anyString(), any(), any()))
                    .thenReturn("SEAT_LIMIT_REACHED");

            assertThat(signup("a@example.com", "acme", null).getStatusCode().value())
                    .isEqualTo(202);
        }
    }

    @Nested
    @DisplayName("Honeypot")
    class HoneypotField {

        @Test
        @DisplayName("a filled honeypot answers 202 and creates nothing")
        void filledHoneypotCreatesNothing() {
            // 202 rather than a 4xx on purpose: an error response would tell the
            // script exactly which field to stop filling in, turning the trap
            // into a one-request tutorial.
            ResponseEntity<Map<String, String>> response = signupWith(null, "http://spam.test");

            assertThat(response.getStatusCode().value()).isEqualTo(202);
            verifyNoInteractions(workerClient, portalLoginService, domainResolver);
        }

        @Test
        @DisplayName("the honeypot answer is indistinguishable from an ordinary success")
        void honeypotMatchesSuccessResponse() {
            ResponseEntity<Map<String, String>> genuine = signup("real@example.com", "acme", null);
            ResponseEntity<Map<String, String>> trapped = signupWith(null, "http://spam.test");

            assertThat(trapped.getStatusCode()).isEqualTo(genuine.getStatusCode());
            assertThat(trapped.getBody()).isEqualTo(genuine.getBody());
        }

        @Test
        @DisplayName("a blank honeypot is treated as untouched and proceeds")
        void blankHoneypotProceeds() {
            assertThat(signupWith(null, "   ").getStatusCode().value()).isEqualTo(202);
            verify(workerClient).portalSignup(TENANT, "a@example.com", null, null);
        }

        @Test
        @DisplayName("the honeypot short-circuits before the bot challenge is even consulted")
        void honeypotShortCircuitsBeforeChallenge() {
            signupWith("solved", "http://spam.test");

            verifyNoInteractions(botChallengeVerifier);
        }
    }

    @Nested
    @DisplayName("Bot challenge")
    class BotChallenge {

        @Test
        @DisplayName("an INVALID solution is a 400 bot_challenge_failed and creates nothing")
        void invalidChallengeIs400() {
            when(botChallengeVerifier.verify(any()))
                    .thenReturn(BotChallengeVerifier.Result.INVALID);

            ResponseEntity<Map<String, String>> response = signupWith("forged", null);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).containsEntry("error", "bot_challenge_failed");
            verify(workerClient, never()).portalSignup(anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("a MISSING solution is a 400 bot_challenge_failed and creates nothing")
        void missingChallengeIs400() {
            when(botChallengeVerifier.verify(any()))
                    .thenReturn(BotChallengeVerifier.Result.MISSING);

            ResponseEntity<Map<String, String>> response = signupWith(null, null);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).containsEntry("error", "bot_challenge_failed");
            verify(workerClient, never()).portalSignup(anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("a VALID solution proceeds to signup and is the payload that was verified")
        void validChallengeProceeds() {
            when(botChallengeVerifier.verify(any())).thenReturn(BotChallengeVerifier.Result.VALID);

            assertThat(signupWith("solved", null).getStatusCode().value()).isEqualTo(202);
            verify(botChallengeVerifier).verify("solved");
            verify(workerClient).portalSignup(TENANT, "a@example.com", null, null);
        }

        @Test
        @DisplayName("a DISABLED verifier proceeds, so deployments with the challenge off are unaffected")
        void disabledChallengeProceeds() {
            when(botChallengeVerifier.verify(any()))
                    .thenReturn(BotChallengeVerifier.Result.DISABLED);

            assertThat(signupWith(null, null).getStatusCode().value()).isEqualTo(202);
            verify(workerClient).portalSignup(TENANT, "a@example.com", null, null);
        }

        @Test
        @DisplayName("a rejected challenge never reaches tenant resolution or the account path")
        void rejectionHappensBeforeAnyLookup() {
            // The enumeration-safety property: a 400 here must be decided from the
            // submitted solution alone, so it cannot differ by whether the email or
            // tenant exists.
            when(botChallengeVerifier.verify(any()))
                    .thenReturn(BotChallengeVerifier.Result.INVALID);

            signupWith("forged", null);

            verifyNoInteractions(portalLoginService, domainResolver, workerClient);
        }

        @Test
        @DisplayName("a rejected challenge answers identically for a known and an unknown tenant")
        void rejectionIsIdenticalAcrossTenants() {
            when(botChallengeVerifier.verify(any()))
                    .thenReturn(BotChallengeVerifier.Result.INVALID);

            ResponseEntity<Map<String, String>> known = controller.signup(
                    new PortalSignupController.SignupRequest(
                            "a@example.com", "acme", null, null, null, "forged", null),
                    httpRequest());
            ResponseEntity<Map<String, String>> unknown = controller.signup(
                    new PortalSignupController.SignupRequest(
                            "a@example.com", "ghost", null, null, null, "forged", null),
                    httpRequest());

            assertThat(unknown.getStatusCode()).isEqualTo(known.getStatusCode());
            assertThat(unknown.getBody()).isEqualTo(known.getBody());
        }

        @Test
        @DisplayName("a blank email is rejected before the challenge is consulted")
        void blankEmailShortCircuitsBeforeChallenge() {
            signup(null, "acme", null);

            verifyNoInteractions(botChallengeVerifier);
        }
    }

    @Nested
    @DisplayName("Self-signup gate")
    class SelfSignupGate {

        @Test
        @DisplayName("a tenant with signup disabled creates nothing, and still answers 202")
        void disabledTenantIsSilentNoOp() {
            when(portalLoginService.selfSignupEnabled(TENANT)).thenReturn(false);

            assertThat(signup("a@example.com", "acme", null).getStatusCode().value())
                    .isEqualTo(202);
            verify(workerClient, never()).portalSignup(anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("the gate is checked per request, so disabling takes effect immediately")
        void gateCheckedPerRequest() {
            signup("a@example.com", "acme", null);
            verify(portalLoginService).selfSignupEnabled(TENANT);
        }
    }

    @Nested
    @DisplayName("Redirect allowlist")
    class RedirectAllowlist {

        @Test
        @DisplayName("an allowlisted redirectUri is accepted")
        void allowlistedAccepted() {
            assertThat(signup("a@example.com", "acme", ALLOWED).getStatusCode().value())
                    .isEqualTo(202);
        }

        @Test
        @DisplayName("a non-allowlisted redirectUri is rejected before any account lookup")
        void foreignRedirectRejected() {
            ResponseEntity<Map<String, String>> response =
                    signup("a@example.com", "acme", "https://evil.test/steal");

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).containsEntry("error", "redirect_uri_not_allowed");
            // Rejected before the account path, so it reveals nothing about the email.
            verify(workerClient, never()).portalSignup(anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("an unknown tenant fails a redirect the same way — empty allowlist")
        void unknownTenantRedirectFailsIdentically() {
            when(portalLoginService.resolveTenantUuid("nope")).thenReturn(Optional.empty());

            ResponseEntity<Map<String, String>> response =
                    signup("a@example.com", "nope", "https://app.example.com/welcome");

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).containsEntry("error", "redirect_uri_not_allowed");
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("a missing email is a plain 400 — it carries no account signal")
        void missingEmailIs400() {
            assertThat(signup(null, "acme", null).getStatusCode().value()).isEqualTo(400);
            assertThat(signup("  ", "acme", null).getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("falls back to the host's tenant when none is supplied")
        void fallsBackToHostTenant() {
            when(domainResolver.resolveTenantSlug("auth.example.com"))
                    .thenReturn(Optional.of("acme"));

            assertThat(signup("a@example.com", null, null).getStatusCode().value()).isEqualTo(202);
            verify(workerClient).portalSignup(TENANT, "a@example.com", null, null);
        }
    }
}
