package io.kelta.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsentController Tests")
class ConsentControllerTest {

    private ConsentController controller;

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    @Mock
    private OAuth2AuthorizationConsentService consentService;

    @BeforeEach
    void setUp() {
        controller = new ConsentController(registeredClientRepository, consentService);
    }

    @Test
    @DisplayName("should populate model with scope information for new consent")
    void shouldPopulateModelForNewConsent() {
        String clientInternalId = UUID.randomUUID().toString();
        RegisteredClient client = RegisteredClient.withId(clientInternalId)
                .clientId("test-app")
                .clientName("Test Application")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://test.com/callback")
                .scope("openid")
                .scope("api")
                .build();

        when(registeredClientRepository.findByClientId("test-app")).thenReturn(client);
        when(consentService.findById(clientInternalId, "user@test.com")).thenReturn(null);

        Principal principal = () -> "user@test.com";
        Model model = new ConcurrentModel();

        String view = controller.consent(principal, model, "test-app", "openid api", "state123");

        assertThat(view).isEqualTo("consent");
        assertThat(model.getAttribute("clientId")).isEqualTo("test-app");
        assertThat(model.getAttribute("clientName")).isEqualTo("Test Application");
        assertThat(model.getAttribute("state")).isEqualTo("state123");
        assertThat(model.getAttribute("principalName")).isEqualTo("user@test.com");

        @SuppressWarnings("unchecked")
        Set<ConsentController.ScopeDisplay> scopes =
                (Set<ConsentController.ScopeDisplay>) model.getAttribute("scopes");
        assertThat(scopes).hasSize(2);
        assertThat(scopes).extracting(ConsentController.ScopeDisplay::scope)
                .containsExactlyInAnyOrder("openid", "api");
    }

    @Test
    @DisplayName("should separate previously approved scopes")
    void shouldSeparatePreviouslyApprovedScopes() {
        String clientInternalId = UUID.randomUUID().toString();
        RegisteredClient client = RegisteredClient.withId(clientInternalId)
                .clientId("test-app")
                .clientName("Test Application")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://test.com/callback")
                .scope("openid")
                .scope("api")
                .build();

        when(registeredClientRepository.findByClientId("test-app")).thenReturn(client);

        OAuth2AuthorizationConsent previousConsent = OAuth2AuthorizationConsent
                .withId(clientInternalId, "user@test.com")
                .scope("openid")
                .build();
        when(consentService.findById(clientInternalId, "user@test.com")).thenReturn(previousConsent);

        Principal principal = () -> "user@test.com";
        Model model = new ConcurrentModel();

        controller.consent(principal, model, "test-app", "openid api", "state123");

        @SuppressWarnings("unchecked")
        Set<ConsentController.ScopeDisplay> newScopes =
                (Set<ConsentController.ScopeDisplay>) model.getAttribute("scopes");
        assertThat(newScopes).hasSize(1);
        assertThat(newScopes).extracting(ConsentController.ScopeDisplay::scope)
                .containsExactly("api");

        @SuppressWarnings("unchecked")
        Set<ConsentController.ScopeDisplay> previouslyApproved =
                (Set<ConsentController.ScopeDisplay>) model.getAttribute("previouslyApprovedScopes");
        assertThat(previouslyApproved).hasSize(1);
        assertThat(previouslyApproved).extracting(ConsentController.ScopeDisplay::scope)
                .containsExactly("openid");
    }

    @Test
    @DisplayName("should redirect on unknown client")
    void shouldRedirectOnUnknownClient() {
        when(registeredClientRepository.findByClientId("unknown")).thenReturn(null);

        Principal principal = () -> "user@test.com";
        Model model = new ConcurrentModel();

        String view = controller.consent(principal, model, "unknown", "openid", "state123");

        assertThat(view).isEqualTo("redirect:/login?error");
    }
}
