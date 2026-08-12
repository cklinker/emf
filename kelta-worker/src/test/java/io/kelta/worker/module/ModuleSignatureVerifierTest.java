package io.kelta.worker.module;

import io.kelta.runtime.module.ModuleSigningKey;
import io.kelta.runtime.module.ModuleSigningKeyStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ModuleSignatureVerifier")
class ModuleSignatureVerifierTest {

    private static final byte[] JAR = "fake-jar-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

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

    /** In-memory key store, enough to exercise multi-key and rotation behaviour. */
    private static final class FakeKeyStore implements ModuleSigningKeyStore {
        private final List<ModuleSigningKey> keys = new ArrayList<>();

        ModuleSigningKey add(String tenantId, String label, PublicKey key, String algorithm) {
            ModuleSigningKey row = new ModuleSigningKey(
                    UUID.randomUUID().toString(), tenantId, label, algorithm, pem(key),
                    ModuleSignatureVerifier.fingerprint(pem(key), algorithm),
                    true, null, Instant.now(), "test");
            keys.add(row);
            return row;
        }

        /** Adds a row whose PEM is not a parseable key, to exercise the skip path. */
        void addBroken(String tenantId, String label) {
            keys.add(new ModuleSigningKey(UUID.randomUUID().toString(), tenantId, label, "Ed25519",
                    "-----BEGIN PUBLIC KEY-----\nbm90LWEta2V5\n-----END PUBLIC KEY-----\n",
                    "deadbeef", true, null, Instant.now(), "test"));
        }

        void retire(String fingerprint) {
            keys.replaceAll(k -> k.fingerprint().equals(fingerprint)
                    ? new ModuleSigningKey(k.id(), k.tenantId(), k.label(), k.algorithm(),
                        k.publicKeyPem(), k.fingerprint(), false, Instant.now(), k.createdAt(),
                        k.createdBy())
                    : k);
        }

        @Override
        public List<ModuleSigningKey> findActiveByTenant(String tenantId) {
            return keys.stream().filter(k -> k.tenantId().equals(tenantId)).filter(ModuleSigningKey::active).toList();
        }

        @Override
        public List<ModuleSigningKey> findByTenant(String tenantId) {
            return keys.stream().filter(k -> k.tenantId().equals(tenantId)).toList();
        }

        @Override
        public Optional<ModuleSigningKey> findById(String tenantId, String id) {
            return keys.stream()
                    .filter(k -> k.tenantId().equals(tenantId) && k.id().equals(id))
                    .findFirst();
        }

        @Override
        public String create(ModuleSigningKey key) {
            keys.add(key);
            return key.id();
        }

        @Override
        public boolean setActive(String tenantId, String id, boolean active, String updatedBy) {
            return true;
        }

        @Override
        public boolean delete(String tenantId, String id) {
            return keys.removeIf(k -> k.tenantId().equals(tenantId) && k.id().equals(id));
        }

        @Override
        public int countModulesSignedBy(String tenantId, String fingerprint) {
            return 0;
        }
    }

    private static ModuleSignatureVerifier withKeys(FakeKeyStore store, boolean required) {
        return new ModuleSignatureVerifier("", "Ed25519", required, store);
    }

    @Nested
    @DisplayName("platform-wide key (legacy posture)")
    class PlatformKey {

        @Test
        @DisplayName("no key configured and not required is a no-op even without a signature")
        void disabledIsNoOp() {
            ModuleSignatureVerifier v = new ModuleSignatureVerifier("", "Ed25519");
            assertThat(v.isEnabledFor(TENANT_A)).isFalse();
            assertThat(v.verify(TENANT_A, JAR, null)).isNull();
        }

        @Test
        @DisplayName("accepts a valid Ed25519 signature")
        void acceptsValidEd25519() throws Exception {
            KeyPair kp = keyPair("Ed25519");
            ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(kp.getPublic()), "Ed25519");

            assertThat(v.isEnabledFor(TENANT_A)).isTrue();
            assertThat(v.verify(TENANT_A, JAR, sign(kp.getPrivate(), "Ed25519", JAR)))
                    .isEqualTo(v.platformKeyFingerprint());
        }

        @Test
        @DisplayName("accepts a valid SHA256withRSA signature")
        void acceptsValidRsa() throws Exception {
            KeyPair kp = keyPair("RSA");
            ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(kp.getPublic()), "SHA256withRSA");

            assertThatCode(() -> v.verify(TENANT_A, JAR, sign(kp.getPrivate(), "SHA256withRSA", JAR)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects a signature over different bytes (tamper)")
        void rejectsTamperedJar() throws Exception {
            KeyPair kp = keyPair("Ed25519");
            ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(kp.getPublic()), "Ed25519");
            String sig = sign(kp.getPrivate(), "Ed25519", JAR);

            assertThatThrownBy(() -> v.verify(TENANT_A, "tampered".getBytes(StandardCharsets.UTF_8), sig))
                    .isInstanceOf(ModuleSignatureException.class)
                    .hasMessageContaining("does not match");
        }

        @Test
        @DisplayName("rejects a signature made with a different key")
        void rejectsWrongKey() throws Exception {
            KeyPair trusted = keyPair("Ed25519");
            KeyPair attacker = keyPair("Ed25519");
            ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(trusted.getPublic()), "Ed25519");

            assertThatThrownBy(() -> v.verify(TENANT_A, JAR, sign(attacker.getPrivate(), "Ed25519", JAR)))
                    .isInstanceOf(ModuleSignatureException.class);
        }

        @Test
        @DisplayName("rejects a missing signature when a key is configured")
        void rejectsMissingSignature() throws Exception {
            KeyPair kp = keyPair("Ed25519");
            ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(kp.getPublic()), "Ed25519");

            assertThatThrownBy(() -> v.verify(TENANT_A, JAR, "  "))
                    .isInstanceOf(ModuleSignatureException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("rejects a non-base64 signature")
        void rejectsNonBase64() throws Exception {
            KeyPair kp = keyPair("Ed25519");
            ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(kp.getPublic()), "Ed25519");

            assertThatThrownBy(() -> v.verify(TENANT_A, JAR, "not base64 !!!"))
                    .isInstanceOf(ModuleSignatureException.class)
                    .hasMessageContaining("base64");
        }

        @Test
        @DisplayName("is trusted for every tenant — the property per-tenant keys remove")
        void platformKeyAppliesToAllTenants() throws Exception {
            KeyPair kp = keyPair("Ed25519");
            ModuleSignatureVerifier v = new ModuleSignatureVerifier(pem(kp.getPublic()), "Ed25519");
            String sig = sign(kp.getPrivate(), "Ed25519", JAR);

            assertThatCode(() -> v.verify(TENANT_A, JAR, sig)).doesNotThrowAnyException();
            assertThatCode(() -> v.verify(TENANT_B, JAR, sig)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("per-tenant keys")
    class PerTenant {

        @Test
        @DisplayName("accepts a signature from the tenant's own key and returns its fingerprint")
        void acceptsTenantKey() throws Exception {
            FakeKeyStore store = new FakeKeyStore();
            KeyPair kp = keyPair("Ed25519");
            ModuleSigningKey key = store.add(TENANT_A, "2026-h1", kp.getPublic(), "Ed25519");
            ModuleSignatureVerifier v = withKeys(store, true);

            assertThat(v.verify(TENANT_A, JAR, sign(kp.getPrivate(), "Ed25519", JAR)))
                    .isEqualTo(key.fingerprint());
        }

        @Test
        @DisplayName("a JAR signed for one tenant is rejected in another — the whole point")
        void tenantKeysAreIsolated() throws Exception {
            FakeKeyStore store = new FakeKeyStore();
            KeyPair aKey = keyPair("Ed25519");
            KeyPair bKey = keyPair("Ed25519");
            store.add(TENANT_A, "a", aKey.getPublic(), "Ed25519");
            store.add(TENANT_B, "b", bKey.getPublic(), "Ed25519");
            ModuleSignatureVerifier v = withKeys(store, true);

            String signedForA = sign(aKey.getPrivate(), "Ed25519", JAR);
            assertThatCode(() -> v.verify(TENANT_A, JAR, signedForA)).doesNotThrowAnyException();
            assertThatThrownBy(() -> v.verify(TENANT_B, JAR, signedForA))
                    .isInstanceOf(ModuleSignatureException.class)
                    .hasMessageContaining(TENANT_B);
        }

        @Test
        @DisplayName("one tenant's key set does not enable verification for another tenant")
        void enablementIsPerTenant() throws Exception {
            FakeKeyStore store = new FakeKeyStore();
            store.add(TENANT_A, "a", keyPair("Ed25519").getPublic(), "Ed25519");
            ModuleSignatureVerifier v = withKeys(store, false);

            assertThat(v.isEnabledFor(TENANT_A)).isTrue();
            assertThat(v.isEnabledFor(TENANT_B)).isFalse();
        }
    }

    @Nested
    @DisplayName("rotation with multiple active keys")
    class Rotation {

        @Test
        @DisplayName("both old and new keys verify while both are active")
        void bothKeysVerifyDuringOverlap() throws Exception {
            FakeKeyStore store = new FakeKeyStore();
            KeyPair oldKey = keyPair("Ed25519");
            KeyPair newKey = keyPair("Ed25519");
            ModuleSigningKey oldRow = store.add(TENANT_A, "old", oldKey.getPublic(), "Ed25519");
            ModuleSigningKey newRow = store.add(TENANT_A, "new", newKey.getPublic(), "Ed25519");
            ModuleSignatureVerifier v = withKeys(store, true);

            assertThat(v.verify(TENANT_A, JAR, sign(oldKey.getPrivate(), "Ed25519", JAR)))
                    .isEqualTo(oldRow.fingerprint());
            assertThat(v.verify(TENANT_A, JAR, sign(newKey.getPrivate(), "Ed25519", JAR)))
                    .isEqualTo(newRow.fingerprint());
        }

        @Test
        @DisplayName("adding a second key does not invalidate JARs signed by the first")
        void addingAKeyIsNonBreaking() throws Exception {
            FakeKeyStore store = new FakeKeyStore();
            KeyPair oldKey = keyPair("Ed25519");
            store.add(TENANT_A, "old", oldKey.getPublic(), "Ed25519");
            ModuleSignatureVerifier v = withKeys(store, true);
            String existingSignature = sign(oldKey.getPrivate(), "Ed25519", JAR);

            store.add(TENANT_A, "new", keyPair("Ed25519").getPublic(), "Ed25519");

            // This is the load-path assertion: a JAR is re-verified on every load, so if adding a
            // key invalidated old signatures, every installed module would drop to stubs at once.
            assertThatCode(() -> v.verify(TENANT_A, JAR, existingSignature))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("retiring a key stops accepting its signatures")
        void retiringAKeyRevokesIt() throws Exception {
            FakeKeyStore store = new FakeKeyStore();
            KeyPair oldKey = keyPair("Ed25519");
            ModuleSigningKey oldRow = store.add(TENANT_A, "old", oldKey.getPublic(), "Ed25519");
            store.add(TENANT_A, "new", keyPair("Ed25519").getPublic(), "Ed25519");
            ModuleSignatureVerifier v = withKeys(store, true);
            String signedByOld = sign(oldKey.getPrivate(), "Ed25519", JAR);

            store.retire(oldRow.fingerprint());

            assertThatThrownBy(() -> v.verify(TENANT_A, JAR, signedByOld))
                    .isInstanceOf(ModuleSignatureException.class);
        }

        @Test
        @DisplayName("keys of different algorithms can be active together")
        void mixedAlgorithmsRotate() throws Exception {
            FakeKeyStore store = new FakeKeyStore();
            KeyPair ed = keyPair("Ed25519");
            KeyPair rsa = keyPair("RSA");
            store.add(TENANT_A, "ed", ed.getPublic(), "Ed25519");
            store.add(TENANT_A, "rsa", rsa.getPublic(), "SHA256withRSA");
            ModuleSignatureVerifier v = withKeys(store, true);

            assertThatCode(() -> v.verify(TENANT_A, JAR, sign(ed.getPrivate(), "Ed25519", JAR)))
                    .doesNotThrowAnyException();
            assertThatCode(() -> v.verify(TENANT_A, JAR, sign(rsa.getPrivate(), "SHA256withRSA", JAR)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("one unparseable key row is skipped, not fatal to the others")
        void brokenKeyDoesNotBrickInstalls() throws Exception {
            FakeKeyStore store = new FakeKeyStore();
            store.addBroken(TENANT_A, "typo");
            KeyPair good = keyPair("Ed25519");
            ModuleSigningKey goodRow = store.add(TENANT_A, "good", good.getPublic(), "Ed25519");
            ModuleSignatureVerifier v = withKeys(store, true);

            assertThat(v.verify(TENANT_A, JAR, sign(good.getPrivate(), "Ed25519", JAR)))
                    .isEqualTo(goodRow.fingerprint());
        }

        @Test
        @DisplayName("a wrong signature reports how many keys were tried and how many were unusable")
        void failureMessageDistinguishesUnusableKeys() throws Exception {
            FakeKeyStore store = new FakeKeyStore();
            store.addBroken(TENANT_A, "typo");
            store.add(TENANT_A, "good", keyPair("Ed25519").getPublic(), "Ed25519");
            ModuleSignatureVerifier v = withKeys(store, true);

            assertThatThrownBy(() -> v.verify(TENANT_A, JAR,
                    sign(keyPair("Ed25519").getPrivate(), "Ed25519", JAR)))
                    .isInstanceOf(ModuleSignatureException.class)
                    .hasMessageContaining("2 trusted signing key(s)")
                    .hasMessageContaining("1 of them unusable");
        }
    }

    @Nested
    @DisplayName("required=true fails closed")
    class FailClosed {

        @Test
        @DisplayName("a tenant with no key cannot install, rather than installing unverified")
        void noKeyBlocksInstall() {
            ModuleSignatureVerifier v = withKeys(new FakeKeyStore(), true);

            assertThat(v.isEnabledFor(TENANT_A)).isFalse();
            assertThatThrownBy(() -> v.verify(TENANT_A, JAR, null))
                    .isInstanceOf(ModuleSignatureException.class)
                    .hasMessageContaining("no active module signing key");
        }

        @Test
        @DisplayName("a signature is not accepted just because it is well-formed")
        void noKeyRejectsEvenAValidLookingSignature() throws Exception {
            KeyPair kp = keyPair("Ed25519");
            ModuleSignatureVerifier v = withKeys(new FakeKeyStore(), true);

            assertThatThrownBy(() -> v.verify(TENANT_A, JAR, sign(kp.getPrivate(), "Ed25519", JAR)))
                    .isInstanceOf(ModuleSignatureException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("retiring every key blocks installs — it does not disable the gate")
        void retiringAllKeysDoesNotBypass() throws Exception {
            FakeKeyStore store = new FakeKeyStore();
            KeyPair kp = keyPair("Ed25519");
            ModuleSigningKey only = store.add(TENANT_A, "only", kp.getPublic(), "Ed25519");
            ModuleSignatureVerifier v = withKeys(store, true);

            store.retire(only.fingerprint());

            // The bypass this closes: with required=false, dropping the last key would turn
            // verification back into a no-op and let an unsigned JAR install.
            assertThatThrownBy(() -> v.verify(TENANT_A, JAR, null))
                    .isInstanceOf(ModuleSignatureException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("required=false keeps the legacy no-op for a tenant with no key")
        void notRequiredIsBackCompatible() {
            ModuleSignatureVerifier v = withKeys(new FakeKeyStore(), false);
            assertThat(v.verify(TENANT_A, JAR, null)).isNull();
        }
    }

    @Nested
    @DisplayName("fingerprint")
    class Fingerprints {

        @Test
        @DisplayName("is stable across PEM reformatting")
        void stableAcrossFormatting() throws Exception {
            KeyPair kp = keyPair("Ed25519");
            String base64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            String wrapped = "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
            String unwrapped = "-----BEGIN PUBLIC KEY-----" + base64 + "-----END PUBLIC KEY-----";

            assertThat(ModuleSignatureVerifier.fingerprint(wrapped, "Ed25519"))
                    .isEqualTo(ModuleSignatureVerifier.fingerprint(unwrapped, "Ed25519"));
        }

        @Test
        @DisplayName("differs between keys")
        void differsBetweenKeys() throws Exception {
            assertThat(ModuleSignatureVerifier.fingerprint(pem(keyPair("Ed25519").getPublic()), "Ed25519"))
                    .isNotEqualTo(
                        ModuleSignatureVerifier.fingerprint(pem(keyPair("Ed25519").getPublic()), "Ed25519"));
        }

        @Test
        @DisplayName("rejects a PEM that is not a key for the stated algorithm")
        void rejectsAlgorithmMismatch() throws Exception {
            String rsaPem = pem(keyPair("RSA").getPublic());
            assertThatThrownBy(() -> ModuleSignatureVerifier.fingerprint(rsaPem, "Ed25519"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects junk rather than fingerprinting bytes that can never verify")
        void rejectsJunk() {
            assertThatThrownBy(() -> ModuleSignatureVerifier.fingerprint("not a pem", "Ed25519"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ModuleSignatureVerifier.fingerprint("", "Ed25519"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
