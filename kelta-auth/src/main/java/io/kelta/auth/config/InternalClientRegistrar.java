package io.kelta.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Registers service-to-service OAuth2 clients for the {@code client_credentials}
 * grant at startup — one per internal caller (e.g. {@code gateway-internal},
 * {@code auth-internal}, {@code ai-internal}).
 *
 * <p>Each registered client issues short-lived access tokens with the
 * {@code internal} scope, which the worker's resource-server filter requires on
 * requests to {@code /internal/**}. Replaces the previous shared-static-token
 * scheme for cross-service calls inside the cluster.
 *
 * <p>Clients are provisioned from {@link AuthProperties#getInternalClients()};
 * entries with a blank secret are skipped so the registrar stays opt-in per
 * environment. Existing registrations are updated in place so secret rotation
 * at deploy time requires only a new env var, not a DB migration.
 */
@Component
@Order(10) // after ConnectedAppRegistrar so the platform client is in place first
public class InternalClientRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InternalClientRegistrar.class);

    /** Scope granted to every internal client; checked by worker resource-server. */
    public static final String INTERNAL_SCOPE = "internal";

    private final RegisteredClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    public InternalClientRegistrar(RegisteredClientRepository clientRepository,
                                    PasswordEncoder passwordEncoder,
                                    AuthProperties properties) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, AuthProperties.InternalClient> configured = properties.getInternalClients();
        if (configured == null || configured.isEmpty()) {
            log.info("No internal OAuth2 clients configured (kelta.auth.internal-clients); "
                    + "service-to-service calls continue to use the legacy shared token if any");
            return;
        }

        int registered = 0;
        int updated = 0;
        int skipped = 0;
        for (Map.Entry<String, AuthProperties.InternalClient> entry : configured.entrySet()) {
            String clientId = entry.getKey();
            String secret = entry.getValue() != null ? entry.getValue().getSecret() : null;
            if (secret == null || secret.isBlank()) {
                log.info("Internal client '{}' has no secret configured — skipping", clientId);
                skipped++;
                continue;
            }

            RegisteredClient existing = clientRepository.findByClientId(clientId);
            RegisteredClient client = buildClient(existing, clientId, secret);
            clientRepository.save(client);
            if (existing == null) {
                registered++;
                log.info("Registered internal OAuth2 client '{}' (client_credentials grant, scope={})",
                        clientId, INTERNAL_SCOPE);
            } else {
                updated++;
                log.info("Updated internal OAuth2 client '{}' (rotated secret)", clientId);
            }
        }

        log.info("Internal client registration complete: registered={}, updated={}, skipped={}",
                registered, updated, skipped);
    }

    private RegisteredClient buildClient(RegisteredClient existing, String clientId, String secret) {
        String id = existing != null ? existing.getId() : UUID.randomUUID().toString();
        return RegisteredClient.withId(id)
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(secret))
                .clientName("Kelta Internal Service: " + clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(INTERNAL_SCOPE)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        // Short-lived so a leaked token is low-blast-radius.
                        // Callers re-use a cached token until ~near-expiry.
                        .accessTokenTimeToLive(Duration.ofMinutes(10))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }
}
