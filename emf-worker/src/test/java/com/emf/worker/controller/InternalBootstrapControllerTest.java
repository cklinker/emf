package com.emf.worker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InternalBootstrapController}.
 *
 * <p>Verifies the internal API endpoints that the gateway uses for
 * bootstrapping its configuration, tenant slug resolution, and
 * OIDC provider lookup.
 */
class InternalBootstrapControllerTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private InternalBootstrapController controller;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        controller = new InternalBootstrapController(jdbcTemplate, objectMapper);
    }

    // ==================== Bootstrap Tests ====================

    @Nested
    @DisplayName("GET /internal/bootstrap")
    class BootstrapTests {

        @Test
        @DisplayName("Should return collections and governor limits")
        void returnsCollectionsAndGovernorLimits() {
            // Given: active collections in DB
            List<Map<String, Object>> collectionRows = List.of(
                    collectionRow("coll-1", "users", "/api/users", true),
                    collectionRow("coll-2", "products", "/api/products", false)
            );
            when(jdbcTemplate.queryForList(contains("FROM collection WHERE active")))
                    .thenReturn(collectionRows);

            // Given: tenants with limits
            List<Map<String, Object>> tenantRows = List.of(
                    tenantLimitsRow("t-1", "{\"apiCallsPerDay\": 50000}"),
                    tenantLimitsRow("t-2", null)
            );
            when(jdbcTemplate.queryForList(contains("FROM tenant")))
                    .thenReturn(tenantRows);

            // When
            ResponseEntity<Map<String, Object>> response = controller.getBootstrapConfig();

            // Then
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
            assertThat(governorLimits.get("t-2").get("apiCallsPerDay")).isEqualTo(100000); // default
        }

        @Test
        @DisplayName("Should skip __control-plane collection")
        void skipsControlPlaneCollection() {
            List<Map<String, Object>> collectionRows = List.of(
                    collectionRow("cp-1", "__control-plane", "/control", true),
                    collectionRow("coll-1", "users", "/api/users", true)
            );
            when(jdbcTemplate.queryForList(contains("FROM collection WHERE active")))
                    .thenReturn(collectionRows);
            when(jdbcTemplate.queryForList(contains("FROM tenant")))
                    .thenReturn(List.of());

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
            when(jdbcTemplate.queryForList(contains("FROM collection WHERE active")))
                    .thenReturn(List.of());
            when(jdbcTemplate.queryForList(contains("FROM tenant")))
                    .thenReturn(List.of());

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
            when(jdbcTemplate.queryForList(contains("FROM collection WHERE active")))
                    .thenReturn(List.of());

            List<Map<String, Object>> tenantRows = List.of(
                    tenantLimitsRow("t-1", "{\"apiCallsPerDay\": 200000, \"maxUsers\": 500}")
            );
            when(jdbcTemplate.queryForList(contains("FROM tenant")))
                    .thenReturn(tenantRows);

            ResponseEntity<Map<String, Object>> response = controller.getBootstrapConfig();

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> governorLimits =
                    (Map<String, Map<String, Object>>) response.getBody().get("governorLimits");
            assertThat(governorLimits.get("t-1").get("apiCallsPerDay")).isEqualTo(200000);
        }

        @Test
        @DisplayName("Should handle malformed limits JSON gracefully")
        void handlesMalformedLimitsJson() {
            when(jdbcTemplate.queryForList(contains("FROM collection WHERE active")))
                    .thenReturn(List.of());

            List<Map<String, Object>> tenantRows = List.of(
                    tenantLimitsRow("t-1", "{invalid json}")
            );
            when(jdbcTemplate.queryForList(contains("FROM tenant")))
                    .thenReturn(tenantRows);

            ResponseEntity<Map<String, Object>> response = controller.getBootstrapConfig();

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> governorLimits =
                    (Map<String, Map<String, Object>>) response.getBody().get("governorLimits");
            // Should use default value on parse failure
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
            List<Map<String, Object>> rows = List.of(
                    slugRow("t-1", "acme"),
                    slugRow("t-2", "globex")
            );
            when(jdbcTemplate.queryForList(contains("FROM tenant")))
                    .thenReturn(rows);

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
            when(jdbcTemplate.queryForList(contains("FROM tenant")))
                    .thenReturn(List.of());

            ResponseEntity<Map<String, String>> response = controller.getSlugMap();

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("Should skip tenants with null slugs")
        void skipsNullSlugs() {
            List<Map<String, Object>> rows = List.of(
                    slugRow("t-1", "acme"),
                    slugRow("t-2", null),
                    slugRow("t-3", "")
            );
            when(jdbcTemplate.queryForList(contains("FROM tenant")))
                    .thenReturn(rows);

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
            providerRow.put("audience", "emf-api");
            providerRow.put("client_id", "emf-client");
            providerRow.put("roles_claim", "roles");
            providerRow.put("roles_mapping", null);

            when(jdbcTemplate.queryForList(contains("FROM oidc_provider"),
                    eq("https://auth.example.com/realms/emf")))
                    .thenReturn(List.of(providerRow));

            ResponseEntity<Map<String, Object>> response =
                    controller.getOidcProviderByIssuer("https://auth.example.com/realms/emf");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("id")).isEqualTo("oidc-1");
            assertThat(body.get("name")).isEqualTo("Keycloak");
            assertThat(body.get("issuer")).isEqualTo("https://auth.example.com/realms/emf");
            assertThat(body.get("jwksUri")).isEqualTo("https://auth.example.com/realms/emf/protocol/openid-connect/certs");
            assertThat(body.get("audience")).isEqualTo("emf-api");
            assertThat(body.get("clientId")).isEqualTo("emf-client");
            assertThat(body.get("rolesClaim")).isEqualTo("roles");
        }

        @Test
        @DisplayName("Should return 404 for unknown issuer")
        void returns404ForUnknownIssuer() {
            when(jdbcTemplate.queryForList(contains("FROM oidc_provider"),
                    eq("https://unknown.example.com")))
                    .thenReturn(List.of());

            ResponseEntity<Map<String, Object>> response =
                    controller.getOidcProviderByIssuer("https://unknown.example.com");

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

            when(jdbcTemplate.queryForList(contains("FROM oidc_provider"),
                    eq("https://sso.example.com")))
                    .thenReturn(List.of(providerRow));

            ResponseEntity<Map<String, Object>> response =
                    controller.getOidcProviderByIssuer("https://sso.example.com");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> body = response.getBody();
            assertThat(body.get("jwksUri")).isEqualTo("https://sso.example.com/jwks");
            assertThat(body.get("audience")).isNull();
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
