package io.kelta.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Wires kelta-auth's service-to-service OAuth2 client for {@code /internal/**}
 * calls into the worker.
 *
 * <p>kelta-auth is the token issuer, so this client asks itself for tokens via
 * {@code client_credentials}. The matching client is registered by
 * {@link InternalClientRegistrar} at startup from
 * {@code kelta.auth.internal-clients.auth-internal.secret}.
 *
 * <p>Exposes {@link #internalWorkerRestClient} — a pre-configured
 * {@link RestClient} with a synchronous request-phase interceptor that attaches
 * {@code Authorization: Bearer …} to every request whose path starts with
 * {@code /internal/}. Non-internal calls pass through untouched, so callers
 * that hit {@code /api/internal/email/send} (still on the legacy
 * {@code X-Internal-Token} path) are unaffected.
 *
 * <p><b>Rollout flag.</b> Gated behind {@code kelta.auth.internal-auth.enabled=true}.
 * When off (the default during rollout) no beans are created and
 * {@link io.kelta.auth.service.WorkerClient} keeps using its pre-change
 * {@code RestClient.builder()} construction — matching worker's own
 * {@code kelta.worker.internal-auth.enabled=false} default state.
 */
@Configuration
public class InternalWorkerClientConfig {

    private static final Logger log = LoggerFactory.getLogger(InternalWorkerClientConfig.class);

    /** Registration id used to look up the client in Spring Security. */
    public static final String CLIENT_REGISTRATION_ID = "auth-internal";

    /** Path prefix that triggers bearer-token attachment. */
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    /**
     * Registers the {@code auth-internal} OAuth2 client. kelta-auth is its own
     * token issuer so the token URI points at the local authorization server.
     */
    @Bean
    @ConditionalOnProperty(name = "kelta.auth.internal-auth.enabled", havingValue = "true")
    public ClientRegistrationRepository internalClientRegistrations(
            @Value("${kelta.auth.internal-auth.client-id:auth-internal}") String clientId,
            @Value("${kelta.auth.internal-auth.client-secret:}") String clientSecret,
            AuthProperties authProperties) {

        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException(
                    "kelta.auth.internal-auth.enabled=true requires kelta.auth.internal-auth.client-secret "
                            + "to be configured. It must match kelta.auth.internal-clients." + clientId + ".secret.");
        }
        String issuerUri = authProperties.getIssuerUri();
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException(
                    "kelta.auth.issuer-uri must be configured when internal-auth is enabled.");
        }

        String tokenUri = issuerUri.endsWith("/") ? issuerUri + "oauth2/token" : issuerUri + "/oauth2/token";
        ClientRegistration registration = ClientRegistration.withRegistrationId(CLIENT_REGISTRATION_ID)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(tokenUri)
                .scope("internal")
                .build();

        log.info("Registered OAuth2 client '{}' for worker /internal/** calls (token URI: {})",
                clientId, tokenUri);
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    @ConditionalOnProperty(name = "kelta.auth.internal-auth.enabled", havingValue = "true")
    public OAuth2AuthorizedClientService internalAuthorizedClientService(
            ClientRegistrationRepository clients) {
        return new InMemoryOAuth2AuthorizedClientService(clients);
    }

    @Bean
    @ConditionalOnProperty(name = "kelta.auth.internal-auth.enabled", havingValue = "true")
    public AuthorizedClientServiceOAuth2AuthorizedClientManager internalAuthorizedClientManager(
            ClientRegistrationRepository clients,
            OAuth2AuthorizedClientService service) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clients, service);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build());
        return manager;
    }

    /**
     * Pre-configured {@link RestClient} that attaches a bearer token to any
     * request whose URL path starts with {@code /internal/}. Consumers inject
     * this instead of building their own {@link RestClient.Builder}.
     */
    @Bean
    @ConditionalOnProperty(name = "kelta.auth.internal-auth.enabled", havingValue = "true")
    public RestClient internalWorkerRestClient(
            AuthorizedClientServiceOAuth2AuthorizedClientManager clientManager,
            AuthProperties authProperties) {

        // client_credentials has no end-user principal; a stable service identity
        // lets the cached authorized-client lookup hit on every request.
        Authentication servicePrincipal = new AnonymousAuthenticationToken(
                CLIENT_REGISTRATION_ID,
                CLIENT_REGISTRATION_ID,
                List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));

        return RestClient.builder()
                .baseUrl(authProperties.getWorkerUrl())
                .requestInterceptor((request, body, execution) -> {
                    String path = request.getURI().getPath();
                    if (path != null && path.startsWith(INTERNAL_PATH_PREFIX)) {
                        var client = clientManager.authorize(
                                OAuth2AuthorizeRequest
                                        .withClientRegistrationId(CLIENT_REGISTRATION_ID)
                                        .principal(servicePrincipal)
                                        .build());
                        if (client == null || client.getAccessToken() == null) {
                            throw new IllegalStateException(
                                    "client_credentials exchange returned no token for " + CLIENT_REGISTRATION_ID);
                        }
                        request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
