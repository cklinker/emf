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
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Per-page authorization (slice 1h) through the real stack (gateway → worker → Postgres + RLS).
 *
 * <p>The mocked-permission {@code PageRenderServiceTest} stubs {@code findProfileSystemPermissions} and
 * so cannot observe whether the real grant read is correctly RLS-scoped, nor whether the gateway
 * actually forwards {@code X-User-Profile-Id} to the worker — which the whole gate hinges on. This
 * scenario closes that gap: it renders restricted pages over the authorized path and asserts the
 * grant-backed 200 and the deny → <b>404</b> (not 403, so a restricted page's existence is not leaked).
 *
 * <p>The admin profile is granted {@code VIEW_SETUP}, so a page requiring it renders (200); a page
 * requiring a permission no profile holds is denied (404) — both decisions made by the real
 * {@code profile_system_permission} read under tenant RLS.
 */
@DisplayName("Page Render Authz Scenario")
class PageRenderAuthzScenarioTest extends ScenarioBase {

    private void createPage(String token, String tenantSlug, String name, String path,
                            Map<String, Object> config) {
        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "type", "ui-pages",
                        "attributes", Map.of(
                                "name", name,
                                "path", path,
                                "title", name,
                                "published", true,
                                "config", config)));
        ResponseEntity<Map> response = gatewayClientWithToken(token)
                .post()
                .uri("/" + tenantSlug + "/api/ui-pages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("page create").isTrue();
    }

    @Test
    @DisplayName("a granted permission renders (200); a non-granted permission is denied (404)")
    @SuppressWarnings("unchecked")
    void gatesRenderOnSystemPermission() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);
        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/ui-pages", HttpStatus.OK, 20);

        List<Map<String, Object>> components = List.of(
                Map.of("id", "h1", "type", "heading", "props", Map.of("text", "Admin")));

        // (a) Restricted to VIEW_SETUP — the admin profile holds it → 200.
        createPage(token, slug, "Authz Allowed", "/authz-allowed", Map.of(
                "schemaVersion", 2,
                "access", Map.of("requiredPermission", "VIEW_SETUP"),
                "components", components));

        ResponseEntity<Map> allowed = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/pages/authz-allowed/render")
                .retrieve().toEntity(Map.class);
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allowed.getBody().get("version")).isEqualTo("2.0");

        // (b) Restricted to a permission no profile is granted → denied → 404 (not 403, no body).
        createPage(token, slug, "Authz Denied", "/authz-denied", Map.of(
                "schemaVersion", 2,
                "access", Map.of("requiredPermission", "NEVER_GRANTED_PERMISSION"),
                "components", components));

        HttpClientErrorException denied = catchThrowableOfType(
                () -> gatewayClientWithToken(token)
                        .get().uri("/" + slug + "/api/pages/authz-denied/render")
                        .retrieve().toEntity(Map.class),
                HttpClientErrorException.class);
        assertThat(denied).as("denied render is a 404").isNotNull();
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // (c) Unrestricted page renders for the same caller (back-compat under the real stack).
        createPage(token, slug, "Authz Open", "/authz-open", Map.of(
                "schemaVersion", 2,
                "components", components));
        ResponseEntity<Map> open = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/pages/authz-open/render")
                .retrieve().toEntity(Map.class);
        assertThat(open.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
