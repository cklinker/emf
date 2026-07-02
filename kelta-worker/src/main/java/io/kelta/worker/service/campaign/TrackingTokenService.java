package io.kelta.worker.service.campaign;

import io.kelta.worker.config.CampaignProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * Signs and verifies opaque tracking tokens used in campaign open-pixel, click-redirect,
 * and unsubscribe links.
 *
 * <p>These links are public (unauthenticated) endpoints, so the token is the only thing
 * standing between an attacker and forged unsubscribes / poisoned open-and-click stats.
 * Each token is {@code base64url(recipientId) + "." + base64url(HMAC-SHA256(secret, recipientId))}.
 * Verification recomputes the MAC and compares in constant time. The recipient id alone then
 * resolves the tenant, campaign, and email from {@code email_campaign_recipient} — nothing
 * sensitive is encoded in the URL.
 */
@Service
public class TrackingTokenService {

    private static final Logger log = LoggerFactory.getLogger(TrackingTokenService.class);
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;

    public TrackingTokenService(CampaignProperties properties) {
        this.secret = properties.trackingSecret().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Signs a recipient id into a self-verifying tracking token.
     *
     * @param recipientId the {@code email_campaign_recipient} row id
     * @return an opaque URL-safe token
     */
    public String sign(String recipientId) {
        String payload = B64.encodeToString(recipientId.getBytes(StandardCharsets.UTF_8));
        String mac = B64.encodeToString(hmac(recipientId));
        return payload + "." + mac;
    }

    /**
     * Verifies a token and extracts the recipient id.
     *
     * @param token the token from a tracking link
     * @return the recipient id if the signature is valid, else empty
     */
    public Optional<String> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        try {
            String recipientId = new String(B64D.decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            byte[] presented = B64D.decode(token.substring(dot + 1));
            byte[] expected = hmac(recipientId);
            if (!MessageDigest.isEqual(expected, presented)) {
                return Optional.empty();
            }
            return Optional.of(recipientId);
        } catch (IllegalArgumentException e) {
            // Malformed base64 — treat as an invalid token, not a server error.
            log.debug("Rejected malformed tracking token");
            return Optional.empty();
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute tracking HMAC", e);
        }
    }
}
