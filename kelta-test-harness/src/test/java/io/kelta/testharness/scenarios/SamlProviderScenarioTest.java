package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Creating a per-tenant SAML provider config (Rec 8 foundation) through the real stack
 * (gateway → worker → PhysicalTableStorageAdapter → {@code saml_provider}, V146).
 *
 * <p>Validates the new {@code saml-providers} system collection end-to-end against a real
 * Postgres with the live RLS + NOT NULL constraints — the layer Mockito worker tests can't
 * exercise. (The kelta-auth SAML SSO flow that consumes this config is a separate slice.)
 */
@DisplayName("SAML Provider Scenario")
class SamlProviderScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("creates and reads back a tenant SAML provider")
    void createsSamlProvider() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/saml-providers", HttpStatus.OK, 20);

        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "type", "saml-providers",
                        "attributes", Map.of(
                                "name", "Acme IdP",
                                "registrationId", "acme",
                                "idpEntityId", "https://idp.acme.example/entity",
                                "ssoUrl", "https://idp.acme.example/sso",
                                "idpCertificate", "-----BEGIN CERTIFICATE-----\nMIIBfake\n-----END CERTIFICATE-----",
                                "emailAttribute", "email",
                                "active", true)));

        ResponseEntity<Map> created = gatewayClientWithToken(token)
                .post()
                .uri("/" + slug + "/api/saml-providers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(Map.class);

        assertThat(created.getStatusCode().is2xxSuccessful())
                .as("SAML provider create should succeed")
                .isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) created.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertThat(attributes)
                .containsEntry("registrationId", "acme")
                .containsEntry("idpEntityId", "https://idp.acme.example/entity");

        // List it back to confirm it persisted under the tenant.
        ResponseEntity<Map> list = gatewayClientWithToken(token)
                .get()
                .uri("/" + slug + "/api/saml-providers")
                .retrieve()
                .toEntity(Map.class);

        assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) list.getBody().get("data");
        assertThat(rows).as("created provider appears in the tenant's list").isNotEmpty();
    }
}
