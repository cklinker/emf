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
 * Mass-email campaign create + enqueue through the real stack (gateway → worker → Postgres)
 * against the {@code email_campaign} table (V152) and the {@code CampaignAdminController}.
 *
 * <p>Exercises the MANAGE_CAMPAIGNS-gated admin route, the read-only "campaigns" system
 * collection listing, and the {@code /send} enqueue transition (DRAFT → QUEUED) — the DB-constraint
 * + RLS + permission path that the Mockito worker tests can't reach.
 */
@DisplayName("Campaign Create Scenario")
class CampaignCreateScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("creates a campaign, lists it, and enqueues it for sending")
    @SuppressWarnings("unchecked")
    void createsAndEnqueuesCampaign() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/admin/campaigns", HttpStatus.OK, 20);

        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "type", "campaigns",
                        "attributes", Map.of(
                                "name", "Q3 Newsletter",
                                "description", "Quarterly product update",
                                "subject", "Hello ${email}",
                                "bodyHtml", "<p>Hi there</p>",
                                "targetCollection", "contacts",
                                "recipientEmailField", "email")));

        ResponseEntity<Map> created = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/admin/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve().toEntity(Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful())
                .as("campaign create should succeed against real Postgres").isTrue();

        Map<String, Object> data = (Map<String, Object>) created.getBody().get("data");
        String campaignId = (String) data.get("id");
        assertThat(campaignId).as("created campaign has an id").isNotBlank();

        // Read-only "campaigns" system collection lists the new row.
        ResponseEntity<Map> listed = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/admin/campaigns")
                .retrieve().toEntity(Map.class);
        List<Map<String, Object>> campaigns = (List<Map<String, Object>>) listed.getBody().get("data");
        assertThat(campaigns).as("the created campaign is listed").isNotEmpty();

        // Enqueue for sending: DRAFT → QUEUED.
        ResponseEntity<Map> sent = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/admin/campaigns/" + campaignId + "/send")
                .retrieve().toEntity(Map.class);
        assertThat(sent.getStatusCode().is2xxSuccessful())
                .as("enqueue should be accepted").isTrue();
        assertThat(sent.getBody().get("status")).isEqualTo("QUEUED");

        // The persisted campaign has left DRAFT (QUEUED, or already picked up by the runner).
        ResponseEntity<Map> fetched = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/admin/campaigns/" + campaignId)
                .retrieve().toEntity(Map.class);
        Map<String, Object> attrs = (Map<String, Object>) ((Map<String, Object>) fetched.getBody().get("data")).get("attributes");
        assertThat(attrs.get("status"))
                .as("campaign has been enqueued")
                .isIn("QUEUED", "SENDING", "SENT", "FAILED");
    }
}
