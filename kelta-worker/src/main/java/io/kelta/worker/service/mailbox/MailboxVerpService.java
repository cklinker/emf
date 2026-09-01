package io.kelta.worker.service.mailbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mints and verifies the thread token carried in a reply's {@code Reply-To}.
 *
 * <p>Format: {@code <local>+t<threadId>.<hmac8>@<verpDomain>}, e.g.
 * {@code support+tab12cd34-....9f3a1b2c@spotopened.com}. When the customer replies, their client
 * addresses the token back to us and the thread is identified without depending on
 * {@code References} surviving — which plenty of clients strip.
 *
 * <p><b>The HMAC is why this is not a security hole.</b> A bare {@code +t<threadId>} address would
 * let anyone who guesses or scrapes a thread id post into that conversation, inheriting whatever
 * trust the thread already carries. Signing means a token can only have come from us.
 *
 * <p>Eight hex characters is a deliberate truncation. The token has to fit in an email local part
 * and stay readable in a bounce message, and 32 bits is ample here: forging one buys an attacker a
 * message in a support thread they already knew the id of, and every reply is still shown to a
 * human against an unverified-sender badge. It is not protecting a login.
 *
 * @since 1.0.0
 */
@Service
public class MailboxVerpService {

    private static final Logger log = LoggerFactory.getLogger(MailboxVerpService.class);

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int TAG_HEX_LENGTH = 8;

    /** Matches the token in any recipient address, case-insensitively. */
    private static final Pattern TOKEN = Pattern.compile(
            "\\+t([0-9a-fA-F-]{36})\\.([0-9a-fA-F]{" + TAG_HEX_LENGTH + "})@",
            Pattern.CASE_INSENSITIVE);

    private final byte[] secret;

    /**
     * @throws IllegalStateException when no secret is configured.
     *     Deliberately fatal, following {@code VisitTokenService}: a fallback default would mean
     *     every deployment signs with a key published in this repository, and the warning would
     *     scroll past on every boot while thread tokens stayed forgeable.
     */
    public MailboxVerpService(@Value("${kelta.mailbox.verp-secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "kelta.mailbox.verp-secret is not set. Reply-To thread tokens are HMAC-signed "
                            + "so that only we can address a message into a conversation, and there "
                            + "is no safe default. Set KELTA_MAILBOX_VERP_SECRET to a random "
                            + "high-entropy value (openssl rand -base64 48) in every environment.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Builds the {@code Reply-To} address for a thread.
     *
     * @param localPart the mailbox address's local part, e.g. {@code support}
     * @param verpDomain the domain configured on the mailbox; when blank there is no usable
     *                   subaddressed domain and the caller must fall back to a plain Reply-To
     * @return the address, or empty when VERP is not configured for this mailbox
     */
    public Optional<String> replyToAddress(String localPart, String verpDomain, String threadId) {
        if (verpDomain == null || verpDomain.isBlank() || localPart == null || localPart.isBlank()) {
            return Optional.empty();
        }
        if (threadId == null || threadId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(localPart + "+t" + threadId + "." + tag(threadId) + "@" + verpDomain);
    }

    /**
     * Extracts a verified thread id from a recipient list.
     *
     * <p>Returns empty when no token is present <b>or</b> when one is present but its signature
     * does not verify. A failed signature is treated exactly like no token at all: the message
     * falls through to the ordinary threading rules rather than being rejected, because a customer
     * whose client mangled the address still deserves to have their mail delivered somewhere.
     *
     * @param recipients any address-bearing header value; {@code To} and {@code Delivered-To} both
     *                   commonly carry it
     */
    public Optional<String> threadIdFrom(String recipients) {
        if (recipients == null || recipients.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = TOKEN.matcher(recipients);
        while (matcher.find()) {
            String threadId = matcher.group(1).toLowerCase(Locale.ROOT);
            String presented = matcher.group(2).toLowerCase(Locale.ROOT);
            if (MessageDigest.isEqual(
                    tag(threadId).getBytes(StandardCharsets.UTF_8),
                    presented.getBytes(StandardCharsets.UTF_8))) {
                return Optional.of(threadId);
            }
            log.warn("Discarding a VERP thread token whose signature does not verify");
        }
        return Optional.empty();
    }

    /** Truncated HMAC over the thread id. Constant-time compared on the way back in. */
    private String tag(String threadId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            String hex = HexFormat.of()
                    .formatHex(mac.doFinal(threadId.toLowerCase(Locale.ROOT)
                            .getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, TAG_HEX_LENGTH);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign a VERP thread token", e);
        }
    }
}
