package io.kelta.worker.service.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPrivateKeySpec;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the Web Push encryption against <b>RFC 8291 §5's own worked example</b>.
 *
 * <p>This is the test that makes hand-rolling the crypto defensible. An
 * encryption bug in this area does not throw and does not fail a smoke test — it
 * produces a body that every browser silently discards, so members simply stop
 * receiving pushes with nothing in any log to explain it. Matching the RFC's
 * published ciphertext byte-for-byte is the only cheap way to know it is right.
 */
@DisplayName("WebPushCrypto Tests")
class WebPushCryptoTest {

    // ---- RFC 8291 §5 test vector -------------------------------------------
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    /** User agent public key (p256dh). */
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    /** Auth secret. */
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    /** Application server (sender) ephemeral key pair from the RFC. */
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    /** The complete aes128gcm body the RFC says this must produce. */
    private static final String EXPECTED_BODY =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
                    + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT"
                    + "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    private static byte[] b64(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** Rebuilds the RFC's fixed sender key pair so the output is deterministic. */
    private static WebPushCrypto.EphemeralKeys rfcSenderKeys() throws Exception {
        BigInteger s = new BigInteger(1, b64(AS_PRIVATE));
        PrivateKey privateKey = KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(s, WebPushCrypto.p256Params()));
        var publicKey = WebPushCrypto.decodePoint(b64(AS_PUBLIC));
        return new WebPushCrypto.EphemeralKeys(
                new KeyPair(publicKey, privateKey), b64(AS_PUBLIC));
    }

    @Nested
    @DisplayName("RFC 8291 conformance")
    class RfcConformance {

        @Test
        @DisplayName("reproduces the RFC 8291 §5 ciphertext byte-for-byte")
        void matchesPublishedVector() throws Exception {
            byte[] body = WebPushCrypto.encrypt(
                    PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                    b64(UA_PUBLIC), b64(AUTH_SECRET), b64(SALT),
                    rfcSenderKeys(), 4096);

            assertThat(b64(body)).isEqualTo(EXPECTED_BODY);
        }

        @Test
        @DisplayName("emits the aes128gcm header the RFC 8188 framing requires")
        void emitsCorrectHeader() throws Exception {
            byte[] salt = b64(SALT);
            byte[] body = WebPushCrypto.encrypt(
                    PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                    b64(UA_PUBLIC), b64(AUTH_SECRET), salt, rfcSenderKeys(), 4096);

            // salt(16) ‖ rs(4, big-endian) ‖ idlen(1) ‖ as_public(65) ‖ ciphertext
            assertThat(Arrays.copyOfRange(body, 0, 16)).isEqualTo(salt);
            assertThat(Arrays.copyOfRange(body, 16, 20)).isEqualTo(new byte[]{0, 0, 0x10, 0});
            assertThat(body[20]).isEqualTo((byte) 65);
            assertThat(Arrays.copyOfRange(body, 21, 86)).isEqualTo(b64(AS_PUBLIC));
        }
    }

    @Nested
    @DisplayName("HKDF (RFC 5869)")
    class Hkdf {

        @Test
        @DisplayName("matches RFC 5869 test case 1")
        void matchesRfc5869Case1() throws Exception {
            byte[] ikm = new byte[22];
            Arrays.fill(ikm, (byte) 0x0b);
            byte[] salt = new byte[13];
            for (int i = 0; i < salt.length; i++) {
                salt[i] = (byte) i;
            }
            byte[] info = new byte[10];
            for (int i = 0; i < info.length; i++) {
                info[i] = (byte) (0xf0 + i);
            }

            byte[] okm = WebPushCrypto.hkdf(salt, ikm, info, 32);

            // First 32 bytes of the RFC's 42-byte expected OKM.
            assertThat(hex(okm)).isEqualTo(
                    "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf");
        }

        private String hex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }

    @Nested
    @DisplayName("Point encoding")
    class PointEncoding {

        @Test
        @DisplayName("round-trips an uncompressed P-256 point")
        void roundTripsPoint() throws Exception {
            var keys = WebPushCrypto.generateEphemeralKeys();

            assertThat(keys.publicPoint()).hasSize(65);
            assertThat(keys.publicPoint()[0]).isEqualTo((byte) 0x04);

            var decoded = WebPushCrypto.decodePoint(keys.publicPoint());
            assertThat(WebPushCrypto.encodePoint((ECPublicKey) decoded))
                    .isEqualTo(keys.publicPoint());
        }

        @Test
        @DisplayName("left-pads coordinates to exactly 32 bytes")
        void padsCoordinates() throws Exception {
            // BigInteger drops leading zeros and can prepend a sign byte; either
            // shifts the point and silently corrupts every payload.
            for (int i = 0; i < 50; i++) {
                var keys = WebPushCrypto.generateEphemeralKeys();
                assertThat(keys.publicPoint()).hasSize(65);
                assertThat(WebPushCrypto.encodePoint(
                        (ECPublicKey) WebPushCrypto.decodePoint(keys.publicPoint())))
                        .isEqualTo(keys.publicPoint());
            }
        }

        @Test
        @DisplayName("rejects a malformed point")
        void rejectsMalformedPoint() {
            assertThatThrownBy(() -> WebPushCrypto.decodePoint(new byte[64]))
                    .isInstanceOf(IllegalArgumentException.class);
            byte[] wrongPrefix = new byte[65];
            wrongPrefix[0] = 0x03;
            assertThatThrownBy(() -> WebPushCrypto.decodePoint(wrongPrefix))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("rejects a subscription key or auth secret of the wrong size")
        void rejectsBadInputs() throws Exception {
            var keys = rfcSenderKeys();
            byte[] plaintext = "hi".getBytes(StandardCharsets.UTF_8);
            byte[] salt = b64(SALT);

            assertThatThrownBy(() -> WebPushCrypto.encrypt(plaintext, new byte[64],
                    b64(AUTH_SECRET), salt, keys, 4096))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> WebPushCrypto.encrypt(plaintext, b64(UA_PUBLIC),
                    new byte[8], salt, keys, 4096))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> WebPushCrypto.encrypt(plaintext, b64(UA_PUBLIC),
                    b64(AUTH_SECRET), new byte[8], keys, 4096))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a different subscription produces a different ciphertext")
        void differentSubscriptionDiffersCiphertext() throws Exception {
            // The IKM binds both public keys, so a payload cannot be replayed at
            // another subscription.
            var other = WebPushCrypto.generateEphemeralKeys();
            byte[] a = WebPushCrypto.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                    b64(UA_PUBLIC), b64(AUTH_SECRET), b64(SALT), rfcSenderKeys(), 4096);
            byte[] b = WebPushCrypto.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                    other.publicPoint(), b64(AUTH_SECRET), b64(SALT), rfcSenderKeys(), 4096);

            assertThat(a).isNotEqualTo(b);
        }
    }
}
