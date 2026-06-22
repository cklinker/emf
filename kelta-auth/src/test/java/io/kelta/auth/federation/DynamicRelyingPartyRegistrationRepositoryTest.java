package io.kelta.auth.federation;

import io.kelta.auth.service.WorkerClient;
import io.kelta.auth.service.WorkerClient.SamlProviderInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("DynamicRelyingPartyRegistrationRepository")
@ExtendWith(MockitoExtension.class)
class DynamicRelyingPartyRegistrationRepositoryTest {

    @Mock private WorkerClient workerClient;

    private static String resource(String path) {
        try (InputStream in = DynamicRelyingPartyRegistrationRepositoryTest.class
                .getResourceAsStream(path)) {
            assertThat(in).as("test fixture %s present", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SamlProviderInfo provider(String idpCertPem) {
        return new SamlProviderInfo(
                "saml-1", "Acme IdP", "acme",
                "https://idp.acme.example/entity",
                "https://idp.acme.example/sso",
                idpCertPem,
                "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
                "email", null, true);
    }

    private DynamicRelyingPartyRegistrationRepository repoWithSpSigning() {
        SamlSpCredentials creds = SamlSpCredentials.fromPem(resource("/saml/sp.crt"), resource("/saml/sp.key"));
        return new DynamicRelyingPartyRegistrationRepository(workerClient, creds);
    }

    @Test
    @DisplayName("builds a registration from per-tenant provider config")
    void buildsRegistration() {
        when(workerClient.findActiveSamlProviders("tenant-1"))
                .thenReturn(List.of(provider(resource("/saml/idp.crt"))));

        RelyingPartyRegistration reg = repoWithSpSigning().findByRegistrationId("tenant-1:saml-1");

        assertThat(reg).isNotNull();
        assertThat(reg.getRegistrationId()).isEqualTo("tenant-1:saml-1");
        assertThat(reg.getAssertingPartyMetadata().getEntityId())
                .isEqualTo("https://idp.acme.example/entity");
        assertThat(reg.getAssertingPartyMetadata().getSingleSignOnServiceLocation())
                .isEqualTo("https://idp.acme.example/sso");
        assertThat(reg.getAssertingPartyMetadata().getVerificationX509Credentials()).isNotEmpty();
        assertThat(reg.getNameIdFormat())
                .isEqualTo("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");
    }

    @Test
    @DisplayName("attaches SP signing credential and requests signing when SP keypair configured")
    void signsWhenSpKeypairPresent() {
        when(workerClient.findActiveSamlProviders("tenant-1"))
                .thenReturn(List.of(provider(resource("/saml/idp.crt"))));

        RelyingPartyRegistration reg = repoWithSpSigning().findByRegistrationId("tenant-1:saml-1");

        assertThat(reg.getSigningX509Credentials()).as("SP signing credential attached").isNotEmpty();
        assertThat(reg.getAssertingPartyMetadata().getWantAuthnRequestsSigned()).isTrue();
    }

    @Test
    @DisplayName("omits SP signing and request signing when no SP keypair configured")
    void noSigningWhenSpKeypairAbsent() {
        when(workerClient.findActiveSamlProviders("tenant-1"))
                .thenReturn(List.of(provider(resource("/saml/idp.crt"))));
        var repo = new DynamicRelyingPartyRegistrationRepository(workerClient, SamlSpCredentials.none());

        RelyingPartyRegistration reg = repo.findByRegistrationId("tenant-1:saml-1");

        assertThat(reg.getSigningX509Credentials()).isEmpty();
        assertThat(reg.getAssertingPartyMetadata().getWantAuthnRequestsSigned()).isFalse();
    }

    @Test
    @DisplayName("returns null for an unknown provider id")
    void returnsNullWhenProviderMissing() {
        when(workerClient.findActiveSamlProviders("tenant-1"))
                .thenReturn(List.of(provider(resource("/saml/idp.crt"))));

        assertThat(repoWithSpSigning().findByRegistrationId("tenant-1:nope")).isNull();
    }

    @Test
    @DisplayName("returns null for a malformed registration id")
    void returnsNullForBadRegistrationId() {
        assertThat(repoWithSpSigning().findByRegistrationId("no-colon")).isNull();
        verifyNoInteractions(workerClient);
    }

    @Test
    @DisplayName("returns null (skips provider) when the IdP certificate is unparseable")
    void returnsNullForBadCertificate() {
        when(workerClient.findActiveSamlProviders("tenant-1"))
                .thenReturn(List.of(provider("-----BEGIN CERTIFICATE-----\nnot-a-cert\n-----END CERTIFICATE-----")));

        assertThat(repoWithSpSigning().findByRegistrationId("tenant-1:saml-1")).isNull();
    }

    @Test
    @DisplayName("caches the registration so repeat lookups don't re-hit the worker")
    void cachesRegistration() {
        when(workerClient.findActiveSamlProviders("tenant-1"))
                .thenReturn(List.of(provider(resource("/saml/idp.crt"))));
        var repo = repoWithSpSigning();

        repo.findByRegistrationId("tenant-1:saml-1");
        repo.findByRegistrationId("tenant-1:saml-1");

        verify(workerClient, times(1)).findActiveSamlProviders("tenant-1");
    }

    @Test
    @DisplayName("findButtonsByTenantId returns registrationId + display name per provider")
    void buildsLoginButtons() {
        when(workerClient.findActiveSamlProviders("tenant-1"))
                .thenReturn(List.of(provider(resource("/saml/idp.crt"))));

        List<DynamicRelyingPartyRegistrationRepository.SamlButton> buttons =
                repoWithSpSigning().findButtonsByTenantId("tenant-1");

        assertThat(buttons).hasSize(1);
        assertThat(buttons.get(0).registrationId()).isEqualTo("tenant-1:saml-1");
        assertThat(buttons.get(0).name()).isEqualTo("Acme IdP");
    }
}
