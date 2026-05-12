package io.kelta.auth.aot;

import io.kelta.auth.model.KeltaSession;
import io.kelta.auth.model.KeltaUserDetails;
import io.kelta.auth.model.KeltaUserDetailsMixin;
import io.kelta.auth.service.WorkerClient;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.lang.Nullable;

/**
 * GraalVM native-image hints for kelta-auth.
 *
 * Imported via @ImportRuntimeHints on AuthApplication. Covers reflection /
 * serialization targets that the framework's auto-generated hints don't see:
 * Kelta domain classes hit by Jackson + Spring Security's serializer chain,
 * the Spring Security Jackson 2 module mixin/deserializer classes (which
 * Jackson instantiates reflectively at runtime), and Thymeleaf templates.
 */
public class AuthRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        registerKeltaModels(hints);
        registerWorkerClientPayloads(hints);
        registerSpringSecurityJackson2(hints);
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

    /**
     * Spring Security's Jackson 2 modules instantiate their mixins and
     * deserializers reflectively. The framework ships no native-image hints
     * for these packages (Jackson 3 has its own pipeline), so we register
     * every Jackson 2 helper class auth uses.
     */
    private void registerSpringSecurityJackson2(RuntimeHints hints) {
        String[] classNames = {
                // spring-security-core
                "org.springframework.security.jackson2.CoreJackson2Module",
                "org.springframework.security.jackson2.SecurityJackson2Modules",
                "org.springframework.security.jackson2.SecurityJackson2Modules$AllowlistTypeIdResolver",
                "org.springframework.security.jackson2.SecurityJackson2Modules$AllowlistTypeResolverBuilder",
                "org.springframework.security.jackson2.AbstractUnmodifiableCollectionDeserializer",
                "org.springframework.security.jackson2.AnonymousAuthenticationTokenMixin",
                "org.springframework.security.jackson2.BadCredentialsExceptionMixin",
                "org.springframework.security.jackson2.FactorGrantedAuthorityMixin",
                "org.springframework.security.jackson2.RememberMeAuthenticationTokenMixin",
                "org.springframework.security.jackson2.SimpleGrantedAuthorityMixin",
                "org.springframework.security.jackson2.UnmodifiableListDeserializer",
                "org.springframework.security.jackson2.UnmodifiableListMixin",
                "org.springframework.security.jackson2.UnmodifiableMapDeserializer",
                "org.springframework.security.jackson2.UnmodifiableMapMixin",
                "org.springframework.security.jackson2.UnmodifiableSetDeserializer",
                "org.springframework.security.jackson2.UnmodifiableSetMixin",
                "org.springframework.security.jackson2.UserDeserializer",
                "org.springframework.security.jackson2.UserMixin",
                "org.springframework.security.jackson2.UsernamePasswordAuthenticationTokenDeserializer",
                "org.springframework.security.jackson2.UsernamePasswordAuthenticationTokenMixin",

                // spring-security-oauth2-authorization-server
                "org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module",
                "org.springframework.security.oauth2.server.authorization.jackson2.DurationMixin",
                "org.springframework.security.oauth2.server.authorization.jackson2.HashSetMixin",
                "org.springframework.security.oauth2.server.authorization.jackson2.JsonNodeUtils",
                "org.springframework.security.oauth2.server.authorization.jackson2.JwsAlgorithmMixin",
                "org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationRequestDeserializer",
                "org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationRequestMixin",
                "org.springframework.security.oauth2.server.authorization.jackson2.OAuth2TokenExchangeActorMixin",
                "org.springframework.security.oauth2.server.authorization.jackson2.OAuth2TokenExchangeCompositeAuthenticationTokenMixin",
                "org.springframework.security.oauth2.server.authorization.jackson2.OAuth2TokenFormatMixin",
                "org.springframework.security.oauth2.server.authorization.jackson2.StringArrayMixin",
                "org.springframework.security.oauth2.server.authorization.jackson2.UnmodifiableMapDeserializer",
                "org.springframework.security.oauth2.server.authorization.jackson2.UnmodifiableMapMixin",

                // spring-security-oauth2-client
                "org.springframework.security.oauth2.client.jackson2.OAuth2ClientJackson2Module",
                "org.springframework.security.oauth2.client.jackson2.ClientRegistrationDeserializer",
                "org.springframework.security.oauth2.client.jackson2.ClientRegistrationMixin",
                "org.springframework.security.oauth2.client.jackson2.DefaultOAuth2UserMixin",
                "org.springframework.security.oauth2.client.jackson2.DefaultOidcUserMixin",
                "org.springframework.security.oauth2.client.jackson2.JsonNodeUtils",
                "org.springframework.security.oauth2.client.jackson2.OAuth2AccessTokenMixin",
                "org.springframework.security.oauth2.client.jackson2.OAuth2AuthenticationExceptionMixin",
                "org.springframework.security.oauth2.client.jackson2.OAuth2AuthenticationTokenMixin",
                "org.springframework.security.oauth2.client.jackson2.OAuth2AuthorizationRequestDeserializer",
                "org.springframework.security.oauth2.client.jackson2.OAuth2AuthorizationRequestMixin",
                "org.springframework.security.oauth2.client.jackson2.OAuth2AuthorizedClientMixin",
                "org.springframework.security.oauth2.client.jackson2.OAuth2ErrorMixin",
                "org.springframework.security.oauth2.client.jackson2.OAuth2RefreshTokenMixin",
                "org.springframework.security.oauth2.client.jackson2.OAuth2UserAuthorityMixin",
                "org.springframework.security.oauth2.client.jackson2.OidcIdTokenMixin",
                "org.springframework.security.oauth2.client.jackson2.OidcUserAuthorityMixin",
                "org.springframework.security.oauth2.client.jackson2.OidcUserInfoMixin",
                "org.springframework.security.oauth2.client.jackson2.StdConverters",
                "org.springframework.security.oauth2.client.jackson2.StdConverters$AccessTokenTypeConverter",
                "org.springframework.security.oauth2.client.jackson2.StdConverters$AuthenticationMethodConverter",
                "org.springframework.security.oauth2.client.jackson2.StdConverters$AuthorizationGrantTypeConverter",
                "org.springframework.security.oauth2.client.jackson2.StdConverters$ClientAuthenticationMethodConverter"
        };
        for (String name : classNames) {
            hints.reflection().registerType(TypeReference.of(name), MemberCategory.values());
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
