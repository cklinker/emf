package io.kelta.worker.service.telehealth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Signs and verifies stateless visit-link tokens (telehealth slice 4,
 * campaign {@code TrackingTokenService} idiom). A token binds
 * {@code (tenantId, appointmentId, portalUserId, exp)}; the endpoint
 * re-validates against the live appointment row (status, window) before
 * acting, so the token alone never grants anything to a cancelled or moved
 * appointment. Format: {@code base64url(payload) + "." + base64url(mac)} with
 * payload {@code tenantId|appointmentId|userId|expEpochSeconds}.
 *
 * <p>Emailed visit links can be clicked many times (confirmation + reminder
 * both carry one), so tokens are multi-use until {@code exp}; each click mints
 * a fresh SINGLE-USE 15-minute portal login token. Possession of the inbox is
 * the factor — the same trust model as portal magic links.
 */
@Service
public class VisitTokenService {

    private static final Logger log = LoggerFactory.getLogger(VisitTokenService.class);
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    public record VisitClaim(String tenantId, String appointmentId, String portalUserId, Instant exp) {
        public boolean expired(Instant now) {
            return now.isAfter(exp);
        }
    }

    private final byte[] secret;

    public VisitTokenService(@Value("${kelta.telehealth.visit-secret:}") String secret) {
        String effective = secret;
        if (effective == null || effective.isBlank()) {
            effective = "kelta-dev-visit-secret";
            log.warn("kelta.telehealth.visit-secret is not set — using the DEV default. "
                    + "Set KELTA_TELEHEALTH_VISIT_SECRET in every non-dev environment.");
        }
        this.secret = effective.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String tenantId, String appointmentId, String portalUserId, Instant exp) {
        String payload = tenantId + "|" + appointmentId + "|" + portalUserId + "|" + exp.getEpochSecond();
        return B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + B64.encodeToString(hmac(payload));
    }

    /** Verifies signature + expiry; the caller still checks the appointment row. */
    public Optional<VisitClaim> verify(String token, Instant now) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        try {
            String payload = new String(B64D.decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            byte[] mac = B64D.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(mac, hmac(payload))) {
                return Optional.empty();
            }
            String[] parts = payload.split("\\|");
            if (parts.length != 4) {
                return Optional.empty();
            }
            VisitClaim claim = new VisitClaim(parts[0], parts[1], parts[2],
                    Instant.ofEpochSecond(Long.parseLong(parts[3])));
            return claim.expired(now) ? Optional.empty() : Optional.of(claim);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
