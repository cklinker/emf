package io.kelta.worker.service.push;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("ApnsPushProvider Tests")
class ApnsPushProviderTest {

    private static final String TEAM = "TEAM123456";
    private static final String KEY_ID = "KEY7890AB";
    private static final String BUNDLE = "io.kelta.app";
    private static final String HOST = "https://api.push.apple.com";

    private static PrivateKey ecPrivateKey;
    private static PublicKey ecPublicKey;
    private static String ecPrivateKeyPem;

    private ObjectMapper objectMapper;
    private FakeSender sender;
    private ApnsPushProvider provider;

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();
        ecPrivateKey = kp.getPrivate();
        ecPublicKey = kp.getPublic();
        ecPrivateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(ecPrivateKey.getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sender = new FakeSender();
        provider = new ApnsPushProvider(TEAM, KEY_ID, BUNDLE, HOST, ecPrivateKey, sender, objectMapper);
    }

    private static PushMessage iosMessage() {
        return new PushMessage("device-token-abc", "ios", "Hello", "World", Map.of("k", "v"));
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("posts to the APNs device URL with token-auth + topic headers")
        void sendsWithCorrectUrlAndHeaders() {
            sender.response = new ApnsPushProvider.ApnsResponse(200, "");

            provider.send(iosMessage());

            assertThat(sender.called).isTrue();
            assertThat(sender.url).isEqualTo(HOST + "/3/device/device-token-abc");
            assertThat(sender.headers)
                    .containsEntry("apns-topic", BUNDLE)
                    .containsEntry("apns-push-type", "alert")
                    .containsEntry("apns-priority", "10");
            assertThat(sender.headers.get("authorization")).startsWith("bearer ");
        }

        @Test
        @DisplayName("builds an aps.alert payload with custom data at the top level")
        void buildsApsPayload() {
            provider.send(iosMessage());

            JsonNode body = objectMapper.readTree(sender.body);
            assertThat(body.get("aps").get("alert").get("title").asText()).isEqualTo("Hello");
            assertThat(body.get("aps").get("alert").get("body").asText()).isEqualTo("World");
            // custom data sits alongside `aps`, never inside it
            assertThat(body.get("k").asText()).isEqualTo("v");
            assertThat(body.get("aps").has("k")).isFalse();
        }

        @Test
        @DisplayName("400 BadDeviceToken → invalid-token PushDeliveryException")
        void badDeviceTokenIsInvalid() {
            sender.response = new ApnsPushProvider.ApnsResponse(400, "{\"reason\":\"BadDeviceToken\"}");

            PushDeliveryException ex = catchThrowableOfType(
                    PushDeliveryException.class, () -> provider.send(iosMessage()));

            assertThat(ex).isNotNull();
            assertThat(ex.isInvalidToken()).isTrue();
        }

        @Test
        @DisplayName("410 Unregistered → invalid-token PushDeliveryException")
        void unregisteredIsInvalid() {
            sender.response = new ApnsPushProvider.ApnsResponse(410, "{\"reason\":\"Unregistered\"}");

            PushDeliveryException ex = catchThrowableOfType(
                    PushDeliveryException.class, () -> provider.send(iosMessage()));

            assertThat(ex.isInvalidToken()).isTrue();
        }

        @Test
        @DisplayName("5xx → retryable (not invalid-token) PushDeliveryException")
        void serverErrorIsNotInvalid() {
            sender.response = new ApnsPushProvider.ApnsResponse(500, "{\"reason\":\"InternalServerError\"}");

            PushDeliveryException ex = catchThrowableOfType(
                    PushDeliveryException.class, () -> provider.send(iosMessage()));

            assertThat(ex.isInvalidToken()).isFalse();
        }

        @Test
        @DisplayName("rejects non-iOS platforms without hitting the network")
        void rejectsNonIos() {
            PushMessage android = new PushMessage("t", "android", "a", "b", Map.of());

            assertThatThrownBy(() -> provider.send(android))
                    .isInstanceOf(PushDeliveryException.class)
                    .hasMessageContaining("only supports iOS");
            assertThat(sender.called).isFalse();
        }

        @Test
        @DisplayName("fails fast when no auth key is configured")
        void failsWithoutAuthKey() {
            ApnsPushProvider noKey = new ApnsPushProvider(TEAM, KEY_ID, BUNDLE, HOST, null, sender, objectMapper);

            assertThatThrownBy(() -> noKey.send(iosMessage()))
                    .isInstanceOf(PushDeliveryException.class)
                    .hasMessageContaining("No APNs auth key");
            assertThat(sender.called).isFalse();
        }
    }

    @Nested
    @DisplayName("provider JWT (ES256)")
    class Jwt {

        @Test
        @DisplayName("has an ES256 header + team iss claim and a signature that verifies")
        void jwtStructureAndSignature() throws Exception {
            provider.send(iosMessage());

            String jwt = sender.headers.get("authorization").substring("bearer ".length());
            String[] parts = jwt.split("\\.");
            assertThat(parts).hasSize(3);

            JsonNode header = objectMapper.readTree(base64UrlDecode(parts[0]));
            JsonNode payload = objectMapper.readTree(base64UrlDecode(parts[1]));
            assertThat(header.get("alg").asText()).isEqualTo("ES256");
            assertThat(header.get("kid").asText()).isEqualTo(KEY_ID);
            assertThat(payload.get("iss").asText()).isEqualTo(TEAM);
            assertThat(payload.get("iat").asLong()).isGreaterThan(0);

            // The raw R||S signature must verify against the public key once re-encoded to DER.
            byte[] raw = Base64.getUrlDecoder().decode(parts[2]);
            byte[] der = joseToDer(raw);
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(ecPublicKey);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            assertThat(verifier.verify(der)).isTrue();
        }
    }

    @Nested
    @DisplayName("key + signature helpers")
    class Helpers {

        @Test
        @DisplayName("parseEcPrivateKey round-trips a PKCS#8 PEM")
        void parsesPem() {
            PrivateKey parsed = ApnsPushProvider.parseEcPrivateKey(ecPrivateKeyPem);
            assertThat(parsed).isNotNull();
            assertThat(parsed.getAlgorithm()).isEqualTo("EC");
        }

        @Test
        @DisplayName("parseEcPrivateKey rejects garbage")
        void rejectsBadPem() {
            assertThatThrownBy(() -> ApnsPushProvider.parseEcPrivateKey("not a key"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("derToJoseSignature emits fixed 64-byte output")
        void derToJoseFixedWidth() throws Exception {
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initSign(ecPrivateKey);
            sig.update("payload".getBytes(StandardCharsets.UTF_8));
            byte[] raw = ApnsPushProvider.derToJoseSignature(sig.sign(), 64);
            assertThat(raw).hasSize(64);
        }
    }

    // --- test helpers ---

    private static String base64UrlDecode(String s) {
        return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
    }

    /** Re-encode a JOSE raw R||S signature back to DER so the JDK verifier can check it. */
    private static byte[] joseToDer(byte[] raw) {
        int half = raw.length / 2;
        byte[] r = trimAndSign(java.util.Arrays.copyOfRange(raw, 0, half));
        byte[] s = trimAndSign(java.util.Arrays.copyOfRange(raw, half, raw.length));
        int len = 2 + r.length + 2 + s.length;
        byte[] der = new byte[2 + len];
        int i = 0;
        der[i++] = 0x30;
        der[i++] = (byte) len;
        der[i++] = 0x02;
        der[i++] = (byte) r.length;
        System.arraycopy(r, 0, der, i, r.length);
        i += r.length;
        der[i++] = 0x02;
        der[i++] = (byte) s.length;
        System.arraycopy(s, 0, der, i, s.length);
        return der;
    }

    /** Strip leading zeros and prepend 0x00 if the high bit is set (DER INTEGER sign rule). */
    private static byte[] trimAndSign(byte[] in) {
        int start = 0;
        while (start < in.length - 1 && in[start] == 0) {
            start++;
        }
        byte[] trimmed = java.util.Arrays.copyOfRange(in, start, in.length);
        if ((trimmed[0] & 0x80) != 0) {
            byte[] withSign = new byte[trimmed.length + 1];
            System.arraycopy(trimmed, 0, withSign, 1, trimmed.length);
            return withSign;
        }
        return trimmed;
    }

    private static final class FakeSender implements ApnsPushProvider.ApnsHttpSender {
        boolean called;
        String url;
        Map<String, String> headers;
        String body;
        ApnsPushProvider.ApnsResponse response = new ApnsPushProvider.ApnsResponse(200, "");

        @Override
        public ApnsPushProvider.ApnsResponse send(String url, Map<String, String> headers, String body) {
            this.called = true;
            this.url = url;
            this.headers = headers;
            this.body = body;
            return response;
        }
    }
}
