package io.kelta.worker.service.push;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Cloud Messaging (FCM) HTTP v1 push notification provider.
 *
 * <p>Uses the FCM HTTP v1 API with Google service account credentials for authentication.
 * Supports per-tenant FCM configuration overrides via {@link TenantPushSettings}.
 *
 * <p>Platform defaults are configured via application properties:
 * <ul>
 *   <li>{@code kelta.push.fcm.project-id} — Google Cloud project ID</li>
 *   <li>{@code kelta.push.fcm.credentials-path} — Path to service account JSON key file</li>
 * </ul>
 *
 * <p>Access tokens are cached (50-min TTL) per credential set to avoid excessive token exchanges.
 *
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(name = "kelta.push.provider", havingValue = "fcm")
public class FcmPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(FcmPushProvider.class);

    private static final String FCM_SEND_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(50);

    private final String platformProjectId;
    private final String platformClientEmail;
    private final PrivateKey platformPrivateKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final Cache<String, CachedToken> tokenCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(TOKEN_TTL)
            .build();

    public FcmPushProvider(
            @Value("${kelta.push.fcm.project-id:}") String projectId,
            @Value("${kelta.push.fcm.credentials-path:}") String credentialsPath,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();

        if (credentialsPath != null && !credentialsPath.isBlank()) {
            try {
                JsonNode credentials = objectMapper.readTree(Files.readString(Path.of(credentialsPath), StandardCharsets.UTF_8));
                this.platformProjectId = projectId.isBlank() ? credentials.get("project_id").asText() : projectId;
                this.platformClientEmail = credentials.get("client_email").asText();
                this.platformPrivateKey = parsePrivateKey(credentials.get("private_key").asText());
                log.info("FCM provider initialized with project: {}", this.platformProjectId);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load FCM credentials from: " + credentialsPath, e);
            }
        } else {
            this.platformProjectId = projectId.isBlank() ? null : projectId;
            this.platformClientEmail = null;
            this.platformPrivateKey = null;
            log.warn("FCM provider initialized without platform credentials — per-tenant config required");
        }
    }

    // Package-private constructor for testing
    FcmPushProvider(String projectId, String clientEmail, PrivateKey privateKey,
                    RestClient restClient, ObjectMapper objectMapper) {
        this.platformProjectId = projectId;
        this.platformClientEmail = clientEmail;
        this.platformPrivateKey = privateKey;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(PushMessage message, TenantPushSettings tenantSettings) throws PushDeliveryException {
        String projectId = resolveProjectId(tenantSettings);
        String accessToken = resolveAccessToken(tenantSettings);

        Map<String, Object> fcmMessage = buildFcmPayload(message);
        String url = String.format(FCM_SEND_URL, projectId);

        try {
            String responseBody = restClient.post()
                    .uri(url)
                    .headers(h -> {
                        h.setBearerAuth(accessToken);
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(Map.of("message", fcmMessage))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        handleFcmError(body, message.deviceToken());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new PushDeliveryException(
                                "FCM server error: " + response.getStatusCode(), false);
                    })
                    .body(String.class);

            log.debug("FCM push sent successfully: {}", responseBody);

        } catch (PushDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new PushDeliveryException("FCM delivery failed: " + e.getMessage(), false);
        }
    }

    private Map<String, Object> buildFcmPayload(PushMessage message) {
        Map<String, Object> fcmMessage = new HashMap<>();

        fcmMessage.put("token", message.deviceToken());

        Map<String, String> notification = new HashMap<>();
        notification.put("title", message.title());
        notification.put("body", message.body());
        fcmMessage.put("notification", notification);

        if (message.data() != null && !message.data().isEmpty()) {
            fcmMessage.put("data", message.data());
        }

        // Platform-specific config
        switch (message.platform()) {
            case "android" -> {
                Map<String, Object> android = new HashMap<>();
                android.put("priority", "high");
                fcmMessage.put("android", android);
            }
            case "ios" -> {
                Map<String, Object> apns = new HashMap<>();
                Map<String, Object> apnsHeaders = new HashMap<>();
                apnsHeaders.put("apns-priority", "10");
                apns.put("headers", apnsHeaders);
                fcmMessage.put("apns", apns);
            }
            case "web" -> {
                Map<String, Object> webpush = new HashMap<>();
                Map<String, Object> webHeaders = new HashMap<>();
                webHeaders.put("Urgency", "high");
                webpush.put("headers", webHeaders);
                fcmMessage.put("webpush", webpush);
            }
            default -> { /* no platform-specific config */ }
        }

        return fcmMessage;
    }

    private void handleFcmError(String responseBody, String deviceToken) throws PushDeliveryException {
        try {
            JsonNode error = objectMapper.readTree(responseBody);
            JsonNode errorNode = error.has("error") ? error.get("error") : null;
            String status = errorNode != null && errorNode.has("status") ? errorNode.get("status").asText() : "";
            String errorMessage = errorNode != null && errorNode.has("message")
                    ? errorNode.get("message").asText() : responseBody;

            boolean isInvalidToken = "NOT_FOUND".equals(status)
                    || "INVALID_ARGUMENT".equals(status)
                    || errorMessage.contains("not a valid FCM registration token")
                    || errorMessage.contains("Requested entity was not found");

            if (isInvalidToken) {
                log.info("FCM reports invalid/expired token for device: {}", deviceToken);
            }

            throw new PushDeliveryException("FCM error: " + errorMessage, isInvalidToken);

        } catch (PushDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new PushDeliveryException("FCM error: " + responseBody, false);
        }
    }

    private String resolveProjectId(TenantPushSettings tenantSettings) {
        if (tenantSettings != null && tenantSettings.hasFcmOverride()) {
            return tenantSettings.fcmProjectId();
        }
        if (platformProjectId == null || platformProjectId.isBlank()) {
            throw new PushDeliveryException("No FCM project ID configured", false);
        }
        return platformProjectId;
    }

    private String resolveAccessToken(TenantPushSettings tenantSettings) {
        String clientEmail;
        PrivateKey privateKey;

        if (tenantSettings != null && tenantSettings.hasFcmOverride()) {
            clientEmail = tenantSettings.fcmClientEmail();
            privateKey = parsePrivateKey(tenantSettings.fcmPrivateKey());
        } else {
            if (platformClientEmail == null || platformPrivateKey == null) {
                throw new PushDeliveryException("No FCM credentials configured", false);
            }
            clientEmail = platformClientEmail;
            privateKey = platformPrivateKey;
        }

        CachedToken cached = tokenCache.getIfPresent(clientEmail);
        if (cached != null) {
            return cached.token();
        }

        String token = exchangeForAccessToken(clientEmail, privateKey);
        tokenCache.put(clientEmail, new CachedToken(token));
        return token;
    }

    private String exchangeForAccessToken(String clientEmail, PrivateKey privateKey) {
        String jwt = createSignedJwt(clientEmail, privateKey);

        try {
            String responseBody = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + jwt)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            return response.get("access_token").asText();

        } catch (Exception e) {
            throw new PushDeliveryException("Failed to obtain FCM access token: " + e.getMessage(), false);
        }
    }

    private String createSignedJwt(String clientEmail, PrivateKey privateKey) {
        long now = Instant.now().getEpochSecond();

        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = base64Url(String.format(
                "{\"iss\":\"%s\",\"scope\":\"%s\",\"aud\":\"%s\",\"iat\":%d,\"exp\":%d}",
                clientEmail, FCM_SCOPE, TOKEN_URL, now, now + 3600));

        String signingInput = header + "." + payload;

        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sig.sign());
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new PushDeliveryException("Failed to sign JWT for FCM auth: " + e.getMessage(), false);
        }
    }

    static PrivateKey parsePrivateKey(String pem) {
        try {
            String keyContent = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid RSA private key: " + e.getMessage(), e);
        }
    }

    private static String base64Url(String input) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    // Package-private for testing — allows pre-populating the token cache
    void putCachedToken(String clientEmail, String token) {
        tokenCache.put(clientEmail, new CachedToken(token));
    }

    private record CachedToken(String token) {}
}
