package io.kelta.auth.federation;

import tools.jackson.databind.ObjectMapper;
import io.kelta.auth.model.KeltaUserDetails;
import io.kelta.auth.service.WorkerClient;
import io.kelta.auth.service.WorkerClient.JitProvisionResult;
import io.kelta.auth.service.WorkerClient.ProfileInfo;
import io.kelta.auth.service.WorkerClient.SamlProviderInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("FederatedUserMapper — SAML")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FederatedUserMapperSamlTest {

    @Mock private WorkerClient workerClient;
    @Mock private Saml2AuthenticatedPrincipal principal;

    private FederatedUserMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FederatedUserMapper(workerClient, new ObjectMapper());
    }

    private SamlProviderInfo provider(String emailAttribute, String profileAttribute) {
        return new SamlProviderInfo("saml-1", "Acme IdP", "acme",
                "https://idp.acme/entity", "https://idp.acme/sso", "cert",
                "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
                emailAttribute, profileAttribute, true);
    }

    @Test
    @DisplayName("maps an active user using the configured email attribute and Minimum Access fallback")
    void mapsActiveUserWithFallbackProfile() {
        doReturn("user@acme.com").when(principal).getFirstAttribute("email");
        doReturn("Jane").when(principal).getFirstAttribute("firstName");
        doReturn("Roe").when(principal).getFirstAttribute("lastName");

        when(workerClient.findProfileByName("Minimum Access", "tenant-1"))
                .thenReturn(Optional.of(new ProfileInfo("p-min", "Minimum Access")));
        when(workerClient.jitProvisionUser("user@acme.com", "tenant-1", "Jane", "Roe", "p-min"))
                .thenReturn(Optional.of(new JitProvisionResult("u1", "p-min", "Minimum Access", "ACTIVE", false)));

        Optional<KeltaUserDetails> result = mapper.mapSamlUser(principal, "tenant-1", provider("email", null));

        assertThat(result).isPresent();
        KeltaUserDetails user = result.get();
        assertThat(user.getId()).isEqualTo("u1");
        assertThat(user.getEmail()).isEqualTo("user@acme.com");
        assertThat(user.getTenantId()).isEqualTo("tenant-1");
        assertThat(user.getProfileName()).isEqualTo("Minimum Access");
        assertThat(user.getDisplayName()).isEqualTo("Jane Roe");
    }

    @Test
    @DisplayName("resolves the profile from the configured profile attribute when present")
    void resolvesProfileFromAttribute() {
        doReturn("user@acme.com").when(principal).getFirstAttribute("email");
        doReturn("Admins").when(principal).getFirstAttribute("role");

        when(workerClient.findProfileByName("Admins", "tenant-1"))
                .thenReturn(Optional.of(new ProfileInfo("p-adm", "Admins")));
        when(workerClient.jitProvisionUser(eq("user@acme.com"), eq("tenant-1"), any(), any(), eq("p-adm")))
                .thenReturn(Optional.of(new JitProvisionResult("u2", "p-adm", "Admins", "ACTIVE", true)));

        Optional<KeltaUserDetails> result = mapper.mapSamlUser(principal, "tenant-1", provider("email", "role"));

        assertThat(result).isPresent();
        assertThat(result.get().getProfileName()).isEqualTo("Admins");
        // Minimum Access fallback is NOT consulted when the attribute resolves a profile.
        verify(workerClient, never()).findProfileByName("Minimum Access", "tenant-1");
        // First-time creation sends the invite email.
        verify(workerClient).sendInviteEmail(eq("tenant-1"), eq("user@acme.com"), anyString());
    }

    @Test
    @DisplayName("falls back to the NameID when it looks like an email and no attribute matches")
    void fallsBackToNameIdEmail() {
        when(principal.getName()).thenReturn("nameid-user@acme.com");
        when(workerClient.findProfileByName("Minimum Access", "tenant-1"))
                .thenReturn(Optional.of(new ProfileInfo("p-min", "Minimum Access")));
        when(workerClient.jitProvisionUser(eq("nameid-user@acme.com"), eq("tenant-1"), any(), any(), eq("p-min")))
                .thenReturn(Optional.of(new JitProvisionResult("u3", "p-min", "Minimum Access", "ACTIVE", false)));

        Optional<KeltaUserDetails> result = mapper.mapSamlUser(principal, "tenant-1", provider("email", null));

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("nameid-user@acme.com");
    }

    @Test
    @DisplayName("returns empty when no email can be resolved")
    void emptyWhenNoEmail() {
        when(principal.getName()).thenReturn("opaque-name-id");

        Optional<KeltaUserDetails> result = mapper.mapSamlUser(principal, "tenant-1", provider("email", null));

        assertThat(result).isEmpty();
        verify(workerClient, never()).jitProvisionUser(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("returns empty when the user is PENDING_ACTIVATION")
    void emptyWhenPendingActivation() {
        doReturn("user@acme.com").when(principal).getFirstAttribute("email");
        when(workerClient.findProfileByName("Minimum Access", "tenant-1")).thenReturn(Optional.empty());
        when(workerClient.jitProvisionUser(eq("user@acme.com"), eq("tenant-1"), any(), any(), isNull()))
                .thenReturn(Optional.of(new JitProvisionResult("u4", null, null, "PENDING_ACTIVATION", true)));

        Optional<KeltaUserDetails> result = mapper.mapSamlUser(principal, "tenant-1", provider("email", null));

        assertThat(result).isEmpty();
    }
}
