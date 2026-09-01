package io.kelta.worker.service.mailbox;

import io.kelta.crypto.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Mints and rotates the two pieces of material an inbound mailbox endpoint needs.
 *
 * <p>They are deliberately different kinds of thing:
 *
 * <ul>
 *   <li>The <b>webhook key</b> is an <i>identifier</i>. It appears in provider consoles,
 *       access logs and support tickets, so it is never treated as a secret. It is random
 *       and long only so the endpoint cannot be enumerated — knowing it gets a caller as
 *       far as "which mailbox", never as far as "authenticated".</li>
 *   <li>The <b>HMAC secret</b> is the actual authenticator, and lives in the credential
 *       vault encrypted at rest — never in a {@code mailbox} column. The row holds only a
 *       reference and a four-character hint, so dumping the table reveals nothing usable.</li>
 * </ul>
 *
 * <p>Rotation keeps two secrets live for an overlap window. A provider cannot change its
 * signing key at the same instant we change ours, so without an overlap every delivery
 * in flight during the switch is rejected — and a rejected inbound message is a lost
 * customer email, not a retryable blip.
 *
 * @since 1.0.0
 */
@Service
// Gated on the SAME property that creates EncryptionService and CredentialResolverImpl
// (both @ConditionalOnProperty "kelta.encryption.key"). Without the gate this component is a
// required dependency on beans that may not exist, and the whole worker fails to start in any
// environment without an encryption key. @ConditionalOnBean is deliberately not used here: it
// is order-sensitive during component scan and can drop the bean even when the dependency is
// later registered — see TenantEmailSettingsController for the same reasoning.
@ConditionalOnProperty(name = "kelta.encryption.key")
public class MailboxSecretService {

    private static final Logger log = LoggerFactory.getLogger(MailboxSecretService.class);

    /** 256 bits. Enough that the endpoint cannot be found by guessing. */
    private static final int WEBHOOK_KEY_BYTES = 32;
    /** 256 bits, matching the HMAC-SHA256 block the signature scheme uses. */
    private static final int SECRET_BYTES = 32;

    private static final String CREDENTIAL_TYPE = "custom";

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final int overlapMinutes;

    private final SecureRandom random = new SecureRandom();

    public MailboxSecretService(JdbcTemplate jdbcTemplate,
                                EncryptionService encryptionService,
                                ObjectMapper objectMapper,
                                @Value("${kelta.mailbox.secret-rotation-overlap-minutes:1440}")
                                int overlapMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.overlapMinutes = overlapMinutes;
    }

    public int overlapMinutes() {
        return overlapMinutes;
    }

    /** A fresh URL-safe webhook key. Unpadded so it never needs escaping in a path segment. */
    public String generateWebhookKey() {
        byte[] buf = new byte[WEBHOOK_KEY_BYTES];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * The plaintext secret and the hint stored beside its reference.
     *
     * <p>The plaintext exists only in the response to the request that created it. It is
     * never persisted in the clear, never logged, and cannot be read back — a caller who
     * loses it must rotate, which is the correct outcome, because "let me look up that
     * secret again" is the capability worth not having.
     */
    public record MintedSecret(String credentialId, String plaintext, String hint) {}

    /**
     * Creates a vault credential holding a new random secret for this mailbox.
     *
     * <p>Must be called with a tenant bound: the {@code credential} row is tenant-scoped
     * and RLS-protected like every other.
     */
    public MintedSecret mint(String tenantId, String mailboxId, String mailboxName, String actor) {
        byte[] buf = new byte[SECRET_BYTES];
        random.nextBytes(buf);
        String plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        String credentialId = UUID.randomUUID().toString();
        // Named per rotation, not per mailbox: credential names are unique per tenant, and
        // reusing one name would collide with the previous secret that is still live during
        // the overlap window.
        String name = "mailbox-inbound-" + mailboxId + "-" + credentialId.substring(0, 8);

        String dataEnc = encryptionService.encrypt(toJson(Map.of("secret", plaintext)));
        String metadata = toJson(Map.of(
                "purpose", "mailbox-inbound-hmac",
                "mailboxId", mailboxId,
                "algorithm", "HmacSHA256"));

        jdbcTemplate.update("""
                INSERT INTO credential
                    (id, tenant_id, name, display_name, description, type, data_enc, metadata,
                     active, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, true, ?, ?, NOW(), NOW())
                """,
                credentialId, tenantId, name,
                "Mailbox inbound HMAC (" + mailboxName + ")",
                "Signing secret for inbound mail delivered to the " + mailboxName + " mailbox.",
                CREDENTIAL_TYPE, dataEnc, metadata,
                actor == null ? "" : actor, actor == null ? "" : actor);

        log.info("Minted inbound secret for mailbox {} in tenant {} (credentialId={})",
                mailboxId, tenantId, credentialId);

        return new MintedSecret(credentialId, plaintext, hintOf(plaintext));
    }

    /**
     * Deactivates a credential once its overlap window has passed.
     *
     * <p>Deactivated rather than deleted so the audit trail still explains what verified a
     * delivery last week.
     */
    public void deactivate(String tenantId, String credentialId) {
        if (credentialId == null || credentialId.isBlank()) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE credential SET active = false, updated_at = NOW() WHERE id = ? AND tenant_id = ?",
                credentialId, tenantId);
    }

    /**
     * The last four characters, for telling two secrets apart in a UI.
     *
     * <p>Four characters of a 256-bit random value narrows nothing — the point is
     * recognition, not verification.
     */
    public String hintOf(String plaintext) {
        if (plaintext == null || plaintext.length() < 4) {
            return "";
        }
        return "…" + plaintext.substring(plaintext.length() - 4);
    }

    private String toJson(Map<String, Object> m) {
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            // Never surface the map: it holds the plaintext secret.
            throw new IllegalStateException("Failed to serialise mailbox credential payload", e);
        }
    }
}
