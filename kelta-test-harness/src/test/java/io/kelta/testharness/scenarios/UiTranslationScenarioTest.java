package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Tenant i18n authoring (app-intelligence slice 4) through the real stack: the
 * {@code ui-translations} system collection rides the generic dynamic route onto the
 * V165 table, so what is proven here is the real DB behavior Mockito can't —
 * the {@code UNIQUE (tenant_id, locale, translation_key)} constraint rejects a
 * duplicate override while the same key in another locale succeeds.
 */
@DisplayName("UI Translation Scenario")
class UiTranslationScenarioTest extends ScenarioBase {

    private static final String URI = "/api/ui-translations";

    @Test
    @DisplayName("translations are unique per (tenant, locale, key); other locales are free")
    @SuppressWarnings("unchecked")
    void translationsAreUniquePerLocaleAndKey() {
        String adminToken = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(adminToken);
        String slug = tenants.slugForTenantId(tenantId);
        waitForStatus(gatewayClientWithToken(adminToken), "/" + slug + URI, HttpStatus.OK, 60);

        String key = "probe.greeting-" + Long.toHexString(System.nanoTime());

        // ---- Create the base override -> 201
        ResponseEntity<Map> created = gatewayClientWithToken(adminToken)
                .post().uri("/" + slug + URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(key, "de", "Hallo"))
                .retrieve().toEntity(Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String rowId = (String) ((Map<String, Object>) created.getBody().get("data")).get("id");
        assertThat(rowId).isNotBlank();

        // ---- Duplicate (same tenant, locale, key) -> rejected by the DB constraint
        Exception duplicate = catchThrowableOfType(Exception.class, () ->
                gatewayClientWithToken(adminToken)
                        .post().uri("/" + slug + URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body(key, "de", "Hallo again"))
                        .retrieve().toEntity(Map.class));
        assertThat(duplicate)
                .isInstanceOfAny(HttpClientErrorException.class, HttpServerErrorException.class);

        // ---- Same key, different locale -> 201 (constraint is per locale)
        ResponseEntity<Map> french = gatewayClientWithToken(adminToken)
                .post().uri("/" + slug + URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(key, "fr", "Bonjour"))
                .retrieve().toEntity(Map.class);
        assertThat(french.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // ---- The overrides read back through the same authorized route
        ResponseEntity<Map> listed = gatewayClientWithToken(adminToken)
                .get().uri("/" + slug + URI + "?filter[key][eq]=" + key + "&page[size]=10")
                .retrieve().toEntity(Map.class);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((java.util.List<?>) listed.getBody().get("data"))).hasSize(2);
    }

    private static Map<String, Object> body(String key, String locale, String value) {
        return Map.of("data", Map.of(
                "type", "ui-translations",
                "attributes", Map.of(
                        "locale", locale,
                        "key", key,
                        "value", value)));
    }
}
