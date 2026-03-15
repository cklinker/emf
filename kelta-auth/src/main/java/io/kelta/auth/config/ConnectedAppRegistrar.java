package io.kelta.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Registers connected apps (e.g. Superset) as OAuth2 clients at startup.
 * Client credentials are read from environment/config so secrets stay out of migrations.
 */
@Component
public class ConnectedAppRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConnectedAppRegistrar.class);

    private final RegisteredClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    public ConnectedAppRegistrar(RegisteredClientRepository clientRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuthProperties properties) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        registerSupersetClient();
    }

    private void registerSupersetClient() {
        if (properties.getSupersetClientId() == null || properties.getSupersetClientId().isBlank()) {
            log.info("No Superset client configured (kelta.auth.superset-client-id not set), skipping registration");
            return;
        }

        String clientId = properties.getSupersetClientId();
        String clientSecret = properties.getSupersetClientSecret();
        String redirectUri = properties.getSupersetRedirectUri();

        if (clientSecret == null || clientSecret.isBlank()) {
            log.warn("Superset client secret not set, skipping registration");
            return;
        }

        RegisteredClient existing = clientRepository.findByClientId(clientId);
        if (existing != null) {
            log.info("Superset OAuth2 client '{}' already registered (id={})", clientId, existing.getId());
            return;
        }

        RegisteredClient supersetClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientName("Apache Superset")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("email")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofHours(8))
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        clientRepository.save(supersetClient);
        log.info("Registered Superset OAuth2 client '{}' with redirect URI '{}'", clientId, redirectUri);
    }
}
