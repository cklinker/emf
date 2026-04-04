package io.kelta.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronizes connected_app records that have authorization_code in their
 * grant_types to the Spring Authorization Server's RegisteredClient repository.
 * <p>
 * This enables third-party apps registered through the platform UI to use the
 * OAuth 2.0 authorization code flow (with optional PKCE) for user-delegated access.
 * <p>
 * Runs after {@link io.kelta.auth.config.ConnectedAppRegistrar} to avoid
 * conflicts with hardcoded platform clients.
 */
@Component
@Order(2) // Run after ConnectedAppRegistrar
public class ConnectedAppClientSynchronizer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConnectedAppClientSynchronizer.class);

    private final JdbcTemplate jdbcTemplate;
    private final RegisteredClientRepository clientRepository;
    private final ObjectMapper objectMapper;

    public ConnectedAppClientSynchronizer(JdbcTemplate jdbcTemplate,
                                          RegisteredClientRepository clientRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.clientRepository = clientRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void run(ApplicationArguments args) {
        synchronizeClients();
    }

    /**
     * Fetches all active connected apps that include authorization_code in their
     * grant_types and ensures each one is registered as an OAuth2 client.
     */
    public void synchronizeClients() {
        List<Map<String, Object>> apps = jdbcTemplate.queryForList(
                "SELECT id, client_id, client_secret_hash, name, redirect_uris, scopes, "
                + "grant_types, require_pkce, consent_required "
                + "FROM connected_app WHERE active = true "
                + "AND grant_types::text LIKE '%authorization_code%'"
        );

        int synced = 0;
        for (Map<String, Object> app : apps) {
            String clientId = (String) app.get("client_id");
            try {
                RegisteredClient existing = clientRepository.findByClientId(clientId);
                if (existing != null) {
                    log.debug("Connected app OAuth2 client '{}' already registered, skipping", clientId);
                    continue;
                }

                registerConnectedAppClient(app);
                synced++;
            } catch (Exception e) {
                log.error("Failed to synchronize connected app client_id={}: {}", clientId, e.getMessage(), e);
            }
        }

        if (synced > 0) {
            log.info("Synchronized {} connected app(s) as OAuth2 authorization_code clients", synced);
        }
    }

    private void registerConnectedAppClient(Map<String, Object> app) {
        String clientId = (String) app.get("client_id");
        String clientSecretHash = (String) app.get("client_secret_hash");
        String name = (String) app.get("name");
        boolean requirePkce = Boolean.TRUE.equals(app.get("require_pkce"));
        boolean consentRequired = Boolean.TRUE.equals(app.get("consent_required"));

        List<String> redirectUris = parseJsonArray(app.get("redirect_uris"));
        List<String> scopes = parseJsonArray(app.get("scopes"));
        List<String> grantTypes = parseJsonArray(app.get("grant_types"));

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                // The secret is already BCrypt-hashed in the DB; Spring expects a
                // pre-encoded value when prefixed with {bcrypt}
                .clientSecret("{bcrypt}" + clientSecretHash)
                .clientName(name);

        // Authentication method
        if (requirePkce) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        }

        // Grant types
        for (String grantType : grantTypes) {
            switch (grantType) {
                case "authorization_code" -> builder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
                case "client_credentials" -> builder.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
                case "refresh_token" -> builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
            }
        }
        // Always allow refresh_token if authorization_code is present
        if (grantTypes.contains("authorization_code")) {
            builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        }

        // Redirect URIs
        for (String uri : redirectUris) {
            builder.redirectUri(uri);
        }

        // Scopes — always include openid for authorization_code flow
        builder.scope(OidcScopes.OPENID);
        builder.scope(OidcScopes.PROFILE);
        builder.scope("email");
        for (String scope : scopes) {
            builder.scope(scope);
        }

        builder.clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(consentRequired)
                .requireProofKey(requirePkce)
                .build());

        builder.tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofHours(1))
                .refreshTokenTimeToLive(Duration.ofDays(7))
                .reuseRefreshTokens(false)
                .build());

        RegisteredClient client = builder.build();
        clientRepository.save(client);
        log.info("Registered connected app '{}' (client_id={}) as OAuth2 client with grant_types={}",
                name, clientId, grantTypes);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonArray(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            String json = value.toString();
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.warn("Failed to parse JSON array: {}", value, e);
            return List.of();
        }
    }
}
