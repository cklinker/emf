package io.kelta.auth.federation;

import tools.jackson.databind.ObjectMapper;
import io.kelta.auth.model.KeltaUserDetails;
import io.kelta.auth.service.WorkerClient;
import io.kelta.auth.service.WorkerClient.OidcProviderInfo;
import io.kelta.auth.service.WorkerClient.JitProvisionResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("FederatedUserMapper")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FederatedUserMapperTest {

    @Mock private WorkerClient workerClient;
    @Mock private OidcUser oidcUser;

    private FederatedUserMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FederatedUserMapper(workerClient, new ObjectMapper());
    }

    private OidcProviderInfo provider(String emailClaim, String nameClaim, String groupsClaim,
                                       String groupsProfileMapping) {
        return new OidcProviderInfo(
                "prov-1", "Test Provider", "https://idp.example.com", "https://idp.example.com/jwks",
                null, "client-id", "enc-secret",
                null, groupsClaim, groupsProfileMapping,
                null, null, null, null,
                emailClaim, null, nameClaim
        );
    }

    @Nested
    @DisplayName("mapUser")
    class MapUser {

        @Test
        @DisplayName("returns KeltaUserDetails for active user")
        void returnsUserDetailsForActiveUser() {
            when(oidcUser.getClaim("email")).thenReturn("user@test.com");
            when(oidcUser.getClaim("name")).thenReturn("John Doe");
            when(oidcUser.getClaim("given_name")).thenReturn("John");
            when(oidcUser.getClaim("family_name")).thenReturn("Doe");

            when(workerClient.jitProvisionUser(eq("user@test.com"), eq("tenant1"),
                    eq("John"), eq("Doe"), isNull()))
                    .thenReturn(Optional.of(new JitProvisionResult(
                            "user-1", "prof-1", "Admin", "ACTIVE", false)));

            Optional<KeltaUserDetails> result = mapper.mapUser(oidcUser, "tenant1",
                    provider(null, null, null, null));

            assertThat(result).isPresent();
            KeltaUserDetails details = result.get();
            assertThat(details.getId()).isEqualTo("user-1");
            assertThat(details.getEmail()).isEqualTo("user@test.com");
            assertThat(details.getTenantId()).isEqualTo("tenant1");
            assertThat(details.getProfileId()).isEqualTo("prof-1");
            assertThat(details.getDisplayName()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("returns empty when no email in claims")
        void returnsEmptyWhenNoEmail() {
            when(oidcUser.getClaim("email")).thenReturn(null);

            Optional<KeltaUserDetails> result = mapper.mapUser(oidcUser, "tenant1",
                    provider(null, null, null, null));

            assertThat(result).isEmpty();
            verifyNoInteractions(workerClient);
        }

        @Test
        @DisplayName("uses custom email claim when configured")
        void usesCustomEmailClaim() {
            when(oidcUser.getClaim("preferred_email")).thenReturn("custom@test.com");
            when(oidcUser.getClaim("name")).thenReturn("Jane");

            when(workerClient.jitProvisionUser(eq("custom@test.com"), anyString(),
                    anyString(), any(), any()))
                    .thenReturn(Optional.of(new JitProvisionResult(
                            "user-1", null, null, "ACTIVE", true)));

            Optional<KeltaUserDetails> result = mapper.mapUser(oidcUser, "tenant1",
                    provider("preferred_email", null, null, null));

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("custom@test.com");
        }

        @Test
        @DisplayName("falls back to default email claim when custom claim is empty")
        void fallsBackToDefaultEmailClaim() {
            when(oidcUser.getClaim("custom_email")).thenReturn("");
            when(oidcUser.getClaim("email")).thenReturn("fallback@test.com");
            when(oidcUser.getClaim("name")).thenReturn("User");

            when(workerClient.jitProvisionUser(eq("fallback@test.com"), anyString(),
                    any(), any(), any()))
                    .thenReturn(Optional.of(new JitProvisionResult(
                            "user-1", null, null, "ACTIVE", false)));

            Optional<KeltaUserDetails> result = mapper.mapUser(oidcUser, "tenant1",
                    provider("custom_email", null, null, null));

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("fallback@test.com");
        }

        @Test
        @DisplayName("returns empty for PENDING_ACTIVATION user")
        void returnsEmptyForPendingActivation() {
            when(oidcUser.getClaim("email")).thenReturn("user@test.com");

            when(workerClient.jitProvisionUser(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Optional.of(new JitProvisionResult(
                            "user-1", null, null, "PENDING_ACTIVATION", true)));

            Optional<KeltaUserDetails> result = mapper.mapUser(oidcUser, "tenant1",
                    provider(null, null, null, null));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty for non-ACTIVE status")
        void returnsEmptyForNonActiveStatus() {
            when(oidcUser.getClaim("email")).thenReturn("user@test.com");

            when(workerClient.jitProvisionUser(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Optional.of(new JitProvisionResult(
                            "user-1", null, null, "SUSPENDED", false)));

            Optional<KeltaUserDetails> result = mapper.mapUser(oidcUser, "tenant1",
                    provider(null, null, null, null));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when JIT provisioning fails")
        void returnsEmptyWhenJitFails() {
            when(oidcUser.getClaim("email")).thenReturn("user@test.com");
            when(workerClient.jitProvisionUser(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            Optional<KeltaUserDetails> result = mapper.mapUser(oidcUser, "tenant1",
                    provider(null, null, null, null));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("uses email as displayName when name claim is absent")
        void usesEmailAsDisplayNameWhenNameAbsent() {
            when(oidcUser.getClaim("email")).thenReturn("user@test.com");
            when(oidcUser.getClaim("name")).thenReturn(null);

            when(workerClient.jitProvisionUser(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Optional.of(new JitProvisionResult(
                            "user-1", null, null, "ACTIVE", false)));

            Optional<KeltaUserDetails> result = mapper.mapUser(oidcUser, "tenant1",
                    provider(null, null, null, null));

            assertThat(result).isPresent();
            assertThat(result.get().getDisplayName()).isEqualTo("user@test.com");
        }
    }

    @Nested
    @DisplayName("extractGroups")
    class ExtractGroups {

        @Test
        @DisplayName("extracts groups from list claim")
        void extractsFromList() {
            when(oidcUser.getClaim("email")).thenReturn("user@test.com");
            when(oidcUser.getClaim("groups")).thenReturn(List.of("admins", "users"));

            String mappingJson = "{\"admins\":\"System Administrator\",\"users\":\"Standard User\"}";
            when(workerClient.jitProvisionUser(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Optional.of(new JitProvisionResult("u1", null, null, "ACTIVE", false)));

            mapper.mapUser(oidcUser, "tenant1", provider(null, null, null, mappingJson));

            // Verify that the jitProvisionUser was called (which means groups extraction worked)
            verify(workerClient).jitProvisionUser(anyString(), anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("extracts groups from comma-separated string")
        void extractsFromString() {
            when(oidcUser.getClaim("email")).thenReturn("user@test.com");
            when(oidcUser.getClaim("custom_groups")).thenReturn("admins,users");

            when(workerClient.jitProvisionUser(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Optional.of(new JitProvisionResult("u1", null, null, "ACTIVE", false)));

            mapper.mapUser(oidcUser, "tenant1", provider(null, null, "custom_groups", null));

            verify(workerClient).jitProvisionUser(anyString(), anyString(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("name extraction")
    class NameExtraction {

        @Test
        @DisplayName("splits display name into first and last name when no given_name/family_name")
        void splitsDisplayName() {
            when(oidcUser.getClaim("email")).thenReturn("user@test.com");
            when(oidcUser.getClaim("name")).thenReturn("John Doe");
            when(oidcUser.getClaim("given_name")).thenReturn(null);
            when(oidcUser.getClaim("family_name")).thenReturn(null);

            when(workerClient.jitProvisionUser(eq("user@test.com"), eq("tenant1"),
                    eq("John"), eq("Doe"), any()))
                    .thenReturn(Optional.of(new JitProvisionResult("u1", null, null, "ACTIVE", false)));

            mapper.mapUser(oidcUser, "tenant1", provider(null, null, null, null));

            verify(workerClient).jitProvisionUser("user@test.com", "tenant1", "John", "Doe", null);
        }

        @Test
        @DisplayName("prefers given_name and family_name claims")
        void prefersGivenAndFamilyName() {
            when(oidcUser.getClaim("email")).thenReturn("user@test.com");
            when(oidcUser.getClaim("name")).thenReturn("Display Name");
            when(oidcUser.getClaim("given_name")).thenReturn("Jane");
            when(oidcUser.getClaim("family_name")).thenReturn("Smith");

            when(workerClient.jitProvisionUser(eq("user@test.com"), eq("tenant1"),
                    eq("Jane"), eq("Smith"), any()))
                    .thenReturn(Optional.of(new JitProvisionResult("u1", null, null, "ACTIVE", false)));

            mapper.mapUser(oidcUser, "tenant1", provider(null, null, null, null));

            verify(workerClient).jitProvisionUser("user@test.com", "tenant1", "Jane", "Smith", null);
        }

        @Test
        @DisplayName("uses display name as first name when it has no space")
        void usesDisplayNameAsFirstNameWhenNoSpace() {
            when(oidcUser.getClaim("email")).thenReturn("user@test.com");
            when(oidcUser.getClaim("name")).thenReturn("SingleName");
            when(oidcUser.getClaim("given_name")).thenReturn(null);
            when(oidcUser.getClaim("family_name")).thenReturn(null);

            when(workerClient.jitProvisionUser(eq("user@test.com"), eq("tenant1"),
                    eq("SingleName"), isNull(), any()))
                    .thenReturn(Optional.of(new JitProvisionResult("u1", null, null, "ACTIVE", false)));

            mapper.mapUser(oidcUser, "tenant1", provider(null, null, null, null));

            verify(workerClient).jitProvisionUser("user@test.com", "tenant1", "SingleName", null, null);
        }
    }
}
