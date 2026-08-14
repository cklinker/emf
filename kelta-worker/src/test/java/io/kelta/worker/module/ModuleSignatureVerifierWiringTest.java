package io.kelta.worker.module;

import io.kelta.runtime.module.ModuleSigningKey;
import io.kelta.runtime.module.ModuleSigningKeyStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link ModuleSignatureVerifier} is actually constructible by Spring.
 *
 * <p>It has two constructors — the {@code @Value}-driven one and a platform-key-only one used by
 * tests. Spring's single-constructor autowiring rule does not apply once there is more than one,
 * so without an explicit {@code @Autowired} it looks for a no-arg constructor and fails the worker
 * at startup. Nothing else would catch that: the worker has no {@code @SpringBootTest}, so no test
 * loads an application context, and unit tests calling {@code new ModuleSignatureVerifier(...)}
 * pass either way.
 */
@DisplayName("ModuleSignatureVerifier Spring wiring")
class ModuleSignatureVerifierWiringTest {

    /**
     * {@code @Import} of the class itself, deliberately — not a {@code @Bean} method calling the
     * constructor by hand. Spring has to resolve the constructor and the {@code @Value} parameters
     * exactly as component scanning would, which is the thing under test. A {@code @Bean} method
     * would pass even with the ambiguity present.
     */
    @Configuration
    @org.springframework.context.annotation.Import(ModuleSignatureVerifier.class)
    static class VerifierConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(VerifierConfig.class);

    @Test
    @DisplayName("constructs with no properties set and no key store")
    void constructsWithDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ModuleSignatureVerifier verifier = context.getBean(ModuleSignatureVerifier.class);
            assertThat(verifier.isRequired()).isFalse();
            assertThat(verifier.platformKeyFingerprint()).isNull();
        });
    }

    @Test
    @DisplayName("picks up kelta.modules.signing.required")
    void readsRequiredProperty() {
        runner.withPropertyValues("kelta.modules.signing.required=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ModuleSignatureVerifier.class).isRequired()).isTrue();
        });
    }

    @Test
    @DisplayName("injects the key store when one is present")
    void injectsKeyStore() {
        runner.withBean(ModuleSigningKeyStore.class, EmptyKeyStore::new)
                .withPropertyValues("kelta.modules.signing.required=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // Reaching the store at all is the assertion: with no anchors and
                    // required=true the verifier must refuse rather than silently no-op.
                    ModuleSignatureVerifier verifier = context.getBean(ModuleSignatureVerifier.class);
                    assertThat(verifier.isEnabledFor("tenant-a")).isFalse();
                });
    }

    /** A store with no keys — enough to prove injection happened. */
    static class EmptyKeyStore implements ModuleSigningKeyStore {
        @Override
        public List<ModuleSigningKey> findActiveByTenant(String tenantId) {
            return List.of();
        }

        @Override
        public List<ModuleSigningKey> findByTenant(String tenantId) {
            return List.of();
        }

        @Override
        public Optional<ModuleSigningKey> findById(String tenantId, String id) {
            return Optional.empty();
        }

        @Override
        public String create(ModuleSigningKey key) {
            return key.id();
        }

        @Override
        public boolean setActive(String tenantId, String id, boolean active, String updatedBy) {
            return false;
        }

        @Override
        public boolean delete(String tenantId, String id) {
            return false;
        }

        @Override
        public int countModulesSignedBy(String tenantId, String fingerprint) {
            return 0;
        }
    }
}
