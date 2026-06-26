package io.kelta.auth.federation;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * PEM parsing helpers for SAML credentials, using only the JDK security
 * providers (no BouncyCastle). Certificates come from the per-tenant
 * {@code saml_provider.idp_certificate} column and from the platform SP
 * signing config; both are PEM-encoded.
 */
final class SamlCertificates {

    private SamlCertificates() {}

    /**
     * Parses a PEM-encoded X.509 certificate.
     *
     * @throws IllegalArgumentException if the PEM is blank or cannot be parsed
     */
    static X509Certificate parseCertificate(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("Certificate PEM is blank");
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse X.509 certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a PKCS#8 PEM-encoded RSA private key.
     *
     * @throws IllegalArgumentException if the PEM is blank or cannot be parsed
     */
    static PrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("Private key PEM is blank");
        }
        try {
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse PKCS#8 private key: " + e.getMessage(), e);
        }
    }
}
