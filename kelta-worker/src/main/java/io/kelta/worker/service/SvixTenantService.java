package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Manages the lifecycle of Svix applications per tenant and brokers
 * Svix portal-access tokens.
 *
 * <p>Calls Svix via REST directly rather than through the Svix Java SDK:
 * the SDK 1.68.0 ships Jackson-2 model annotations and doesn't deserialize
 * cleanly under Spring Boot 4 / Jackson 3.
 *
 * @since 1.0.0
 */
public class SvixTenantService {

    private static final Logger log = LoggerFactory.getLogger(SvixTenantService.class);

    private final RestClient svixRestClient;

    public SvixTenantService(RestClient svixRestClient) {
        this.svixRestClient = svixRestClient;
    }

    /**
     * Ensures a Svix application exists for the given tenant. Uses Svix's
     * {@code get_if_exists=true} flag so a pre-existing app is returned
     * instead of producing a 422.
     *
     * @param tenantId   the tenant UUID (used as the Svix app uid)
     * @param tenantName the tenant display name
     */
    public void ensureApplication(String tenantId, String tenantName) {
        try {
            Map<String, Object> body = Map.of(
                    "name", tenantName != null ? tenantName : tenantId,
                    "uid", tenantId
            );
            svixRestClient.post()
                    .uri(uri -> uri.path("/api/v1/app/")
                            .queryParam("get_if_exists", "true")
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Ensured Svix application for tenant '{}' (id={})", tenantName, tenantId);
        } catch (Exception e) {
            log.error("Failed to ensure Svix application for tenant '{}' (id={}): {}",
                    tenantName, tenantId, e.getMessage());
        }
    }

    /**
     * Deletes the Svix application for a tenant.
     *
     * @param tenantId the tenant UUID (matches the Svix app uid)
     */
    public void deleteApplication(String tenantId) {
        try {
            svixRestClient.delete()
                    .uri("/api/v1/app/{appId}/", tenantId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Deleted Svix application for tenant '{}'", tenantId);
        } catch (Exception e) {
            log.error("Failed to delete Svix application for tenant '{}': {}",
                    tenantId, e.getMessage());
        }
    }

    /**
     * Generates a short-lived portal access token for the given tenant's
     * Svix application. Throws on upstream failure so the caller can map
     * the error.
     *
     * @param tenantId the tenant UUID (matches the Svix app uid)
     * @return the portal token and URL
     */
    public PortalAccess getPortalAccess(String tenantId) {
        Map<String, Object> response = svixRestClient.post()
                .uri("/api/v1/auth/app-portal-access/{appId}/", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null) {
            throw new IllegalStateException("Empty response from Svix portal-access endpoint");
        }
        return new PortalAccess(
                (String) response.get("token"),
                (String) response.get("url")
        );
    }

    public record PortalAccess(String token, String url) {}
}
