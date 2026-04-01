package io.kelta.auth.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.kelta.auth.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkerClient {

    private static final Logger log = LoggerFactory.getLogger(WorkerClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public WorkerClient(AuthProperties properties,
                        ObjectMapper objectMapper,
                        @Value("${kelta.internal.token:}") String internalToken) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getWorkerUrl())
                .build();
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
    }

    /**
     * Sends an email via the worker's internal email endpoint.
     *
     * @return true if the email was successfully queued, false on failure
     */
    public boolean sendEmail(String tenantId, String to, String subject, String body, String source) {
        try {
            Map<String, String> requestBody = Map.of(
                    "tenantId", tenantId,
                    "to", to,
                    "subject", subject,
                    "body", body,
                    "source", source
            );

            restClient.post()
                    .uri("/api/internal/email/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", internalToken)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            return true;
        } catch (Exception e) {
            log.error("Failed to send email via worker: to={}, source={}, error={}", to, source, e.getMessage());
            return false;
        }
    }

    public record OidcProviderInfo(
            String id, String name, String issuer, String jwksUri, String audience,
            String clientId, String clientSecretEnc,
            String rolesClaim, String groupsClaim,
            String groupsProfileMapping,
            String authorizationUri, String tokenUri,
            String userinfoUri, String endSessionUri,
            String emailClaim, String usernameClaim, String nameClaim
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

            return Optional.of(parseOidcProviderInfo(response));
        } catch (Exception e) {
            log.warn("Failed to look up OIDC provider for issuer {}: {}", issuer, e.getMessage());
            return Optional.empty();
        }
    }

    public List<OidcProviderInfo> findActiveOidcProviders(String tenantId) {
        try {
            JsonNode response = restClient.get()
                    .uri("/internal/oidc/providers?tenantId={tenantId}", tenantId)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray()) {
                return List.of();
            }

            List<OidcProviderInfo> providers = new ArrayList<>();
            for (JsonNode node : response) {
                providers.add(parseOidcProviderInfo(node));
            }
            return providers;
        } catch (Exception e) {
            log.warn("Failed to list OIDC providers for tenant {}: {}", tenantId, e.getMessage());
            return List.of();
        }
    }

    public record JitProvisionResult(
            String userId, String profileId, String profileName,
            String status, boolean created
    ) {}

    public Optional<JitProvisionResult> jitProvisionUser(
            String email, String tenantId, String firstName, String lastName, String profileId) {
        try {
            Map<String, Object> body = Map.of(
                    "email", email,
                    "tenantId", tenantId,
                    "firstName", firstName != null ? firstName : "",
                    "lastName", lastName != null ? lastName : "",
                    "profileId", profileId != null ? profileId : ""
            );

            // Remove empty profileId
            java.util.Map<String, Object> requestBody = new java.util.LinkedHashMap<>(body);
            if (profileId == null) {
                requestBody.remove("profileId");
            }

            JsonNode response = restClient.post()
                    .uri("/internal/user-identity/jit")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(new JitProvisionResult(
                    response.path("userId").asText(null),
                    response.path("profileId").asText(null),
                    response.path("profileName").asText(null),
                    response.path("status").asText(null),
                    response.path("created").asBoolean(false)
            ));
        } catch (Exception e) {
            log.warn("Failed to JIT provision user {}: {}", email, e.getMessage());
            return Optional.empty();
        }
    }

    private OidcProviderInfo parseOidcProviderInfo(JsonNode response) {
        return new OidcProviderInfo(
                response.path("id").asText(null),
                response.path("name").asText(null),
                response.path("issuer").asText(null),
                response.path("jwksUri").asText(null),
                response.path("audience").asText(null),
                response.path("clientId").asText(null),
                response.path("clientSecretEnc").asText(null),
                response.path("rolesClaim").asText(null),
                response.path("groupsClaim").asText(null),
                response.path("groupsProfileMapping").asText(null),
                response.path("authorizationUri").asText(null),
                response.path("tokenUri").asText(null),
                response.path("userinfoUri").asText(null),
                response.path("endSessionUri").asText(null),
                response.path("emailClaim").asText(null),
                response.path("usernameClaim").asText(null),
                response.path("nameClaim").asText(null)
        );
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

    // -----------------------------------------------------------------------
    // SMS Verification
    // -----------------------------------------------------------------------

    public boolean sendSmsCode(String phoneNumber, String tenantId) {
        try {
            restClient.post()
                    .uri("/internal/sms/send")
                    .body(Map.of("phoneNumber", phoneNumber, "tenantId", tenantId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("Failed to send SMS code via worker: phone={}, error={}", phoneNumber, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public boolean verifySmsCode(String phoneNumber, String code, String tenantId) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/internal/sms/verify")
                    .body(Map.of("phoneNumber", phoneNumber, "code", code, "tenantId", tenantId))
                    .retrieve()
                    .body(Map.class);
            return response != null && Boolean.TRUE.equals(response.get("verified"));
        } catch (Exception e) {
            log.error("Failed to verify SMS code via worker: error={}", e.getMessage());
            return false;
        }
    }
}
