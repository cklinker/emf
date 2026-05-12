package io.kelta.auth.aot;

import io.kelta.auth.model.KeltaSession;
import io.kelta.auth.model.KeltaUserDetails;
import io.kelta.auth.model.KeltaUserDetailsMixin;
import io.kelta.auth.service.WorkerClient;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.lang.Nullable;

/**
 * GraalVM native-image hints for kelta-auth.
 *
 * Wired via META-INF/spring/aot.factories so Spring Boot picks it up during
 * native-image compilation. Covers reflection / serialization targets that
 * the framework's auto-generated hints don't see: Kelta domain classes hit
 * by Jackson + Spring Security's serializer chain, and Thymeleaf templates.
 */
public class AuthRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        registerKeltaModels(hints);
        registerWorkerClientPayloads(hints);
        registerSerialization(hints);
        registerResources(hints);
    }

    private void registerKeltaModels(RuntimeHints hints) {
        // KeltaUserDetails is reflectively serialized by Spring Authorization
        // Server's JdbcOAuth2AuthorizationRowMapper (via its custom ObjectMapper
        // configured in AuthorizationServerConfig). Needs full reflective access.
        hints.reflection().registerType(KeltaUserDetails.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.DECLARED_FIELDS);

        hints.reflection().registerType(KeltaUserDetailsMixin.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.DECLARED_FIELDS);

        hints.reflection().registerType(KeltaSession.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.DECLARED_FIELDS);

        hints.reflection().registerType(KeltaSession.Builder.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.DECLARED_FIELDS);
    }

    private void registerWorkerClientPayloads(RuntimeHints hints) {
        Class<?>[] payloads = {
                WorkerClient.OidcProviderInfo.class,
                WorkerClient.UserIdentity.class,
                WorkerClient.ProfileInfo.class,
                WorkerClient.JitProvisionResult.class
        };
        for (Class<?> payload : payloads) {
            hints.reflection().registerType(payload,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
        }
    }

    private void registerSerialization(RuntimeHints hints) {
        // KeltaSession travels through Spring Session's Redis serializer.
        hints.serialization().registerType(KeltaSession.class);
    }

    private void registerResources(RuntimeHints hints) {
        // Thymeleaf templates rendered at runtime — must be in the native image.
        hints.resources().registerPattern("templates/*.html");

        // Password blocklist consulted by PasswordPolicyService.
        hints.resources().registerPattern("common-passwords.txt");

        // Logback config — Logback's own native hints register logback-spring.xml
        // by default; include it here explicitly in case the auto-hint misses it.
        hints.resources().registerPattern("logback-spring.xml");
    }
}
