package io.kelta.auth.federation;

import io.kelta.auth.service.OidcDiscoveryService;
import io.kelta.auth.service.WorkerClient;
import io.kelta.auth.service.WorkerClient.OidcProviderInfo;
import io.kelta.crypto.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically loads OAuth2 client registrations from the OIDC provider database.
 *
 * <p>Each OIDC provider configured for a tenant becomes an OAuth2 client registration
 * that kelta-auth can use to redirect users to external IdPs for authentication.
 *
 * <p>The registration ID is the OIDC provider's database ID, which is also used
 * as the OAuth2 login link: {@code /oauth2/authorization/{providerId}}.
 */
public class DynamicClientRegistrationRepository implements ClientRegistrationRepository {

    private static final Logger log = LoggerFactory.getLogger(DynamicClientRegistrationRepository.class);

    private final WorkerClient workerClient;
    private final OidcDiscoveryService discoveryService;
    private final EncryptionService encryptionService;
    private final Map<String, ClientRegistration> registrationCache = new ConcurrentHashMap<>();

    public DynamicClientRegistrationRepository(
            WorkerClient workerClient,
            OidcDiscoveryService discoveryService,
            EncryptionService encryptionService) {
        this.workerClient = workerClient;
        this.discoveryService = discoveryService;
        this.encryptionService = encryptionService;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        // Check cache first
        ClientRegistration cached = registrationCache.get(registrationId);
        if (cached != null) {
            return cached;
        }

        // The registrationId format is: {tenantId}:{providerId}
        // This allows us to look up the correct provider for the tenant
        String[] parts = registrationId.split(":", 2);
        if (parts.length != 2) {
            log.warn("Invalid registration ID format: {} (expected tenantId:providerId)", registrationId);
            return null;
        }

        String tenantId = parts[0];
        String providerId = parts[1];

        List<OidcProviderInfo> providers = workerClient.findActiveOidcProviders(tenantId);
        for (OidcProviderInfo provider : providers) {
            if (providerId.equals(provider.id())) {
                ClientRegistration registration = buildClientRegistration(registrationId, provider);
                if (registration != null) {
                    registrationCache.put(registrationId, registration);
                }
                return registration;
            }
        }

        log.warn("No OIDC provider found for registration: {}", registrationId);
        return null;
    }

    /**
     * Loads all client registrations for a tenant.
     *
     * @param tenantId the tenant UUID
     * @return list of client registrations for the tenant's external IdPs
     */
    public List<ClientRegistration> findByTenantId(String tenantId) {
        List<OidcProviderInfo> providers = workerClient.findActiveOidcProviders(tenantId);
        return providers.stream()
                .filter(p -> p.clientId() != null && p.clientSecretEnc() != null)
                .map(p -> {
                    String registrationId = tenantId + ":" + p.id();
                    ClientRegistration reg = buildClientRegistration(registrationId, p);
                    if (reg != null) {
                        registrationCache.put(registrationId, reg);
                    }
                    return reg;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Evicts cached registrations (call when OIDC provider config changes).
     */
    public void evictAll() {
        registrationCache.clear();
    }

    private ClientRegistration buildClientRegistration(String registrationId, OidcProviderInfo provider) {
        try {
            // Decrypt client secret
            String clientSecret = null;
            if (provider.clientSecretEnc() != null && !provider.clientSecretEnc().isBlank()) {
                clientSecret = encryptionService.decrypt(provider.clientSecretEnc());
            }

            if (provider.clientId() == null || clientSecret == null) {
                log.debug("Skipping OIDC provider {} — missing clientId or clientSecret", provider.id());
                return null;
            }

            // Resolve endpoints using Discovery + overrides
            OidcDiscoveryService.EndpointOverrides overrides = new OidcDiscoveryService.EndpointOverrides(
                    provider.authorizationUri(),
                    provider.tokenUri(),
                    provider.userinfoUri(),
                    provider.jwksUri(),
                    provider.endSessionUri()
            );
            OidcDiscoveryService.ResolvedEndpoints endpoints =
                    discoveryService.resolve(provider.issuer(), overrides);

            if (endpoints.authorizationUri() == null || endpoints.tokenUri() == null) {
                log.warn("OIDC provider {} missing required endpoints (authorization or token)", provider.id());
                return null;
            }

            return ClientRegistration.withRegistrationId(registrationId)
                    .clientId(provider.clientId())
                    .clientSecret(clientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "email", "profile")
                    .authorizationUri(endpoints.authorizationUri())
                    .tokenUri(endpoints.tokenUri())
                    .userInfoUri(endpoints.userinfoUri())
                    .jwkSetUri(endpoints.jwksUri())
                    .userNameAttributeName(IdTokenClaimNames.SUB)
                    .clientName(provider.name() != null ? provider.name() : provider.issuer())
                    .issuerUri(provider.issuer())
                    .build();

        } catch (Exception e) {
            log.error("Failed to build client registration for OIDC provider {}: {}",
                    provider.id(), e.getMessage());
            return null;
        }
    }
}
