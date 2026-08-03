package io.kelta.auth.service.botchallenge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AltchaBotChallengeVerifier Tests")
class AltchaBotChallengeVerifierTest {

    private static final String HMAC_KEY = "test-shared-secret";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Default: every challenge is seen for the first time.
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
    }

    private AltchaBotChallengeVerifier verifier() {
        return new AltchaBotChallengeVerifier(redisTemplate, objectMapper, true, HMAC_KEY, 1000, 600);
    }

    /** Solves a challenge the way a browser widget would: brute force. */
    private String solve(Map<String, Object> challenge) {
        String salt = (String) challenge.get("salt");
        String target = (String) challenge.get("challenge");
        int max = (int) challenge.get("maxnumber");
        for (int n = 0; n <= max; n++) {
            if (sha256Hex(salt + n).equals(target)) {
                return encode(Map.of(
                        "algorithm", challenge.get("algorithm"),
                        "challenge", target,
                        "number", n,
                        "salt", salt,
                        "signature", challenge.get("signature")));
            }
        }
        throw new AssertionError("challenge was unsolvable within maxnumber");
    }

    private String encode(Map<String, Object> solution) {
        return Base64.getEncoder().encodeToString(
                objectMapper.writeValueAsString(solution).getBytes(StandardCharsets.UTF_8));
    }

    /** The signature the verifier would produce for a challenge. */
    private static String hmacHex(String value) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("refuses to start when enabled without a shared HMAC key")
        void requiresHmacKeyWhenEnabled() {
            // A per-pod random key would verify only on the pod that issued the
            // challenge, so a share of real signups would fail with no visible
            // cause. Failing startup is the honest outcome.
            assertThatThrownBy(() -> new AltchaBotChallengeVerifier(
                    redisTemplate, objectMapper, true, "  ", 1000, 600))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("hmac-key");
        }

        @Test
        @DisplayName("starts without a key when disabled")
        void noKeyNeededWhenDisabled() {
            var disabled = new AltchaBotChallengeVerifier(
                    redisTemplate, objectMapper, false, null, 1000, 600);

            assertThat(disabled.enabled()).isFalse();
        }

        @Test
        @DisplayName("a disabled verifier passes everything through and issues nothing")
        void disabledPassesThrough() {
            var disabled = new AltchaBotChallengeVerifier(
                    redisTemplate, objectMapper, false, null, 1000, 600);

            // DISABLED, not VALID: callers can tell "no challenge required here"
            // from "this solution was checked and passed".
            assertThat(disabled.verify(null)).isEqualTo(BotChallengeVerifier.Result.DISABLED);
            assertThat(disabled.verify("garbage")).isEqualTo(BotChallengeVerifier.Result.DISABLED);
            assertThatThrownBy(disabled::issue).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Issuing")
    class Issuing {

        @Test
        @DisplayName("issues a solvable, signed, expiring challenge")
        void issuesSolvableChallenge() {
            Map<String, Object> challenge = verifier().issue();

            assertThat(challenge).containsKeys("algorithm", "challenge", "maxnumber", "salt", "signature");
            assertThat(challenge.get("algorithm")).isEqualTo("SHA-256");
            assertThat((String) challenge.get("salt")).contains("?expires=");
            assertThat(verifier().verify(solve(challenge))).isEqualTo(BotChallengeVerifier.Result.VALID);
        }

        @Test
        @DisplayName("issues a distinct challenge each time")
        void issuesDistinctChallenges() {
            // A repeated challenge would be solvable once and replayed forever.
            var v = verifier();
            assertThat(v.issue().get("challenge")).isNotEqualTo(v.issue().get("challenge"));
        }
    }

    @Nested
    @DisplayName("Verification")
    class Verification {

        @Test
        @DisplayName("accepts a correctly solved challenge")
        void acceptsValidSolution() {
            var v = verifier();
            assertThat(v.verify(solve(v.issue()))).isEqualTo(BotChallengeVerifier.Result.VALID);
        }

        @Test
        @DisplayName("reports a missing solution distinctly from a wrong one")
        void reportsMissing() {
            var v = verifier();
            assertThat(v.verify(null)).isEqualTo(BotChallengeVerifier.Result.MISSING);
            assertThat(v.verify("   ")).isEqualTo(BotChallengeVerifier.Result.MISSING);
        }

        @Test
        @DisplayName("rejects a self-issued challenge — the signature must be ours")
        void rejectsForgedChallenge() {
            // THE critical case: without the HMAC check a client could publish a
            // trivial challenge (e.g. number 0), 'solve' it instantly, and the
            // proof of work would cost nothing.
            String salt = "aabbcc?expires=" + Instant.now().plusSeconds(300).getEpochSecond();
            String forged = sha256Hex(salt + 0);
            String payload = encode(Map.of(
                    "algorithm", "SHA-256", "challenge", forged, "number", 0,
                    "salt", salt, "signature", "deadbeef"));

            assertThat(verifier().verify(payload)).isEqualTo(BotChallengeVerifier.Result.INVALID);
        }

        @Test
        @DisplayName("rejects a signed challenge answered with the wrong number")
        void rejectsWrongNumber() {
            var v = verifier();
            Map<String, Object> challenge = v.issue();
            Map<String, Object> solution = new HashMap<>(challenge);
            solution.remove("maxnumber");
            // Deliberately not the solution — proves the hash check does work
            // independently of the signature check.
            solution.put("number", -1);

            assertThat(v.verify(encode(solution))).isEqualTo(BotChallengeVerifier.Result.INVALID);
        }

        @Test
        @DisplayName("rejects an expired challenge even when correctly signed and solved")
        void rejectsExpired() {
            // Signature and hash both check out — only the stamped expiry says no.
            // Without this a challenge solved once stays usable indefinitely.
            assertThat(verifier().verify(signedSolutionExpiring(-3600)))
                    .isEqualTo(BotChallengeVerifier.Result.INVALID);
            assertThat(verifier().verify(signedSolutionExpiring(3600)))
                    .isEqualTo(BotChallengeVerifier.Result.VALID);
        }

        @Test
        @DisplayName("rejects a salt with no expiry stamp")
        void rejectsSaltWithoutExpiry() {
            // Only challenges we issued get this far (the signature matched), and
            // we always stamp one — a missing stamp means the format drifted, and
            // accepting it would mint a challenge that never ages out.
            String salt = "0011223344";
            String challenge = sha256Hex(salt + 7);
            String payload = encode(Map.of(
                    "algorithm", "SHA-256", "challenge", challenge, "number", 7,
                    "salt", salt, "signature", hmacHex(challenge)));

            assertThat(verifier().verify(payload)).isEqualTo(BotChallengeVerifier.Result.INVALID);
        }

        /** A properly signed, correctly solved challenge with a chosen expiry offset. */
        private String signedSolutionExpiring(long secondsFromNow) {
            String salt = "0011223344?expires=" + Instant.now().plusSeconds(secondsFromNow).getEpochSecond();
            String challenge = sha256Hex(salt + 7);
            return encode(Map.of(
                    "algorithm", "SHA-256", "challenge", challenge, "number", 7,
                    "salt", salt, "signature", hmacHex(challenge)));
        }

        @Test
        @DisplayName("rejects a solution for an unexpected algorithm")
        void rejectsWrongAlgorithm() {
            var v = verifier();
            Map<String, Object> solution = new HashMap<>(v.issue());
            solution.put("algorithm", "MD5");
            solution.put("number", 0);

            assertThat(v.verify(encode(solution))).isEqualTo(BotChallengeVerifier.Result.INVALID);
        }

        @Test
        @DisplayName("rejects undecodable or incomplete payloads without throwing")
        void rejectsMalformed() {
            var v = verifier();
            for (String bad : new String[]{
                    "not-base64!!!",
                    Base64.getEncoder().encodeToString("{not json".getBytes(StandardCharsets.UTF_8)),
                    Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8)),
                    encode(Map.of("algorithm", "SHA-256", "challenge", "x", "salt", "y"))}) {
                assertThat(v.verify(bad)).as("payload: %s", bad)
                        .isEqualTo(BotChallengeVerifier.Result.INVALID);
            }
        }
    }

    @Nested
    @DisplayName("Replay protection")
    class ReplayProtection {

        @Test
        @DisplayName("a solution can only be used once")
        void rejectsReplay() {
            // Without this the whole mechanism collapses: one unit of work would
            // buy unlimited requests.
            var v = verifier();
            String solved = solve(v.issue());
            when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true, false);

            assertThat(v.verify(solved)).isEqualTo(BotChallengeVerifier.Result.VALID);
            assertThat(v.verify(solved)).isEqualTo(BotChallengeVerifier.Result.INVALID);
        }

        @Test
        @DisplayName("claims the challenge in Redis, not in memory")
        void claimsAcrossTheFleet() {
            // Several auth pods serve this endpoint; a per-pod set would admit
            // the same solution once per pod.
            var v = verifier();
            Map<String, Object> challenge = v.issue();

            v.verify(solve(challenge));

            verify(valueOps).setIfAbsent(
                    eq("botchallenge:used:" + challenge.get("challenge")), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("fails CLOSED when Redis is unavailable")
        void failsClosedWithoutRedis() {
            // Opposite of the rate limiter's fail-open posture, deliberately: a
            // limiter failing open leaves the other controls standing, while a
            // challenge failing open is an open door on the endpoint it exists
            // to protect. The path stays rate limited regardless.
            var v = verifier();
            String solved = solve(v.issue());
            when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenThrow(new org.springframework.dao.QueryTimeoutException("redis down"));

            assertThat(v.verify(solved)).isEqualTo(BotChallengeVerifier.Result.INVALID);
        }

        @Test
        @DisplayName("does not claim a challenge that failed verification")
        void doesNotClaimInvalid() {
            // Claiming first would let an attacker burn a victim's in-flight
            // challenge by submitting a bad solution for it.
            var v = verifier();
            Map<String, Object> challenge = v.issue();
            Map<String, Object> solution = new HashMap<>(challenge);
            solution.remove("maxnumber");
            solution.put("number", -1);

            v.verify(encode(solution));

            verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        }
    }
}
