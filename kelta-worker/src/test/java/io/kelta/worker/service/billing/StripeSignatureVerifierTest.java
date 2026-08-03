package io.kelta.worker.service.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the webhook signature check — the sole trust anchor for a
 * gateway-unauthenticated endpoint. Every branch here must fail closed.
 */
@DisplayName("StripeSignatureVerifier Tests")
class StripeSignatureVerifierTest {

    private static final String SECRET = "whsec_test_secret_value";
    private static final String BODY = "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\"}";

    private final StripeSignatureVerifier verifier = new StripeSignatureVerifier();

    /** Builds a genuine header the way the processor would. */
    private static String header(long timestamp, String body, String secret) {
        return "t=" + timestamp + ",v1=" + sign(timestamp, body, secret);
    }

    private static String sign(long timestamp, String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("Valid signatures")
    class Valid {

        @Test
        @DisplayName("accepts a correctly signed payload")
        void acceptsValidSignature() {
            Instant now = Instant.now();
            String h = header(now.getEpochSecond(), BODY, SECRET);

            assertThat(verifier.verify(h, BODY, SECRET)).isTrue();
        }

        @Test
        @DisplayName("accepts when any of several v1 signatures matches (secret rotation)")
        void acceptsAnyOfMultipleSignatures() {
            long ts = Instant.now().getEpochSecond();
            String good = sign(ts, BODY, SECRET);
            String stale = sign(ts, BODY, "whsec_previous_secret");
            String h = "t=" + ts + ",v1=" + stale + ",v1=" + good;

            assertThat(verifier.verify(h, BODY, SECRET)).isTrue();
        }

        @Test
        @DisplayName("ignores unknown signature schemes")
        void ignoresUnknownSchemes() {
            long ts = Instant.now().getEpochSecond();
            String h = "t=" + ts + ",v0=deadbeef,v1=" + sign(ts, BODY, SECRET);

            assertThat(verifier.verify(h, BODY, SECRET)).isTrue();
        }

        @Test
        @DisplayName("accepts a timestamp at the edge of the tolerance window")
        void acceptsAtToleranceEdge() {
            Instant now = Instant.now();
            long ts = now.minus(Duration.ofMinutes(4)).getEpochSecond();

            assertThat(verifier.verify(header(ts, BODY, SECRET), BODY, SECRET,
                    Duration.ofMinutes(5), now)).isTrue();
        }
    }

    @Nested
    @DisplayName("Rejected signatures")
    class Rejected {

        @Test
        @DisplayName("rejects a signature computed over a different body")
        void rejectsTamperedBody() {
            long ts = Instant.now().getEpochSecond();
            String h = header(ts, BODY, SECRET);

            assertThat(verifier.verify(h, BODY.replace("evt_1", "evt_2"), SECRET)).isFalse();
        }

        @Test
        @DisplayName("rejects a signature made with the wrong secret")
        void rejectsWrongSecret() {
            long ts = Instant.now().getEpochSecond();
            String h = header(ts, BODY, "whsec_attacker_secret");

            assertThat(verifier.verify(h, BODY, SECRET)).isFalse();
        }

        @Test
        @DisplayName("rejects a replayed request outside the tolerance window")
        void rejectsReplayOutsideTolerance() {
            Instant now = Instant.now();
            long ts = now.minus(Duration.ofMinutes(10)).getEpochSecond();

            assertThat(verifier.verify(header(ts, BODY, SECRET), BODY, SECRET,
                    Duration.ofMinutes(5), now)).isFalse();
        }

        @Test
        @DisplayName("rejects a future-dated timestamp outside tolerance")
        void rejectsFutureTimestamp() {
            Instant now = Instant.now();
            long ts = now.plus(Duration.ofMinutes(10)).getEpochSecond();

            assertThat(verifier.verify(header(ts, BODY, SECRET), BODY, SECRET,
                    Duration.ofMinutes(5), now)).isFalse();
        }

        @Test
        @DisplayName("rejects a header with no v1 component")
        void rejectsMissingV1() {
            assertThat(verifier.verify("t=" + Instant.now().getEpochSecond(), BODY, SECRET))
                    .isFalse();
        }

        @Test
        @DisplayName("rejects a header with no timestamp")
        void rejectsMissingTimestamp() {
            assertThat(verifier.verify("v1=deadbeef", BODY, SECRET)).isFalse();
        }

        @Test
        @DisplayName("rejects a non-numeric timestamp")
        void rejectsNonNumericTimestamp() {
            assertThat(verifier.verify("t=not-a-number,v1=deadbeef", BODY, SECRET)).isFalse();
        }

        @Test
        @DisplayName("rejects a non-hex signature without throwing")
        void rejectsNonHexSignature() {
            long ts = Instant.now().getEpochSecond();
            assertThat(verifier.verify("t=" + ts + ",v1=zzzz", BODY, SECRET)).isFalse();
            assertThat(verifier.verify("t=" + ts + ",v1=abc", BODY, SECRET)).isFalse();
        }

        @Test
        @DisplayName("rejects null and blank inputs")
        void rejectsNullAndBlankInputs() {
            long ts = Instant.now().getEpochSecond();
            String h = header(ts, BODY, SECRET);

            assertThat(verifier.verify(null, BODY, SECRET)).isFalse();
            assertThat(verifier.verify("", BODY, SECRET)).isFalse();
            assertThat(verifier.verify(h, null, SECRET)).isFalse();
            assertThat(verifier.verify(h, BODY, null)).isFalse();
            assertThat(verifier.verify(h, BODY, "")).isFalse();
        }

        @Test
        @DisplayName("rejects a garbage header without throwing")
        void rejectsGarbageHeader() {
            assertThat(verifier.verify("not a signature at all", BODY, SECRET)).isFalse();
            assertThat(verifier.verify(",,,,", BODY, SECRET)).isFalse();
            assertThat(verifier.verify("=", BODY, SECRET)).isFalse();
        }
    }

    @Nested
    @DisplayName("Parsing helpers")
    class Parsing {

        @Test
        @DisplayName("parses timestamp and all v1 signatures")
        void parsesHeader() {
            StripeSignatureVerifier.ParsedSignature parsed =
                    StripeSignatureVerifier.parse("t=1699999999,v1=aa,v1=bb");

            assertThat(parsed).isNotNull();
            assertThat(parsed.timestamp()).isEqualTo(1699999999L);
            assertThat(parsed.signatures()).containsExactly("aa", "bb");
        }

        @Test
        @DisplayName("tolerates whitespace around components")
        void tolerksWhitespace() {
            StripeSignatureVerifier.ParsedSignature parsed =
                    StripeSignatureVerifier.parse("t = 1699999999 , v1 = aa");

            assertThat(parsed).isNotNull();
            assertThat(parsed.timestamp()).isEqualTo(1699999999L);
            assertThat(parsed.signatures()).containsExactly("aa");
        }

        @Test
        @DisplayName("decodes hex case-insensitively and rejects bad input")
        void decodesHex() {
            assertThat(StripeSignatureVerifier.decodeHex("00ff")).containsExactly(0, -1);
            assertThat(StripeSignatureVerifier.decodeHex("00FF")).containsExactly(0, -1);
            assertThat(StripeSignatureVerifier.decodeHex("")).isNull();
            assertThat(StripeSignatureVerifier.decodeHex("f")).isNull();
            assertThat(StripeSignatureVerifier.decodeHex("gg")).isNull();
        }
    }
}
