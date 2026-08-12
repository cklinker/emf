package io.kelta.worker.module;

import io.kelta.runtime.module.ModuleSigningKey;
import io.kelta.runtime.module.ModuleSigningKeyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Verifies that a runtime module JAR was signed by a publisher the tenant trusts, before the
 * JAR is installed and again before it is classloaded.
 *
 * <p>This is an authenticity gate, not an integrity one — the loader's SHA-256 checksum already
 * detects corruption. It matters because {@link SandboxedModuleClassLoader} allows {@code java.*}
 * wholesale: a module JAR runs arbitrary Java inside the worker JVM, with sockets, filesystem,
 * threads and {@code ProcessBuilder}.
 *
 * <h2>Trust anchors are per tenant</h2>
 *
 * <p>Anchors come from {@link ModuleSigningKeyStore}, scoped to the installing tenant, so a JAR
 * signed for tenant A is not loadable in tenant B. A tenant may trust several keys at once and a
 * signature is accepted when it verifies against any of them — that is what makes rotation
 * non-breaking, since a JAR is re-verified on every load and not only at install.
 *
 * <p>{@code kelta.modules.signing.public-key} remains supported as an <em>additional</em>
 * platform-wide anchor for operator-published modules. It is trusted for every tenant, which is
 * the property per-tenant keys exist to remove, so a warning is logged while it is set.
 *
 * <h2>When no anchor exists</h2>
 *
 * <p>With {@code kelta.modules.signing.required=false} (the default, for back-compat) a tenant
 * that has designated no key skips verification, and a warning records the unsigned posture.
 *
 * <p>With {@code required=true} that tenant cannot install a JAR at all. This is deliberately
 * fail-closed: retiring keys must make installs <em>impossible</em>, never <em>unverified</em>,
 * or dropping a tenant's last key becomes a way to turn the gate off.
 *
 * @since 1.0.0
 */
@Component
public class ModuleSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(ModuleSignatureVerifier.class);

    /** Label reported for the platform-wide key, which has no {@code tenant_module_signing_key} row. */
    public static final String PLATFORM_KEY_LABEL = "platform";

    private final String platformKeyPem;
    private final String platformAlgorithm;
    private final boolean required;
    private final ModuleSigningKeyStore keyStore;

    // Explicit, because the test/no-store constructor below makes this class multi-constructor:
    // Spring's single-constructor autowiring rule stops applying and it would look for a no-arg
    // constructor instead, failing the worker at startup.
    @Autowired
    public ModuleSignatureVerifier(
            @Value("${kelta.modules.signing.public-key:}") String platformKeyPem,
            @Value("${kelta.modules.signing.algorithm:Ed25519}") String platformAlgorithm,
            @Value("${kelta.modules.signing.required:false}") boolean required,
            @Nullable ModuleSigningKeyStore keyStore) {
        this.platformKeyPem = platformKeyPem == null ? "" : platformKeyPem.trim();
        this.platformAlgorithm = (platformAlgorithm == null || platformAlgorithm.isBlank())
                ? "Ed25519" : platformAlgorithm.trim();
        this.required = required;
        this.keyStore = keyStore;

        if (keyStore == null) {
            log.warn("Module signing key store unavailable — only the platform-wide key can be "
                    + "trusted, per-tenant keys are ignored");
        }
        if (!this.platformKeyPem.isBlank()) {
            log.warn("A platform-wide module signing key is configured (algorithm={}). It is "
                    + "trusted for EVERY tenant; prefer per-tenant keys and unset "
                    + "kelta.modules.signing.public-key once they are in place", this.platformAlgorithm);
        }
        if (required) {
            log.info("Module JAR signing REQUIRED — a tenant with no trusted key cannot install a JAR");
        } else {
            log.warn("Module JAR signing NOT required — a tenant with no trusted key installs "
                    + "unverified JARs; set kelta.modules.signing.required=true to enforce");
        }
    }

    /**
     * Platform-key-only verifier, for tests and for deployments with no key store.
     */
    public ModuleSignatureVerifier(String platformKeyPem, String platformAlgorithm) {
        this(platformKeyPem, platformAlgorithm, false, null);
    }

    /** Whether signing is mandatory platform-wide (a tenant with no key cannot install). */
    public boolean isRequired() {
        return required;
    }

    /** Fingerprint of the platform-wide key, or {@code null} when none is configured. */
    @Nullable
    public String platformKeyFingerprint() {
        if (platformKeyPem.isBlank()) {
            return null;
        }
        try {
            return fingerprint(platformKeyPem, platformAlgorithm);
        } catch (Exception e) {
            log.error("Configured platform module signing key is unusable: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Whether any trust anchor exists for a tenant, so signatures will actually be checked.
     *
     * @param tenantId the installing tenant
     */
    public boolean isEnabledFor(String tenantId) {
        return !trustedKeys(tenantId).isEmpty();
    }

    /**
     * Verifies a detached signature over the JAR bytes against the tenant's trust anchors.
     *
     * @param tenantId        the tenant installing or loading the module
     * @param jarBytes        the raw module JAR bytes
     * @param signatureBase64 the base64-encoded detached signature
     * @return the fingerprint of the key that verified the signature, or {@code null} when the
     *         tenant has no anchor and signing is not required (nothing was checked)
     * @throws ModuleSignatureException when signing is required but the tenant has no anchor, or
     *         the signature is missing, malformed, or verifies against none of the anchors
     */
    @Nullable
    public String verify(String tenantId, byte[] jarBytes, String signatureBase64) {
        List<TrustAnchor> anchors = trustedKeys(tenantId);

        if (anchors.isEmpty()) {
            if (required) {
                throw new ModuleSignatureException(
                        "Module JAR signing is required but tenant " + tenantId
                        + " has no active module signing key — add one before installing a module");
            }
            return null;
        }

        if (signatureBase64 == null || signatureBase64.isBlank()) {
            throw new ModuleSignatureException(
                    "Module JAR signature is required but was not provided");
        }
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signatureBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new ModuleSignatureException("Module JAR signature is not valid base64", e);
        }

        int unusable = 0;
        for (TrustAnchor anchor : anchors) {
            try {
                Signature verifier = Signature.getInstance(anchor.algorithm());
                verifier.initVerify(parsePublicKey(anchor.publicKeyPem(), anchor.algorithm()));
                verifier.update(jarBytes);
                if (verifier.verify(signatureBytes)) {
                    return anchor.fingerprint();
                }
            } catch (Exception e) {
                // One malformed or unsupported key must not brick every install for the tenant;
                // the remaining anchors still get their turn. Counted so the failure message can
                // distinguish "signed by the wrong key" from "no key here was usable".
                unusable++;
                log.warn("Module signing key '{}' for tenant {} is unusable, skipping: {}",
                        anchor.label(), tenantId, e.getMessage());
            }
        }

        throw new ModuleSignatureException(
                "Module JAR signature does not match any of the " + anchors.size()
                + " trusted signing key(s) for tenant " + tenantId
                + (unusable > 0 ? " (" + unusable + " of them unusable — see warnings)" : ""));
    }

    /**
     * The anchors trusted for a tenant: its active keys, plus the platform-wide key when one is
     * configured.
     */
    private List<TrustAnchor> trustedKeys(String tenantId) {
        List<TrustAnchor> anchors = new ArrayList<>();
        if (keyStore != null && tenantId != null && !tenantId.isBlank()) {
            for (ModuleSigningKey key : keyStore.findActiveByTenant(tenantId)) {
                anchors.add(new TrustAnchor(
                        key.label(), key.algorithm(), key.publicKeyPem(), key.fingerprint()));
            }
        }
        if (!platformKeyPem.isBlank()) {
            String fingerprint = platformKeyFingerprint();
            if (fingerprint != null) {
                anchors.add(new TrustAnchor(
                        PLATFORM_KEY_LABEL, platformAlgorithm, platformKeyPem, fingerprint));
            }
        }
        return anchors;
    }

    /**
     * SHA-256 over the DER SPKI bytes of a public key, lowercase hex.
     *
     * <p>Taken over the decoded DER rather than the PEM text so it is stable across
     * reformatting, line-wrapping and trailing-newline differences.
     *
     * @param publicKeyPem X.509/SPKI public key in PEM
     * @param algorithm    JCA signature algorithm the key is for
     * @return the fingerprint
     * @throws IllegalArgumentException when the PEM does not parse as a key for this algorithm
     */
    public static String fingerprint(String publicKeyPem, String algorithm) {
        byte[] der = der(publicKeyPem);
        // Parse before fingerprinting: a fingerprint over bytes that are not a usable key would
        // name something that can never verify anything.
        parsePublicKey(publicKeyPem, algorithm);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not fingerprint public key: " + e.getMessage(), e);
        }
    }

    /**
     * Parses an X.509/SPKI PEM public key for the given signature algorithm.
     *
     * @throws IllegalArgumentException when the PEM is not a usable key for this algorithm
     */
    public static PublicKey parsePublicKey(String publicKeyPem, String algorithm) {
        try {
            return KeyFactory.getInstance(keyAlgorithm(algorithm))
                    .generatePublic(new X509EncodedKeySpec(der(publicKeyPem)));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Not a valid " + algorithm + " public key: " + e.getMessage(), e);
        }
    }

    private static byte[] der(String publicKeyPem) {
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalArgumentException("Public key PEM is empty");
        }
        String base64 = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Public key PEM body is not valid base64", e);
        }
    }

    /** Derives the KeyFactory algorithm from a signature algorithm name. */
    private static String keyAlgorithm(String algorithm) {
        String a = algorithm == null ? "" : algorithm.toUpperCase();
        if (a.contains("RSA")) {
            return "RSA";
        }
        if (a.contains("ECDSA") || a.equals("EC")) {
            return "EC";
        }
        return "Ed25519";
    }

    /** One candidate key, from either the tenant's key set or the platform config. */
    private record TrustAnchor(String label, String algorithm, String publicKeyPem, String fingerprint) {
    }
}
