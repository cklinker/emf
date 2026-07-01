package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tenant IP allowlist config through the real stack (gateway → worker →
 * {@code TenantIpAllowlistConfigEventPublisher} → Postgres).
 *
 * <p>This is the layer Mockito worker tests can't reach: it proves the
 * {@code ip_allowlist_enabled} BOOLEAN + {@code ip_allowlist_cidrs} JSONB columns
 * (V148) persist + round-trip against real Postgres under the live NOT NULL / RLS
 * constraints, and that the before-save CIDR validation rejects malformed input with
 * a 4xx rather than storing bad data. Guards the DB-constraint gap where a bad write
 * would slip past unit tests + smoke e2e.
 */
@DisplayName("Tenant IP Allowlist Scenario")
class TenantIpAllowlistScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("persists + round-trips the allowlist and rejects a malformed CIDR")
    void persistsAndValidatesAllowlist() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);
        var client = gatewayClientWithToken(token);

        waitForStatus(client, "/" + slug + "/api/tenants", HttpStatus.OK, 20);

        // 1. Enable the allowlist with two valid CIDRs.
        Map<String, Object> update = Map.of(
                "data", Map.of(
                        "type", "tenants",
                        "id", tenantId,
                        "attributes", Map.of(
                                "ipAllowlistEnabled", true,
                                "ipAllowlistCidrs", List.of("10.0.0.0/8", "192.168.0.0/16"))));

        ResponseEntity<Map> saved = client.patch()
                .uri("/" + slug + "/api/tenants/" + tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(update)
                .retrieve()
                .toEntity(Map.class);
        assertThat(saved.getStatusCode().is2xxSuccessful())
                .as("enabling the allowlist should persist against real Postgres").isTrue();

        // 2. Read it back — the BOOLEAN + JSONB columns round-trip.
        ResponseEntity<Map> readback = client.get()
                .uri("/" + slug + "/api/tenants/" + tenantId)
                .retrieve()
                .toEntity(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) readback.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
        assertThat(attrs.get("ipAllowlistEnabled")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<String> cidrs = (List<String>) attrs.get("ipAllowlistCidrs");
        assertThat(cidrs).containsExactlyInAnyOrder("10.0.0.0/8", "192.168.0.0/16");

        // 3. A malformed CIDR is rejected by the before-save hook (4xx, not stored).
        Map<String, Object> bad = Map.of(
                "data", Map.of(
                        "type", "tenants",
                        "id", tenantId,
                        "attributes", Map.of("ipAllowlistCidrs", List.of("not-a-cidr"))));

        try {
            client.patch()
                    .uri("/" + slug + "/api/tenants/" + tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bad)
                    .retrieve()
                    .toBodilessEntity();
            fail("invalid CIDR should have been rejected with a 4xx");
        } catch (HttpClientErrorException error) {
            assertThat(error.getStatusCode().is4xxClientError())
                    .as("invalid CIDR should be rejected with a client error").isTrue();
        }

        // 4. The rejected write did not clobber the stored ranges.
        ResponseEntity<Map> afterReject = client.get()
                .uri("/" + slug + "/api/tenants/" + tenantId)
                .retrieve()
                .toEntity(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> afterData = (Map<String, Object>) afterReject.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> afterAttrs = (Map<String, Object>) afterData.get("attributes");
        @SuppressWarnings("unchecked")
        List<String> afterCidrs = (List<String>) afterAttrs.get("ipAllowlistCidrs");
        assertThat(afterCidrs).containsExactlyInAnyOrder("10.0.0.0/8", "192.168.0.0/16");
    }
}
