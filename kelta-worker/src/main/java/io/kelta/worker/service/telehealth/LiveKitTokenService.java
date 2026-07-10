package io.kelta.worker.service.telehealth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * LiveKit access-token minting + webhook verification (telehealth slice 5).
 * LiveKit tokens are plain HS256 JWTs signed with the API secret — no vendor
 * SDK: {@code iss} = API key, {@code sub} = participant identity, a
 * {@code video} claim carries the room-scoped grants. Webhooks arrive with an
 * {@code Authorization} JWT (same secret) whose {@code sha256} claim is the
 * base64 digest of the raw body — verify signature AND digest before trusting
 * an event.
 *
 * <p>Keys are per-environment platform config (never tenant-visible); rooms
 * are tenant-namespaced opaque names, so a token grants exactly one room.
 */
@Service
public class LiveKitTokenService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitTokenService.class);
    private static final Duration DEFAULT_TTL = Duration.ofHours(2);

    private final String url;
    private final String apiKey;
    private final byte[] apiSecret;
    private final boolean configured;

    public LiveKitTokenService(
            @Value("${kelta.telehealth.livekit.url:}") String url,
            @Value("${kelta.telehealth.livekit.api-key:}") String apiKey,
            @Value("${kelta.telehealth.livekit.api-secret:}") String apiSecret) {
        this.configured = url != null && !url.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
        this.url = configured ? url : "ws://localhost:7880";
        this.apiKey = configured ? apiKey : "devkey";
        // MACSigner needs >= 256-bit keys; pad the dev default.
        String secret = configured ? apiSecret : "devsecret-devsecret-devsecret-32";
        this.apiSecret = secret.getBytes(StandardCharsets.UTF_8);
        if (!configured) {
            log.warn("LiveKit is not configured (kelta.telehealth.livekit.*) — using dev defaults. "
                    + "Set KELTA_LIVEKIT_URL / KELTA_LIVEKIT_API_KEY / KELTA_LIVEKIT_API_SECRET.");
        }
    }

    public String serverUrl() {
        return url;
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Mints a room-scoped join token. TTL covers the visit window; never longer-lived. */
    public MintedToken mint(String identity, String displayName, String roomName, Instant expiresAt) {
        try {
            Instant now = Instant.now();
            Instant exp = expiresAt != null && expiresAt.isAfter(now)
                    ? expiresAt : now.plus(DEFAULT_TTL);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(apiKey)
                    .subject(identity)
                    .jwtID(UUID.randomUUID().toString())
                    .notBeforeTime(Date.from(now.minusSeconds(10)))
                    .expirationTime(Date.from(exp))
                    .claim("name", displayName == null ? identity : displayName)
                    .claim("video", Map.of(
                            "roomJoin", true,
                            "room", roomName,
                            "canPublish", true,
                            "canSubscribe", true))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(apiSecret));
            return new MintedToken(jwt.serialize(), exp);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint LiveKit token", e);
        }
    }

    public record MintedToken(String token, Instant expiresAt) {}

    /**
     * Verifies a LiveKit webhook: HS256 signature with the API secret AND the
     * {@code sha256} claim matching the raw body digest. Empty on any mismatch.
     */
    public Optional<String> verifyWebhook(String authorizationHeader, String rawBody) {
        if (authorizationHeader == null || authorizationHeader.isBlank() || rawBody == null) {
            return Optional.empty();
        }
        String token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring("Bearer ".length())
                : authorizationHeader;
        try {
            SignedJWT jwt = SignedJWT.parse(token.trim());
            if (!jwt.verify(new MACVerifier(apiSecret))) {
                return Optional.empty();
            }
            String expected = jwt.getJWTClaimsSet().getStringClaim("sha256");
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawBody.getBytes(StandardCharsets.UTF_8));
            String actual = Base64.getEncoder().encodeToString(digest);
            if (expected == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            Date exp = jwt.getJWTClaimsSet().getExpirationTime();
            if (exp != null && exp.toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            return Optional.ofNullable(jwt.getJWTClaimsSet().getIssuer());
        } catch (Exception e) {
            log.debug("LiveKit webhook verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
