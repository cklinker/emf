package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the end-to-end authentication flow:
 * direct login → JWT → gateway validates and forwards the request.
 */
@DisplayName("Auth Flow Scenario")
class AuthFlowScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("direct login returns a valid access token")
    void directLoginReturnsAccessToken() {
        String token = auth.loginAsAdmin();

        assertThat(token).isNotBlank();
        // JWT has 3 base64url parts separated by dots
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("access token contains required claims")
    void accessTokenHasRequiredClaims() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);

        assertThat(tenantId).isNotBlank();
        // tenant ID must be a UUID
        assertThat(tenantId).matches("[0-9a-f-]{36}");
    }

    @Test
    @DisplayName("gateway accepts a valid token on a public endpoint")
    void gatewayAcceptsValidToken() {
        String token = auth.loginAsAdmin();

        // /actuator/health is unauthenticated but a valid token should not cause rejection
        var status = gatewayClientWithToken(token)
                .get()
                .uri("/actuator/health")
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();

        assertThat(status.is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("gateway rejects requests with no token on protected endpoints")
    void gatewayRejectsNoToken() {
        String tenantId = auth.extractTenantId(auth.loginAsAdmin());
        String slug     = tenants.slugForTenantId(tenantId);
        assertThat(slug).isNotNull();

        // Unauthenticated access to a collection endpoint should be rejected
        assertThatThrownBy(() ->
                gatewayClient()
                        .get()
                        .uri("/" + slug + "/api/collections")
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOfSatisfying(HttpClientErrorException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
        );
    }

    @Test
    @DisplayName("gateway forwards authenticated request to worker and returns data")
    void gatewayForwardsToWorker() {
        String token    = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug     = tenants.slugForTenantId(tenantId);
        assertThat(slug).isNotNull();

        // /api/collections is a system collection endpoint — returns the list of collections
        @SuppressWarnings("unchecked")
        Map<String, Object> body = gatewayClientWithToken(token)
                .get()
                .uri("/" + slug + "/api/collections")
                .retrieve()
                .body(Map.class);

        // JSON:API response has a "data" array
        assertThat(body).containsKey("data");
    }
}
