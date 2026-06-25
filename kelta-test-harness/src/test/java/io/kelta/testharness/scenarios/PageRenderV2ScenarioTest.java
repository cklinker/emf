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
 * Rendering a v2 page through the real stack (gateway → worker → QueryEngine → Postgres) and back via
 * {@code GET /api/pages/{slug}/render}.
 *
 * <p>The mocked-{@code QueryEngine} {@code PageRenderServiceTest} proves the parsing matrix but runs
 * with no DB, so it cannot catch a worker-side {@code config} JSON serialization drop on a real
 * round-trip. Per the DB-constraint / serialization test-gap convention, this scenario authors a v2
 * {@code config} (page-level {@code variables} + {@code dataSources} alongside the {@code components}
 * tree, all inside the single {@code config} JSON column), publishes it, then renders it and asserts
 * the page-level siblings survived (de)serialization and the whole {@code config} round-trips as
 * {@code tree} with the components byte-identical.
 */
@DisplayName("Page Render v2 Scenario")
class PageRenderV2ScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("renders a published v2 page, surfacing variables/dataSources and the verbatim tree")
    @SuppressWarnings("unchecked")
    void rendersV2Page() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/ui-pages", HttpStatus.OK, 20);

        // v2 config: components tree + page-level variables/dataSources siblings, all in `config`.
        List<Map<String, Object>> components = List.of(
                Map.of("id", "h1", "type", "heading", "props", Map.of("text", "Open tickets")),
                Map.of("id", "t1", "type", "table", "props", Map.of("source", "data.tickets")));
        List<Map<String, Object>> variables = List.of(
                Map.of("name", "statusFilter", "type", "string", "default", "open"));
        List<Map<String, Object>> dataSources = List.of(
                Map.of("name", "tickets", "collection", "tickets", "mode", "list"));
        Map<String, Object> config = Map.of(
                "schemaVersion", 2,
                "layout", Map.of("kind", "grid"),
                "variables", variables,
                "dataSources", dataSources,
                "components", components);

        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "type", "ui-pages",
                        "attributes", Map.of(
                                "name", "Render V2 Page",
                                "path", "/render-v2",
                                "title", "Render V2 Page",
                                "published", true,
                                "config", config)));

        ResponseEntity<Map> createResponse = gatewayClientWithToken(token)
                .post()
                .uri("/" + slug + "/api/ui-pages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(Map.class);

        assertThat(createResponse.getStatusCode().is2xxSuccessful())
                .as("v2 page create should succeed")
                .isTrue();
        Map<String, Object> data = (Map<String, Object>) createResponse.getBody().get("data");
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        String pageSlug = (String) attributes.get("slug");
        assertThat(pageSlug).as("slug auto-derived from name").isNotBlank();

        // Render through the gateway and assert the round-tripped contract.
        ResponseEntity<Map> render = gatewayClientWithToken(token)
                .get()
                .uri("/" + slug + "/api/pages/" + pageSlug + "/render")
                .retrieve()
                .toEntity(Map.class);

        assertThat(render.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> contract = render.getBody();
        assertThat(contract.get("version")).isEqualTo("2.0");

        List<Map<String, Object>> renderedVariables = (List<Map<String, Object>>) contract.get("variables");
        assertThat(renderedVariables).as("variables survive Postgres JSON round-trip").hasSize(1);
        assertThat(renderedVariables.getFirst().get("name")).isEqualTo("statusFilter");

        List<Map<String, Object>> renderedDataSources = (List<Map<String, Object>>) contract.get("dataSources");
        assertThat(renderedDataSources).as("dataSources survive round-trip").hasSize(1);
        assertThat(renderedDataSources.getFirst().get("collection")).isEqualTo("tickets");

        // tree is the whole config; its components must be byte-identical to what was authored.
        Map<String, Object> tree = (Map<String, Object>) contract.get("tree");
        assertThat(tree).as("tree carries the whole config").containsKeys("components", "variables", "dataSources");
        assertThat(tree.get("components"))
                .as("components round-trip with no drop or reordering")
                .isEqualTo(components);
    }
}
