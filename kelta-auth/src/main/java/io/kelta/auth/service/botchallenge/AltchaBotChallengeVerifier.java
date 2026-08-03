package io.kelta.auth.service.botchallenge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Self-hosted ALTCHA-compatible proof-of-work challenge.
 *
 * <p>The scheme: the server picks a secret number, publishes
 * {@code challenge = SHA-256(salt ‖ number)} plus an HMAC over that challenge, and
 * the client brute-forces the number back out. Verification is one hash and one
 * HMAC — the cost is entirely on the client, and the server keeps no per-challenge
 * state until a solution actually arrives.
 *
 * <p>Nothing here talks to a third party: the widget is served from the same
 * origin and the challenge is issued and verified in this process. That is the
 * reason for choosing this scheme over a hosted CAPTCHA.
 *
 * <p><b>Three things must all hold</b> for a solution to pass, and each closes a
 * distinct hole:
 * <ul>
 *   <li>the HMAC matches — proves <em>we</em> issued this challenge, so a client
 *       cannot invent an easy one and solve it;</li>
 *   <li>the hash matches — proves the work was actually done;</li>
 *   <li>the salt has not expired and the challenge has not been seen before —
 *       without replay rejection one solved challenge buys unlimited requests,
 *       which defeats the entire mechanism.</li>
 * </ul>
 *
 * <p>Replay state lives in <b>Redis</b>, not memory: with several auth pods a
 * per-pod set would let the same solution through once per pod. For the same
 * reason the HMAC key must be configured explicitly — a per-pod random key means
 * a challenge issued by one pod fails on another, so startup refuses rather than
 * shipping a control that intermittently rejects real users.
 */
@Service
public class AltchaBotChallengeVerifier implements BotChallengeVerifier {

    private static final Logger log = LoggerFactory.getLogger(AltchaBotChallengeVerifier.class);

    private static final String ALGORITHM = "SHA-256";
    private static final String REPLAY_KEY_PREFIX = "botchallenge:used:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean enabled;
    private final int maxNumber;
    private final Duration ttl;
    private final byte[] hmacKey;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AltchaBotChallengeVerifier(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${kelta.auth.bot-challenge.enabled:false}") boolean enabled,
            @Value("${kelta.auth.bot-challenge.hmac-key:}") String hmacKey,
            @Value("${kelta.auth.bot-challenge.max-number:100000}") int maxNumber,
            @Value("${kelta.auth.bot-challenge.ttl-seconds:600}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxNumber = Math.max(1, maxNumber);
        this.ttl = Duration.ofSeconds(Math.max(30, ttlSeconds));

        if (enabled && (hmacKey == null || hmacKey.isBlank())) {
            // Refusing to start beats starting with a key that differs per pod:
            // challenges would verify only on the pod that issued them, so a
            // share of real signups would fail for no visible reason.
            throw new IllegalStateException(
                    "kelta.auth.bot-challenge.enabled=true requires kelta.auth.bot-challenge.hmac-key "
                    + "(a shared secret — every auth pod must use the same value)");
        }
        this.hmacKey = hmacKey == null ? new byte[0] : hmacKey.getBytes(StandardCharsets.UTF_8);

        if (enabled) {
            log.info("Bot challenge ENABLED (maxNumber={}, ttl={}s)", this.maxNumber, this.ttl.toSeconds());
        } else {
            log.info("Bot challenge disabled (kelta.auth.bot-challenge.enabled=false)");
        }
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public Map<String, Object> issue() {
        if (!enabled) {
            throw new IllegalStateException("Bot challenge is disabled");
        }
        byte[] saltBytes = new byte[12];
        RANDOM.nextBytes(saltBytes);
        // The expiry travels inside the salt, so it is covered by the challenge
        // hash and therefore by the signature — a client cannot extend it.
        String salt = HexFormat.of().formatHex(saltBytes)
                + "?expires=" + Instant.now().plus(ttl).getEpochSecond();
        int number = RANDOM.nextInt(maxNumber + 1);
        String challenge = sha256Hex(salt + number);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("algorithm", ALGORITHM);
        payload.put("challenge", challenge);
        payload.put("maxnumber", maxNumber);
        payload.put("salt", salt);
        payload.put("signature", hmacHex(challenge));
        return payload;
    }

    @Override
    public Result verify(String payload) {
        if (!enabled) {
            return Result.DISABLED;
        }
        if (payload == null || payload.isBlank()) {
            return Result.MISSING;
        }

        JsonNode solution;
        try {
            solution = objectMapper.readTree(new String(
                    Base64.getDecoder().decode(payload.trim()), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            log.debug("Bot challenge solution was not decodable: {}", e.getMessage());
            return Result.INVALID;
        }

        String algorithm = text(solution, "algorithm");
        String challenge = text(solution, "challenge");
        String salt = text(solution, "salt");
        String signature = text(solution, "signature");
        JsonNode numberNode = solution.get("number");
        if (challenge == null || salt == null || signature == null
                || numberNode == null || !numberNode.isNumber()) {
            return Result.INVALID;
        }
        if (!ALGORITHM.equals(algorithm)) {
            return Result.INVALID;
        }

        // Ours? Constant-time — a byte-by-byte compare leaks the signature.
        if (!MessageDigest.isEqual(hmacHex(challenge).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            log.debug("Bot challenge signature mismatch");
            return Result.INVALID;
        }
        if (expired(salt)) {
            log.debug("Bot challenge expired");
            return Result.INVALID;
        }
        // Solved? Compared constant-time for the same reason.
        String expected = sha256Hex(salt + numberNode.asLong());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                challenge.getBytes(StandardCharsets.US_ASCII))) {
            log.debug("Bot challenge solution did not match");
            return Result.INVALID;
        }
        if (!claim(challenge)) {
            log.warn("Bot challenge replay rejected");
            return Result.INVALID;
        }
        return Result.VALID;
    }

    /**
     * Claims a challenge exactly once across the fleet. Redis is the arbiter, so
     * two pods cannot both accept the same solution.
     *
     * <p>Fails <b>closed</b>: if Redis is unreachable the solution is rejected.
     * That is the opposite of the rate limiter's fail-open posture, deliberately —
     * a limiter that fails open still leaves the other controls standing, while a
     * challenge that fails open during an outage is an open door on the exact
     * endpoint this exists to protect. The path stays rate limited either way.
     */
    private boolean claim(String challenge) {
        try {
            Boolean fresh = redisTemplate.opsForValue()
                    .setIfAbsent(REPLAY_KEY_PREFIX + challenge, "1", ttl);
            return Boolean.TRUE.equals(fresh);
        } catch (RuntimeException e) {
            log.error("Bot challenge replay check failed (Redis unavailable) — rejecting: {}",
                    e.getMessage());
            return false;
        }
    }

    /** Reads {@code ?expires=<epochSeconds>} out of the salt. */
    private boolean expired(String salt) {
        int marker = salt.indexOf("?expires=");
        if (marker < 0) {
            // Only challenges we issued reach here (the signature already
            // matched), and we always stamp an expiry — so a missing one means
            // the format changed underneath us. Reject rather than accept
            // something that would never age out.
            return true;
        }
        try {
            long expires = Long.parseLong(salt.substring(marker + "?expires=".length()).trim());
            return Instant.now().getEpochSecond() > expires;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private String hmacHex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute challenge signature", e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? null : value.stringValue();
    }
}
