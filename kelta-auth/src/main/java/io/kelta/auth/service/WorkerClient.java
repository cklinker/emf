package io.kelta.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.auth.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

@Service
public class WorkerClient {

    private static final Logger log = LoggerFactory.getLogger(WorkerClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WorkerClient(AuthProperties properties, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getWorkerUrl())
                .build();
        this.objectMapper = objectMapper;
    }

    public record OidcProviderInfo(
            String id, String issuer, String jwksUri, String audience,
            String clientId, String rolesClaim, String groupsClaim
    ) {}

    public record UserIdentity(
            String userId, String profileId, String profileName
    ) {}

    public Optional<OidcProviderInfo> findOidcProviderByIssuer(String issuer, String tenantId) {
        try {
            String url = tenantId != null
                    ? "/internal/oidc/by-issuer?issuer={issuer}&tenantId={tenantId}"
                    : "/internal/oidc/by-issuer?issuer={issuer}";

            JsonNode response = tenantId != null
                    ? restClient.get()
                        .uri(url, issuer, tenantId)
                        .retrieve()
                        .body(JsonNode.class)
                    : restClient.get()
                        .uri(url, issuer)
                        .retrieve()
                        .body(JsonNode.class);

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(new OidcProviderInfo(
                    response.path("id").asText(null),
                    response.path("issuer").asText(null),
                    response.path("jwksUri").asText(null),
                    response.path("audience").asText(null),
                    response.path("clientId").asText(null),
                    response.path("rolesClaim").asText(null),
                    response.path("groupsClaim").asText(null)
            ));
        } catch (Exception e) {
            log.warn("Failed to look up OIDC provider for issuer {}: {}", issuer, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<UserIdentity> findUserIdentity(String email, String tenantId) {
        try {
            JsonNode response = restClient.get()
                    .uri("/internal/user-identity?email={email}&tenantId={tenantId}", email, tenantId)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(new UserIdentity(
                    response.path("userId").asText(null),
                    response.path("profileId").asText(null),
                    response.path("profileName").asText(null)
            ));
        } catch (Exception e) {
            log.warn("Failed to look up user identity for {}: {}", email, e.getMessage());
            return Optional.empty();
        }
    }
}
