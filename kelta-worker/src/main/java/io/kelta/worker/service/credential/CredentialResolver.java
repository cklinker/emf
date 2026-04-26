package io.kelta.worker.service.credential;

import io.kelta.runtime.credential.ResolvedCredential;

/**
 * Resolves a credential reference (id or name) into a {@link ResolvedCredential}
 * containing decrypted secret material. Implementations must:
 * <ul>
 *   <li>Enforce tenant scoping via {@code TenantContext} so RLS filters apply.</li>
 *   <li>Decrypt the stored blob through {@link io.kelta.crypto.EncryptionService}.</li>
 *   <li>Cache results per-pod with a short TTL.</li>
 *   <li>Invalidate cache entries when the credential changes (driven by
 *       {@code kelta.config.credential.changed.<id>} NATS events).</li>
 *   <li>Write an audit row to {@code setup_audit_trail} for every successful
 *       resolution — without ever logging the decrypted material.</li>
 * </ul>
 */
public interface CredentialResolver {

    /**
     * Resolves a credential by ID or name, returning the decrypted material.
     *
     * @param tenantId  the owning tenant; resolution is scoped to this tenant only
     * @param reference either the credential UUID or its tenant-unique {@code name}
     * @param ctx       audit context captured in the {@code setup_audit_trail} row
     * @throws CredentialNotFoundException  when no matching credential exists
     * @throws CredentialDisabledException  when the credential exists but {@code active=false}
     * @throws CredentialDecryptException   when decryption or JSON parsing fails
     */
    ResolvedCredential resolve(String tenantId, String reference, ResolutionContext ctx);

    /**
     * Drops every cached entry for {@code credentialId} across all tenants
     * (driven by NATS events; pods see invalidations without round-trips).
     */
    void invalidate(String credentialId);

    /** Drops the entire cache. Intended for tests and emergency operator action. */
    void invalidateAll();
}
