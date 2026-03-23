package io.kelta.worker.controller;

import io.kelta.worker.repository.BootstrapRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link InternalBootstrapController}.
 *
 * <p>Verifies the internal API endpoints that the gateway uses for
 * bootstrapping its configuration, tenant slug resolution, and
 * OIDC provider lookup.
 */
class InternalBootstrapControllerTest {

    private BootstrapRepository repository;
    private InternalBootstrapController controller;

    @BeforeEach
    void setUp() {
        repository = mock(BootstrapRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        controller = new InternalBootstrapController(repository, objectMapper);
    }

    // ==================== Bootstrap Tests ====================

    @Nested
    @DisplayName("GET /internal/bootstrap")
    class BootstrapTests {

        @Test
        @DisplayName("Should return collections and governor limits")
        void returnsCollectionsAndGovernorLimits() {
            when(repository.findActiveCollections()).thenReturn(List.of(
                    collectionRow("coll-1", "users", "/api/users", true),
                    collectionRow("coll-2", "products", "/api/products", false)
            ));

            when(repository.findTenantLimits()).thenReturn(List.of(
                    tenantLimitsRow("t-1", "{\"apiCallsPerDay\": 50000}"),
                    tenantLimitsRow("t-2", null)
            ));

            ResponseEntity<Map<String, Object>> response = controller.getBootstrapConfig();

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> collections = (List<Map<String, Object>>) body.get("collections");
            assertThat(collections).hasSize(2);
            assertThat(collections.get(0).get("name")).isEqualTo("users");
            assertThat(collections.get(0).get("systemCollection")).isEqualTo(true);
            assertThat(collections.get(1).get("name")).isEqualTo("products");
            assertThat(collections.get(1).get("systemCollection")).isEqualTo(false);

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> governorLimits =
                    (Map<String, Map<String, Object>>) body.get("governorLimits");
            assertThat(governorLimits).hasSize(2);
            assertThat(governorLimits.get("t-1").get("apiCallsPerDay")).isEqualTo(50000);
            assertThat(governorLimits.get("t-2").get("apiCallsPerDay")).isEqualTo(100000);
        }

        @Test
        @DisplayName("Should skip __control-plane collection")
        void skipsControlPlaneCollection() {
            when(repository.findActiveCollections()).thenReturn(List.of(
                    collectionRow("cp-1", "__control-plane", "/control", true),
                    collectionRow("coll-1", "users", "/api/users", true)
            ));
            when(repository.findTenantLimits()).thenReturn(List.of());

            ResponseEntity<Map<String, Object>> response = controller.getBootstrapConfig();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> collections =
                    (List<Map<String, Object>>) response.getBody().get("collections");
            assertThat(collections).hasSize(1);
            assertThat(collections.get(0).get("name")).isEqualTo("users");
        }

        @Test
        @DisplayName("Should handle empty collections")
        void handlesEmptyCollections() {
            when(repository.findActiveCollections()).thenReturn(List.of());
            when(repository.findTenantLimits()).thenReturn(List.of());

            ResponseEntity<Map<String, Object>> response = controller.getBootstrapConfig();

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> collections =
                    (List<Map<String, Object>>) response.getBody().get("collections");
            assertThat(collections).isEmpty();
        }

        @Test
        @DisplayName("Should parse governor limits from JSON string")
        void parsesGovernorLimitsFromJson() {
            when(repository.findActiveCollections()).thenReturn(List.of());
            when(repository.findTenantLimits()).thenReturn(List.of(
                    tenantLimitsRow("t-1", "{\"apiCallsPerDay\": 200000, \"maxUsers\": 500}")
            ));

            ResponseEntity<Map<String, Object>> response = controller.getBootstrapConfig();

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> governorLimits =
                    (Map<String, Map<String, Object>>) response.getBody().get("governorLimits");
            assertThat(governorLimits.get("t-1").get("apiCallsPerDay")).isEqualTo(200000);
        }

        @Test
        @DisplayName("Should handle malformed limits JSON gracefully")
        void handlesMalformedLimitsJson() {
            when(repository.findActiveCollections()).thenReturn(List.of());
            when(repository.findTenantLimits()).thenReturn(List.of(
                    tenantLimitsRow("t-1", "{invalid json}")
            ));

            ResponseEntity<Map<String, Object>> response = controller.getBootstrapConfig();

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> governorLimits =
                    (Map<String, Map<String, Object>>) response.getBody().get("governorLimits");
            assertThat(governorLimits.get("t-1").get("apiCallsPerDay")).isEqualTo(100000);
        }
    }

    // ==================== Slug Map Tests ====================

    @Nested
    @DisplayName("GET /internal/tenants/slug-map")
    class SlugMapTests {

        @Test
        @DisplayName("Should return slug to tenant ID mapping")
        void returnsSlugMapping() {
            when(repository.findRoutableTenants()).thenReturn(List.of(
                    slugRow("t-1", "acme"),
                    slugRow("t-2", "globex")
            ));

            ResponseEntity<Map<String, String>> response = controller.getSlugMap();

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, String> slugMap = response.getBody();
            assertThat(slugMap).hasSize(2);
            assertThat(slugMap.get("acme")).isEqualTo("t-1");
            assertThat(slugMap.get("globex")).isEqualTo("t-2");
        }

        @Test
        @DisplayName("Should handle empty tenant list")
        void handlesEmptyTenantList() {
            when(repository.findRoutableTenants()).thenReturn(List.of());

            ResponseEntity<Map<String, String>> response = controller.getSlugMap();

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("Should skip tenants with null slugs")
        void skipsNullSlugs() {
            when(repository.findRoutableTenants()).thenReturn(List.of(
                    slugRow("t-1", "acme"),
                    slugRow("t-2", null),
                    slugRow("t-3", "")
            ));

            ResponseEntity<Map<String, String>> response = controller.getSlugMap();

            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get("acme")).isEqualTo("t-1");
        }
    }

    // ==================== OIDC Provider Tests ====================

    @Nested
    @DisplayName("GET /internal/oidc/by-issuer")
    class OidcProviderTests {

        @Test
        @DisplayName("Should return OIDC provider info for known issuer")
        void returnsProviderInfoForKnownIssuer() {
            Map<String, Object> providerRow = new HashMap<>();
            providerRow.put("id", "oidc-1");
            providerRow.put("name", "Keycloak");
            providerRow.put("issuer", "https://auth.example.com/realms/emf");
            providerRow.put("jwks_uri", "https://auth.example.com/realms/emf/protocol/openid-connect/certs");
            providerRow.put("audience", "kelta-api");
            providerRow.put("client_id", "kelta-client");
            providerRow.put("roles_claim", "roles");
            providerRow.put("roles_mapping", null);

            when(repository.findOidcProviderByIssuerAndTenant(
                    "https://auth.example.com/realms/emf", "tenant-1"))
                    .thenReturn(Optional.of(providerRow));

            ResponseEntity<Map<String, Object>> response =
                    controller.getOidcProviderByIssuer("https://auth.example.com/realms/emf", "tenant-1");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("id")).isEqualTo("oidc-1");
            assertThat(body.get("name")).isEqualTo("Keycloak");
            assertThat(body.get("jwksUri")).isEqualTo("https://auth.example.com/realms/emf/protocol/openid-connect/certs");
            assertThat(body.get("audience")).isEqualTo("kelta-api");
            assertThat(body.get("clientId")).isEqualTo("kelta-client");
        }

        @Test
        @DisplayName("Should return 404 for unknown issuer")
        void returns404ForUnknownIssuer() {
            when(repository.findOidcProviderByIssuer("https://unknown.example.com"))
                    .thenReturn(Optional.empty());

            ResponseEntity<Map<String, Object>> response =
                    controller.getOidcProviderByIssuer("https://unknown.example.com", null);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        @DisplayName("Should handle provider with null audience")
        void handlesNullAudience() {
            Map<String, Object> providerRow = new HashMap<>();
            providerRow.put("id", "oidc-2");
            providerRow.put("name", "Authentik");
            providerRow.put("issuer", "https://sso.example.com");
            providerRow.put("jwks_uri", "https://sso.example.com/jwks");
            providerRow.put("audience", null);
            providerRow.put("client_id", null);
            providerRow.put("roles_claim", null);
            providerRow.put("roles_mapping", null);

            when(repository.findOidcProviderByIssuer("https://sso.example.com"))
                    .thenReturn(Optional.of(providerRow));

            ResponseEntity<Map<String, Object>> response =
                    controller.getOidcProviderByIssuer("https://sso.example.com", null);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody().get("jwksUri")).isEqualTo("https://sso.example.com/jwks");
            assertThat(response.getBody().get("audience")).isNull();
        }

        @Test
        @DisplayName("Should use tenant-scoped lookup when tenantId provided")
        void usesTenantScopedLookup() {
            Map<String, Object> providerRow = new HashMap<>();
            providerRow.put("id", "oidc-1");
            providerRow.put("name", "Keycloak");
            providerRow.put("issuer", "https://auth.example.com/realms/emf");
            providerRow.put("jwks_uri", "https://auth.example.com/realms/emf/protocol/openid-connect/certs");
            providerRow.put("audience", "kelta-api");
            providerRow.put("client_id", "kelta-client");
            providerRow.put("roles_claim", "roles");
            providerRow.put("roles_mapping", null);

            when(repository.findOidcProviderByIssuerAndTenant(
                    "https://auth.example.com/realms/emf", "tenant-1"))
                    .thenReturn(Optional.of(providerRow));

            ResponseEntity<Map<String, Object>> response =
                    controller.getOidcProviderByIssuer("https://auth.example.com/realms/emf", "tenant-1");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody().get("id")).isEqualTo("oidc-1");
            verify(repository).findOidcProviderByIssuerAndTenant(
                    "https://auth.example.com/realms/emf", "tenant-1");
            verify(repository, never()).findOidcProviderByIssuer(anyString());
        }

        @Test
        @DisplayName("Should reject issuer not belonging to the specified tenant")
        void rejectsIssuerFromDifferentTenant() {
            when(repository.findOidcProviderByIssuerAndTenant(
                    "https://auth.example.com/realms/emf", "tenant-2"))
                    .thenReturn(Optional.empty());

            ResponseEntity<Map<String, Object>> response =
                    controller.getOidcProviderByIssuer("https://auth.example.com/realms/emf", "tenant-2");

            assertThat(response.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        @DisplayName("Should fall back to global lookup when tenantId is not provided")
        void fallsBackToGlobalLookupWithoutTenantId() {
            Map<String, Object> providerRow = new HashMap<>();
            providerRow.put("id", "oidc-1");
            providerRow.put("name", "Keycloak");
            providerRow.put("issuer", "https://auth.example.com/realms/emf");
            providerRow.put("jwks_uri", "https://auth.example.com/realms/emf/protocol/openid-connect/certs");
            providerRow.put("audience", null);
            providerRow.put("client_id", null);
            providerRow.put("roles_claim", null);
            providerRow.put("roles_mapping", null);

            when(repository.findOidcProviderByIssuer("https://auth.example.com/realms/emf"))
                    .thenReturn(Optional.of(providerRow));

            ResponseEntity<Map<String, Object>> response =
                    controller.getOidcProviderByIssuer("https://auth.example.com/realms/emf", null);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            verify(repository).findOidcProviderByIssuer("https://auth.example.com/realms/emf");
            verify(repository, never()).findOidcProviderByIssuerAndTenant(anyString(), anyString());
        }
    }

    // ==================== User Identity Tests ====================

    @Nested
    @DisplayName("GET /internal/user-identity")
    class UserIdentityTests {

        @Test
        @DisplayName("Should return user identity with profileId and profileName")
        void returnsUserIdentity() {
            Map<String, Object> identityRow = new HashMap<>();
            identityRow.put("id", "user-1");
            identityRow.put("profile_id", "profile-1");
            identityRow.put("profile_name", "Standard User");

            when(repository.findUserIdentity("user@test.com", "tenant-1"))
                    .thenReturn(Optional.of(identityRow));

            ResponseEntity<Map<String, Object>> response =
                    controller.getUserIdentity("user@test.com", "tenant-1");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("userId")).isEqualTo("user-1");
            assertThat(body.get("profileId")).isEqualTo("profile-1");
            assertThat(body.get("profileName")).isEqualTo("Standard User");
        }

        @Test
        @DisplayName("Should return 404 when user not found")
        void returns404WhenUserNotFound() {
            when(repository.findUserIdentity("unknown@test.com", "tenant-1"))
                    .thenReturn(Optional.empty());

            ResponseEntity<Map<String, Object>> response =
                    controller.getUserIdentity("unknown@test.com", "tenant-1");

            assertThat(response.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        @DisplayName("Should handle null profile")
        void handlesNullProfile() {
            Map<String, Object> identityRow = new HashMap<>();
            identityRow.put("id", "user-1");
            identityRow.put("profile_id", null);
            identityRow.put("profile_name", null);

            when(repository.findUserIdentity("user@test.com", "tenant-1"))
                    .thenReturn(Optional.of(identityRow));

            ResponseEntity<Map<String, Object>> response =
                    controller.getUserIdentity("user@test.com", "tenant-1");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody().get("profileId")).isNull();
            assertThat(response.getBody().get("profileName")).isNull();
        }
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> collectionRow(String id, String name, String path, boolean system) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("path", path);
        row.put("system_collection", system);
        return row;
    }

    private Map<String, Object> tenantLimitsRow(String id, String limitsJson) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("limits", limitsJson);
        return row;
    }

    private Map<String, Object> slugRow(String id, String slug) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("slug", slug);
        return row;
    }

}
