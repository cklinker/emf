package io.kelta.runtime.module;

import java.time.Instant;

/**
 * A trust anchor for module JAR signatures, owned by one tenant.
 *
 * <p>A tenant may hold several {@linkplain #active() active} keys at once, and a JAR
 * signature is accepted when it verifies against any of them. That is what makes key
 * rotation non-breaking: a JAR is re-verified on every load, not only at install, so
 * replacing a tenant's only key would invalidate every module it has already installed.
 *
 * <p>{@link #algorithm()} is per key rather than per platform, so an Ed25519 key and an RSA
 * key can be trusted simultaneously and changing algorithm is the same rolling operation as
 * changing key.
 *
 * <p>This carries only the public half. The private key never reaches the platform.
 *
 * @param id             primary key
 * @param tenantId       owning tenant
 * @param label          operator-facing name, unique per tenant (e.g. {@code "2026-h2"})
 * @param algorithm      JCA signature algorithm, e.g. {@code Ed25519}, {@code SHA256withRSA}
 * @param publicKeyPem   X.509/SPKI public key in PEM
 * @param fingerprint    SHA-256 over the DER SPKI bytes, lowercase hex — names the key in
 *                       logs and on {@code tenant_module} without moving the PEM around
 * @param active         whether signatures from this key are currently accepted
 * @param retiredAt      when the key was deactivated, or {@code null} while active
 * @param createdAt      when the key was added
 * @param createdBy      user who added the key
 * @since 1.0.0
 */
public record ModuleSigningKey(
    String id,
    String tenantId,
    String label,
    String algorithm,
    String publicKeyPem,
    String fingerprint,
    boolean active,
    Instant retiredAt,
    Instant createdAt,
    String createdBy
) {
    /** Short form for log lines, where the full 64-char fingerprint is noise. */
    public String shortFingerprint() {
        if (fingerprint == null || fingerprint.length() <= 12) {
            return fingerprint;
        }
        return fingerprint.substring(0, 12);
    }
}
