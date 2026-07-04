package io.kelta.worker.service.push;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * Apple Push Notification service (APNs) provider using token-based (JWT) auth over the
 * APNs HTTP/2 provider API.
 *
 * <p>Mirrors {@link FcmPushProvider}'s dependency-free approach: no vendor SDK — the ES256
 * provider JWT is signed with {@code java.security} and requests go over the JDK's native
 * HTTP/2 {@link HttpClient}. Only iOS device tokens are supported (APNs is Apple-only).
 *
 * <p>Platform config via application properties (env {@code KELTA_PUSH_APNS_*}):
 * <ul>
 *   <li>{@code kelta.push.apns.team-id} — Apple Developer team ID (JWT {@code iss})</li>
 *   <li>{@code kelta.push.apns.key-id} — the auth key's Key ID (JWT header {@code kid})</li>
 *   <li>{@code kelta.push.apns.bundle-id} — the app bundle id ({@code apns-topic})</li>
 *   <li>{@code kelta.push.apns.auth-key-path} — path to the {@code .p8} auth key (PKCS#8 PEM)</li>
 *   <li>{@code kelta.push.apns.sandbox} — {@code true} → sandbox host (default {@code false})</li>
 * </ul>
 *
 * <p>The provider JWT is cached (40-min TTL); APNs accepts a token for up to an hour.
 * Per-tenant APNs overrides are not yet supported (platform config only) — a follow-up.
 *
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(name = "kelta.push.provider", havingValue = "apns")
public class ApnsPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(ApnsPushProvider.class);

    private static final String PROD_HOST = "https://api.push.apple.com";
    private static final String SANDBOX_HOST = "https://api.sandbox.push.apple.com";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(40);

    /** Minimal HTTP seam so unit tests don't need a live HTTP/2 endpoint. */
    interface ApnsHttpSender {
        ApnsResponse send(String url, Map<String, String> headers, String body) throws Exception;
    }

    record ApnsResponse(int status, String body) {}

    private final String teamId;
    private final String keyId;
    private final String bundleId;
    private final String baseUrl;
    private final PrivateKey authKey;
    private final ApnsHttpSender sender;
    private final ObjectMapper objectMapper;

    private final Cache<String, CachedToken> tokenCache = Caffeine.newBuilder()
            .maximumSize(16)
            .expireAfterWrite(TOKEN_TTL)
            .build();

    public ApnsPushProvider(
            @Value("${kelta.push.apns.team-id:}") String teamId,
            @Value("${kelta.push.apns.key-id:}") String keyId,
            @Value("${kelta.push.apns.bundle-id:}") String bundleId,
            @Value("${kelta.push.apns.auth-key-path:}") String authKeyPath,
            @Value("${kelta.push.apns.sandbox:false}") boolean sandbox,
            ObjectMapper objectMapper) {

        this.teamId = teamId;
        this.keyId = keyId;
        this.bundleId = bundleId;
        this.baseUrl = sandbox ? SANDBOX_HOST : PROD_HOST;
        this.objectMapper = objectMapper;
        this.sender = jdkHttp2Sender();

        if (authKeyPath != null && !authKeyPath.isBlank()) {
            try {
                this.authKey = parseEcPrivateKey(
                        Files.readString(Path.of(authKeyPath), StandardCharsets.UTF_8));
                log.info("APNs provider initialized (team={}, key={}, topic={}, sandbox={})",
                        teamId, keyId, bundleId, sandbox);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load APNs auth key from: " + authKeyPath, e);
            }
        } else {
            this.authKey = null;
            log.warn("APNs provider initialized without an auth key — set kelta.push.apns.auth-key-path");
        }
    }

    // Package-private constructor for testing — inject a fake sender + a test key.
    ApnsPushProvider(String teamId, String keyId, String bundleId, String baseUrl,
                     PrivateKey authKey, ApnsHttpSender sender, ObjectMapper objectMapper) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.bundleId = bundleId;
        this.baseUrl = baseUrl;
        this.authKey = authKey;
        this.sender = sender;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(PushMessage message, TenantPushSettings tenantSettings) throws PushDeliveryException {
        if (authKey == null) {
            throw new PushDeliveryException("No APNs auth key configured", false);
        }
        if (!"ios".equals(message.platform())) {
            throw new PushDeliveryException(
                    "APNs only supports iOS device tokens, got: " + message.platform(), false);
        }

        String jwt = resolveJwt();
        String body = buildApnsPayload(message);
        String url = baseUrl + "/3/device/" + message.deviceToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "bearer " + jwt);
        headers.put("apns-topic", bundleId);
        headers.put("apns-push-type", "alert");
        headers.put("apns-priority", "10");

        try {
            ApnsResponse response = sender.send(url, headers, body);
            if (response.status() == 200) {
                log.debug("APNs push sent successfully to device: {}", message.deviceToken());
                return;
            }
            handleApnsError(response, message.deviceToken());
        } catch (PushDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new PushDeliveryException("APNs delivery failed: " + e.getMessage(), false);
        }
    }

    String buildApnsPayload(PushMessage message) {
        Map<String, Object> aps = new HashMap<>();
        Map<String, String> alert = new HashMap<>();
        alert.put("title", message.title());
        alert.put("body", message.body());
        aps.put("alert", alert);

        Map<String, Object> payload = new HashMap<>();
        payload.put("aps", aps);
        // APNs custom data is placed at the top level, alongside `aps` (never inside it).
        if (message.data() != null) {
            message.data().forEach(payload::put);
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new PushDeliveryException("Failed to build APNs payload: " + e.getMessage(), false);
        }
    }

    private void handleApnsError(ApnsResponse response, String deviceToken) throws PushDeliveryException {
        String reason = "";
        try {
            if (response.body() != null && !response.body().isBlank()) {
                JsonNode node = objectMapper.readTree(response.body());
                if (node.has("reason")) {
                    reason = node.get("reason").asText();
                }
            }
        } catch (Exception ignored) {
            // non-JSON body — fall through with the raw body in the message
        }

        // APNs marks a token as permanently bad via these reasons (or a 410 Gone).
        boolean isInvalidToken = response.status() == 410
                || "BadDeviceToken".equals(reason)
                || "Unregistered".equals(reason)
                || "DeviceTokenNotForTopic".equals(reason);

        if (isInvalidToken) {
            log.info("APNs reports invalid/expired token for device: {}", deviceToken);
        }

        String detail = reason.isBlank() ? String.valueOf(response.body()) : reason;
        throw new PushDeliveryException("APNs error (" + response.status() + "): " + detail, isInvalidToken);
    }

    private String resolveJwt() {
        CachedToken cached = tokenCache.getIfPresent(keyId);
        if (cached != null) {
            return cached.token();
        }
        String jwt = createSignedJwt();
        tokenCache.put(keyId, new CachedToken(jwt));
        return jwt;
    }

    private String createSignedJwt() {
        long now = Instant.now().getEpochSecond();

        String header = base64Url("{\"alg\":\"ES256\",\"kid\":\"" + keyId + "\"}");
        String payload = base64Url("{\"iss\":\"" + teamId + "\",\"iat\":" + now + "}");
        String signingInput = header + "." + payload;

        try {
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initSign(authKey);
            sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
            // Java emits a DER-encoded ECDSA signature; JOSE ES256 needs raw R||S (64 bytes).
            byte[] rawSignature = derToJoseSignature(sig.sign(), 64);
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature);
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new PushDeliveryException("Failed to sign APNs JWT: " + e.getMessage(), false);
        }
    }

    /**
     * Convert a DER-encoded ECDSA signature ({@code SEQUENCE { INTEGER r, INTEGER s }}) into the
     * fixed-length {@code R||S} concatenation JOSE/ES256 requires.
     */
    static byte[] derToJoseSignature(byte[] der, int outputLength) {
        int offset = 0;
        if (der[offset++] != 0x30) {
            throw new IllegalArgumentException("Invalid ECDSA signature: missing SEQUENCE");
        }
        // Sequence length (P-256 signatures are well under 128 bytes → single-byte length).
        int seqLen = der[offset++] & 0xff;
        if (seqLen < 0) {
            throw new IllegalArgumentException("Invalid ECDSA signature: bad sequence length");
        }
        if (der[offset++] != 0x02) {
            throw new IllegalArgumentException("Invalid ECDSA signature: missing R integer");
        }
        int rLen = der[offset++] & 0xff;
        byte[] r = new byte[rLen];
        System.arraycopy(der, offset, r, 0, rLen);
        offset += rLen;
        if (der[offset++] != 0x02) {
            throw new IllegalArgumentException("Invalid ECDSA signature: missing S integer");
        }
        int sLen = der[offset++] & 0xff;
        byte[] s = new byte[sLen];
        System.arraycopy(der, offset, s, 0, sLen);

        int half = outputLength / 2;
        byte[] out = new byte[outputLength];
        copyRightAligned(r, out, 0, half);
        copyRightAligned(s, out, half, half);
        return out;
    }

    /** Strip a DER INTEGER's optional leading sign byte and right-align it into a fixed slot. */
    private static void copyRightAligned(byte[] src, byte[] dest, int destOffset, int len) {
        int start = 0;
        while (start < src.length - 1 && src[start] == 0) {
            start++;
        }
        int srcLen = src.length - start;
        if (srcLen > len) {
            throw new IllegalArgumentException("ECDSA integer longer than target width");
        }
        System.arraycopy(src, start, dest, destOffset + len - srcLen, srcLen);
    }

    static PrivateKey parseEcPrivateKey(String pem) {
        try {
            String keyContent = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid APNs EC private key: " + e.getMessage(), e);
        }
    }

    private static ApnsHttpSender jdkHttp2Sender() {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return (url, headers, body) -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(builder::header);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new ApnsResponse(response.statusCode(), response.body());
        };
    }

    private static String base64Url(String input) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    private record CachedToken(String token) {}
}
