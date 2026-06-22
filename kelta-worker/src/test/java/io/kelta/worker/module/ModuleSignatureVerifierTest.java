package io.kelta.worker.module;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("ModuleSignatureVerifier")
class ModuleSignatureVerifierTest {

    private static final byte[] JAR = "fake-jar-bytes".getBytes(StandardCharsets.UTF_8);

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }

    private static String sign(PrivateKey key, String algorithm, byte[] data) throws Exception {
        Signature s = Signature.getInstance(algorithm);
        s.initSign(key);
        s.update(data);
        return Base64.getEncoder().encodeToString(s.sign());
    }

    private static KeyPair keyPair(String algorithm) throws Exception {
        return KeyPairGenerator.getInstance(algorithm).generateKeyPair();
    }

    @Test
    @DisplayName("disabled (no key configured) is a no-op even without a signature")
    void disabledIsNoOp() {
        ModuleSignatureVerifier v = new ModuleSignatureVerifier("", "Ed25519");
        assertThat(v.isEnabled()).isFalse();
        assertThatCode(() -> v.verify(JAR, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("accepts a valid Ed25519 signature")
    void acceptsValidEd25519() throws Exception {
        KeyPair kp = keyPair("Ed25519");
        ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(kp.getPublic()), "Ed25519");

        assertThat(v.isEnabled()).isTrue();
        assertThatCode(() -> v.verify(JAR, sign(kp.getPrivate(), "Ed25519", JAR)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("accepts a valid SHA256withRSA signature")
    void acceptsValidRsa() throws Exception {
        KeyPair kp = keyPair("RSA");
        ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(kp.getPublic()), "SHA256withRSA");

        assertThatCode(() -> v.verify(JAR, sign(kp.getPrivate(), "SHA256withRSA", JAR)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a signature over different bytes (tamper)")
    void rejectsTamperedJar() throws Exception {
        KeyPair kp = keyPair("Ed25519");
        ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(kp.getPublic()), "Ed25519");
        String sig = sign(kp.getPrivate(), "Ed25519", JAR);

        assertThatThrownBy(() -> v.verify("tampered".getBytes(StandardCharsets.UTF_8), sig))
                .isInstanceOf(ModuleSignatureException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("rejects a signature made with a different key")
    void rejectsWrongKey() throws Exception {
        KeyPair trusted = keyPair("Ed25519");
        KeyPair attacker = keyPair("Ed25519");
        ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(trusted.getPublic()), "Ed25519");

        assertThatThrownBy(() -> v.verify(JAR, sign(attacker.getPrivate(), "Ed25519", JAR)))
                .isInstanceOf(ModuleSignatureException.class);
    }

    @Test
    @DisplayName("rejects a missing signature when enabled")
    void rejectsMissingSignature() throws Exception {
        KeyPair kp = keyPair("Ed25519");
        ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(kp.getPublic()), "Ed25519");

        assertThatThrownBy(() -> v.verify(JAR, "  "))
                .isInstanceOf(ModuleSignatureException.class)
                .hasMessageContaining("required");
    }

    @Test
    @DisplayName("rejects a non-base64 signature")
    void rejectsNonBase64() throws Exception {
        KeyPair kp = keyPair("Ed25519");
        ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(kp.getPublic()), "Ed25519");

        assertThatThrownBy(() -> v.verify(JAR, "not base64 !!!"))
                .isInstanceOf(ModuleSignatureException.class)
                .hasMessageContaining("base64");
    }
}
