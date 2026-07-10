package io.kelta.worker.service.telehealth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LiveKitTokenService")
class LiveKitTokenServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret";
    private final LiveKitTokenService service =
            new LiveKitTokenService("wss://livekit.test", "test-key", SECRET);

    @Test
    @DisplayName("mints an HS256 token with the room-scoped video grant")
    void mintClaims() throws Exception {
        Instant exp = Instant.now().plusSeconds(3600);
        LiveKitTokenService.MintedToken minted =
                service.mint("user-1", "Pat Doe", "t_t1_room", exp);

        SignedJWT jwt = SignedJWT.parse(minted.token());
        assertThat(jwt.verify(new com.nimbusds.jose.crypto.MACVerifier(
                SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("test-key");
        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.getStringClaim("name")).isEqualTo("Pat Doe");
        @SuppressWarnings("unchecked")
        Map<String, Object> video = (Map<String, Object>) claims.getClaim("video");
        assertThat(video).containsEntry("roomJoin", true)
                .containsEntry("room", "t_t1_room")
                .containsEntry("canPublish", true)
                .containsEntry("canSubscribe", true);
        assertThat(claims.getExpirationTime().toInstant())
                .isCloseTo(exp, org.assertj.core.api.Assertions.within(2, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("verifies a LiveKit-style webhook (signature + body digest) and rejects tampering")
    void webhookVerification() throws Exception {
        String body = "{\"event\":\"room_started\",\"id\":\"EV_1\",\"room\":{\"name\":\"t_t1_room\"}}";
        String digest = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("test-key")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("sha256", digest)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        String auth = jwt.serialize();

        assertThat(service.verifyWebhook(auth, body)).contains("test-key");
        // Body tampering breaks the digest.
        assertThat(service.verifyWebhook(auth, body + " ")).isEmpty();
        // Wrong secret breaks the signature.
        LiveKitTokenService other =
                new LiveKitTokenService("wss://x", "test-key", "another-secret-another-secret!!");
        assertThat(other.verifyWebhook(auth, body)).isEmpty();
        // Garbage inputs.
        assertThat(service.verifyWebhook(null, body)).isEmpty();
        assertThat(service.verifyWebhook("not-a-jwt", body)).isEmpty();
    }
}
