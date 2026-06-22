package io.kelta.auth.federation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.saml2.core.Saml2X509Credential;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * The platform-wide SAML service-provider signing credential.
 *
 * <p>Kelta acts as a single SAML SP across all tenants; this one keypair signs
 * outbound AuthnRequests for every tenant's IdP registration and is published in
 * the SP metadata. It is built from PEM config ({@code kelta.auth.saml.sp-signing-*}).
 * When the config is absent the credential is empty and request signing is
 * disabled — the SAML flow still works with IdPs that don't require signed
 * requests.
 */
public final class SamlSpCredentials {

    private static final Logger log = LoggerFactory.getLogger(SamlSpCredentials.class);

    private final Saml2X509Credential signingCredential;

    private SamlSpCredentials(Saml2X509Credential signingCredential) {
        this.signingCredential = signingCredential;
    }

    /** No SP signing credential — AuthnRequests are sent unsigned. */
    public static SamlSpCredentials none() {
        return new SamlSpCredentials(null);
    }

    /**
     * Builds the SP signing credential from PEM cert + PKCS#8 private key. Returns
     * an empty credential (signing disabled) when either value is blank or fails
     * to parse, logging a warning rather than failing startup — a malformed SP key
     * should not take the whole auth server down.
     */
    public static SamlSpCredentials fromPem(String certificatePem, String privateKeyPem) {
        if (certificatePem == null || certificatePem.isBlank()
                || privateKeyPem == null || privateKeyPem.isBlank()) {
            log.info("SAML SP signing not configured (kelta.auth.saml.sp-signing-* unset) — "
                    + "AuthnRequests will be sent unsigned");
            return none();
        }
        try {
            X509Certificate cert = SamlCertificates.parseCertificate(certificatePem);
            PrivateKey key = SamlCertificates.parsePrivateKey(privateKeyPem);
            log.info("SAML SP request signing enabled");
            return new SamlSpCredentials(Saml2X509Credential.signing(key, cert));
        } catch (Exception e) {
            log.warn("SAML SP signing config present but unparseable ({}) — disabling request signing",
                    e.getMessage());
            return none();
        }
    }

    public boolean hasSigning() {
        return signingCredential != null;
    }

    public Optional<Saml2X509Credential> signing() {
        return Optional.ofNullable(signingCredential);
    }
}
