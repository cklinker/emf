package io.kelta.worker.service.push;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.ECPrivateKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * Browser Web Push over VAPID (RFC 8292) with RFC 8291 aes128gcm payloads.
 *
 * <p>Registered only when VAPID keys are configured, and <b>alongside</b> the
 * property-selected mobile provider rather than instead of it — a tenant can have
 * both browser and native subscribers, and {@link DefaultPushService} routes per
 * device.
 *
 * <p><b>Deliberately no push library.</b> The available ones pull BouncyCastle,
 * whose reflective surface must be enumerated in {@code reflect-config.json} for
 * the native worker or it fails only in production. The VAPID JWT uses nimbus
 * (already on the classpath and already native-proven via LiveKit token minting)
 * and the payload encryption uses JDK crypto — see {@link WebPushCrypto}, which
 * is verified against the RFC's own test vector.
 *
 * <p>404 and 410 mean the subscription is gone for good; those are surfaced as
 * invalid-token failures so {@code DefaultPushService} prunes the device, exactly
 * as it does for a stale FCM/APNs token.
 */
@Component
public class WebPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(WebPushProvider.class);

    static final String PLATFORM = "web";
    /** Payload record size; the browser requirement is at least 4096. */
    private static final int RECORD_SIZE = 4096;
    /** VAPID JWTs are short-lived; the spec caps them at 24h. */
    private static final long JWT_TTL_HOURS = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final PrivateKey privateKey;
    private final String subject;

    public WebPushProvider(ObjectMapper objectMapper,
                           @Value("${kelta.push.vapid.public-key:}") String publicKey,
                           @Value("${kelta.push.vapid.private-key:}") String privateKey,
                           @Value("${kelta.push.vapid.subject:}") String subject) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
        this.publicKey = publicKey;
        this.subject = subject == null || subject.isBlank() ? "mailto:admin@kelta.io" : subject;
        this.privateKey = parsePrivateKey(privateKey);
        // Always a bean: on the native image @ConditionalOnProperty is fixed at
        // AOT build time, so a key configured at deploy time could never enable
        // web push. Configuration is judged at runtime instead (supports()).
        if (configured()) {
            log.info("WebPushProvider active (VAPID subject {})", this.subject);
        } else {
            log.info("WebPushProvider idle — no VAPID key pair configured");
        }
    }

    /** True when a usable VAPID key pair is present. */
    private boolean configured() {
        return publicKey != null && !publicKey.isBlank() && privateKey != null;
    }

    /** Only handles browser subscriptions, and only once a key pair is configured. */
    @Override
    public boolean supports(String platform) {
        return PLATFORM.equalsIgnoreCase(platform) && configured();
    }

    /** The key a frontend passes to {@code pushManager.subscribe}. */
    public String publicKey() {
        return publicKey;
    }

    @Override
    public void send(PushMessage message, TenantPushSettings tenantSettings)
            throws PushDeliveryException {
        if (privateKey == null) {
            throw new PushDeliveryException("VAPID private key is not configured", false);
        }
        if (message.subscription() == null || message.subscription().isBlank()) {
            // The token is only a hash of the endpoint, so without the stored
            // subscription there is nothing to send to.
            throw new PushDeliveryException("device has no stored web push subscription", true);
        }

        JsonNode subscription;
        try {
            subscription = objectMapper.readTree(message.subscription());
        } catch (RuntimeException e) {
            throw new PushDeliveryException("unreadable web push subscription", true);
        }

        String endpoint = text(subscription, "endpoint");
        JsonNode keys = subscription.path("keys");
        String p256dh = text(keys, "p256dh");
        String auth = text(keys, "auth");
        if (endpoint == null || p256dh == null || auth == null) {
            throw new PushDeliveryException("incomplete web push subscription", true);
        }

        byte[] body;
        String vapidToken;
        try {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            body = WebPushCrypto.encrypt(
                    payload(message).getBytes(StandardCharsets.UTF_8),
                    decode(p256dh), decode(auth), salt,
                    WebPushCrypto.generateEphemeralKeys(), RECORD_SIZE);
            vapidToken = vapidToken(endpoint);
        } catch (Exception e) {
            // A crypto failure is ours, not the subscription's — do not prune.
            throw new PushDeliveryException("web push encryption failed: " + e.getMessage(), false);
        }

        ResponseEntity<String> response = restClient
                .method(HttpMethod.POST)
                .uri(endpoint)
                .header("TTL", "86400")
                .header("Content-Encoding", "aes128gcm")
                .header("Authorization", "vapid t=" + vapidToken + ", k=" + publicKey)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body)
                // Read the status rather than letting RestClient throw, so 404/410
                // can be distinguished from a transient 5xx.
                .retrieve()
                .onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);

        int status = response.getStatusCode().value();
        if (status / 100 == 2) {
            return;
        }
        if (status == 404 || status == 410) {
            // The subscription is permanently gone — prune the device.
            throw new PushDeliveryException("subscription expired (HTTP " + status + ")", true);
        }
        throw new PushDeliveryException("push service returned HTTP " + status, false);
    }

    /**
     * VAPID JWT (RFC 8292): audience is the push service's origin, not the full
     * endpoint — sending the endpoint leaks the subscription into the token.
     */
    String vapidToken(String endpoint) throws Exception {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(audience)
                .expirationTime(Date.from(Instant.now().plus(JWT_TTL_HOURS, ChronoUnit.HOURS)))
                .subject(subject)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims);
        jwt.sign(new ECDSASigner(privateKey, com.nimbusds.jose.jwk.Curve.P_256));
        return jwt.serialize();
    }

    private String payload(PushMessage message) {
        var node = objectMapper.createObjectNode();
        node.put("title", message.title());
        node.put("body", message.body());
        if (message.data() != null && !message.data().isEmpty()) {
            var data = node.putObject("data");
            message.data().forEach(data::put);
        }
        return node.toString();
    }

    /** Base64url, tolerating the padded form some clients send. */
    static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value.replace("=", ""));
    }

    private PrivateKey parsePrivateKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            BigInteger s = new BigInteger(1, decode(encoded));
            return KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(s, WebPushCrypto.p256Params()));
        } catch (Exception e) {
            // Log without the key material.
            log.error("VAPID private key could not be parsed — web push is disabled: {}",
                    e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String s = value.stringValue();
        return s.isBlank() ? null : s;
    }
}
