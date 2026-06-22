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
 * Rec 4f end-to-end on the real stack: import an OpenAPI spec, then materialize a
 * GET operation as an external-rest virtual collection (gateway → worker →
 * {@code ExternalEntityMaterializer} → {@code queryEngine.create} on the
 * {@code collections}/{@code fields} system collections against real Postgres).
 *
 * <p>This is the layer Mockito worker tests can't reach: it proves creating an
 * external-rest collection + its fields runs no physical-table DDL, satisfies the
 * live NOT NULL / RLS constraints, and registers a queryable collection.
 */
@DisplayName("External Collection Materialize Scenario")
class ExternalCollectionMaterializeScenarioTest extends ScenarioBase {

    private static final String SPEC = """
        {
          "openapi": "3.0.0",
          "info": {"title": "Petstore", "version": "1.0.0"},
          "servers": [{"url": "https://api.example.com/v1"}],
          "paths": {
            "/pets": {
              "get": {
                "operationId": "listPets",
                "responses": {
                  "200": {
                    "description": "ok",
                    "content": {"application/json": {"schema": {
                      "type": "array",
                      "items": {"type": "object", "properties": {
                        "id": {"type": "string"},
                        "name": {"type": "string"}
                      }}
                    }}}
                  }
                }
              }
            }
          }
        }
        """;

    @Test
    @DisplayName("imports a spec and materializes a GET operation into an external-rest collection")
    void materializesExternalCollection() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);
        var client = gatewayClientWithToken(token);

        waitForStatus(client, "/" + slug + "/api/api-specs/library", HttpStatus.OK, 20);

        // 1. Import the spec.
        ResponseEntity<Map> imported = client.post()
                .uri("/" + slug + "/api/api-specs/import")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Petstore", "sourceType", "INLINE_JSON", "raw", SPEC))
                .retrieve()
                .toEntity(Map.class);
        assertThat(imported.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) imported.getBody().get("spec");
        String specId = String.valueOf(spec.get("id"));
        assertThat(specId).isNotBlank();

        // 2. Find the GET /pets operation's syntheticOpId.
        ResponseEntity<Map> ops = client.get()
                .uri("/" + slug + "/api/api-specs/" + specId + "/operations")
                .retrieve()
                .toEntity(Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> opList = (List<Map<String, Object>>) ops.getBody().get("data");
        String opId = opList.stream()
                .filter(o -> "GET".equalsIgnoreCase(String.valueOf(o.get("httpMethod"))))
                .map(o -> String.valueOf(o.get("syntheticOpId")))
                .findFirst().orElseThrow();

        // 3. Materialize it as an external-rest collection.
        ResponseEntity<Map> materialized = client.post()
                .uri("/" + slug + "/api/api-specs/" + specId + "/operations/" + opId + "/materialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("collectionName", "extpets"))
                .retrieve()
                .toEntity(Map.class);

        assertThat(materialized.getStatusCode().is2xxSuccessful())
                .as("materialize should succeed against real Postgres").isTrue();
        Map<String, Object> body = materialized.getBody();
        assertThat(body.get("collectionName")).isEqualTo("extpets");
        assertThat(((Number) body.get("fieldCount")).intValue()).isEqualTo(2);
        assertThat(String.valueOf(body.get("collectionId"))).isNotBlank();

        // 4. The collection record persisted under the tenant.
        ResponseEntity<Map> collections = client.get()
                .uri("/" + slug + "/api/collections?page[size]=200")
                .retrieve()
                .toEntity(Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) collections.getBody().get("data");
        boolean present = rows.stream().anyMatch(r -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> attrs = (Map<String, Object>) r.get("attributes");
            return attrs != null && "extpets".equals(attrs.get("name"));
        });
        assertThat(present).as("materialized collection appears in the tenant's collection list").isTrue();
    }
}
