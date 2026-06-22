package io.kelta.worker.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifies that a runtime module JAR was signed by the trusted publisher before it
 * is installed (Rec 9 security). The existing loader records a SHA-256 checksum
 * (integrity — detects corruption) but not a signature (authenticity — proves who
 * produced the JAR); without this, any JAR with a valid checksum could be loaded
 * into the {@link SandboxedModuleClassLoader}.
 *
 * <p>Verification is enabled only when a trusted public key is configured
 * ({@code kelta.modules.signing.public-key}, an X.509/SPKI key in PEM). When it is
 * set, every JAR install must carry a detached base64 signature over the raw JAR
 * bytes that verifies against the key, or the install is rejected. When it is
 * unset, verification is a no-op (back-compat for environments that haven't
 * adopted signing yet) — a warning is logged so the unsigned posture is visible.
 *
 * <p>Default algorithm is {@code Ed25519}; {@code SHA256withRSA} and ECDSA are also
 * supported via {@code kelta.modules.signing.algorithm}.
 */
@Component
public class ModuleSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(ModuleSignatureVerifier.class);

    private final String publicKeyPem;
    private final String algorithm;

    public ModuleSignatureVerifier(
            @Value("${kelta.modules.signing.public-key:}") String publicKeyPem,
            @Value("${kelta.modules.signing.algorithm:Ed25519}") String algorithm) {
        this.publicKeyPem = publicKeyPem == null ? "" : publicKeyPem.trim();
        this.algorithm = (algorithm == null || algorithm.isBlank()) ? "Ed25519" : algorithm.trim();
        if (isEnabled()) {
            log.info("Module JAR signature verification ENABLED (algorithm={})", this.algorithm);
        } else {
            log.warn("Module JAR signature verification DISABLED — set "
                    + "kelta.modules.signing.public-key to require signed modules");
        }
    }

    /** Whether a trusted publisher key is configured (signature checks enforced). */
    public boolean isEnabled() {
        return !publicKeyPem.isBlank();
    }

    /**
     * Verifies a detached signature over the JAR bytes.
     *
     * @param jarBytes        the raw module JAR bytes
     * @param signatureBase64 the base64-encoded detached signature
     * @throws ModuleSignatureException when verification is enabled and the
     *         signature is missing, malformed, or does not verify
     */
    public void verify(byte[] jarBytes, String signatureBase64) {
        if (!isEnabled()) {
            return;
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

        boolean verified;
        try {
            PublicKey publicKey = parsePublicKey();
            Signature verifier = Signature.getInstance(algorithm);
            verifier.initVerify(publicKey);
            verifier.update(jarBytes);
            verified = verifier.verify(signatureBytes);
        } catch (Exception e) {
            throw new ModuleSignatureException(
                    "Module JAR signature verification failed: " + e.getMessage(), e);
        }
        if (!verified) {
            throw new ModuleSignatureException(
                    "Module JAR signature does not match the trusted publisher key");
        }
    }

    private PublicKey parsePublicKey() throws Exception {
        String base64 = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance(keyAlgorithm()).generatePublic(new X509EncodedKeySpec(der));
    }

    /** Derives the KeyFactory algorithm from the configured signature algorithm. */
    private String keyAlgorithm() {
        String a = algorithm.toUpperCase();
        if (a.contains("RSA")) {
            return "RSA";
        }
        if (a.contains("ECDSA") || a.equals("EC")) {
            return "EC";
        }
        return "Ed25519";
    }
}
