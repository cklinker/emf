package io.kelta.worker.service.mailbox.inbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * A provider-neutral adapter: the caller POSTs raw RFC 5322 bytes and signs them.
 *
 * <p>Signature header, matching the shape used by most webhook providers:
 *
 * <pre>X-Kelta-Signature: t=&lt;unix-seconds&gt;,v1=&lt;hex hmac-sha256&gt;</pre>
 *
 * where the signed value is {@code t + "." + rawBody}. Including the timestamp <i>inside</i> the
 * signed value is what makes it more than decoration: signing the body alone would let anyone who
 * captured one valid request replay it forever, since they could restate any timestamp they liked.
 *
 * @since 1.0.0
 */
@Component
public class GenericHmacInboundAdapter implements InboundMailAdapter {

    private static final Logger log = LoggerFactory.getLogger(GenericHmacInboundAdapter.class);

    public static final String PROVIDER = "GENERIC_HMAC";
    public static final String SIGNATURE_HEADER = "X-Kelta-Signature";

    /** How far a request's timestamp may be from ours. Covers clock skew, not much more. */
    private static final long MAX_SKEW_SECONDS = 300;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Override
    public String providerId() {
        return PROVIDER;
    }

    @Override
    public boolean verify(InboundRequest request, MailboxRef mailbox, InboundSecrets secrets) {
        String header = request.header(SIGNATURE_HEADER);
        if (header == null || header.isBlank()) {
            log.warn("Rejecting inbound mail for mailbox {}: no {} header", mailbox.id(), SIGNATURE_HEADER);
            return false;
        }

        String timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("t".equals(kv[0])) {
                timestamp = kv[1];
            } else if ("v1".equals(kv[0])) {
                signature = kv[1];
            }
        }
        if (timestamp == null || signature == null) {
            log.warn("Rejecting inbound mail for mailbox {}: malformed signature header", mailbox.id());
            return false;
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        long skew = Math.abs(Instant.now().getEpochSecond() - ts);
        if (skew > MAX_SKEW_SECONDS) {
            log.warn("Rejecting inbound mail for mailbox {}: timestamp is {}s out of date",
                    mailbox.id(), skew);
            return false;
        }

        byte[] signed = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[signed.length + request.rawBody().length];
        System.arraycopy(signed, 0, payload, 0, signed.length);
        System.arraycopy(request.rawBody(), 0, payload, signed.length, request.rawBody().length);

        // The previous secret is accepted while its overlap window is open. Without that, every
        // delivery in flight during a rotation is rejected — and a rejected inbound message is a
        // lost customer email, not a retryable blip.
        return matches(payload, signature, secrets.current())
                || matches(payload, signature, secrets.previous());
    }

    private boolean matches(byte[] payload, String presented, String secret) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload));
            // Constant-time: a length-or-content-dependent comparison leaks the signature one
            // byte at a time to anyone willing to measure.
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    presented.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<String> providerEventId(InboundRequest request) {
        // No provider id: the pipeline falls back to hashing the body, which is exactly right
        // here because the body IS the message.
        return Optional.empty();
    }

    @Override
    public InboundEnvelope extract(InboundRequest request, MailboxRef mailbox) {
        // No verdicts. A caller posting raw MIME has no authenticated position to report SPF or
        // DKIM from, and accepting self-reported verdicts would let a sender declare itself
        // DMARC-passing — which auto-reply treats as permission to answer without a human.
        return InboundEnvelope.ofRaw(request.rawBody(), NormalizedInboundMail.Verdicts.unknown());
    }
}
