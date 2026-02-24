package com.emf.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

/**
 * Configuration for service account authentication using OAuth2 client credentials flow.
 *
 * <p>This configuration enables the gateway to authenticate with the worker service
 * using a service account (client credentials grant type). The service account
 * credentials are configured in Keycloak and provided via environment variables.
 *
 * <p>The WebClient is configured with a filter that automatically acquires and adds
 * Bearer tokens to requests using client credentials flow.
 */
@Configuration
public class ServiceAccountConfig {
    
    @Value("${emf.gateway.service-account.client-id:emf-gateway-service}")
    private String clientId;
    
    @Value("${emf.gateway.service-account.client-secret:emf-gateway-service-secret}")
    private String clientSecret;
    
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;
    
    /**
     * Creates a ReactiveClientRegistrationRepository for the gateway service account.
     * This registration is used for client credentials flow to obtain tokens.
     */
    @Bean
    public ReactiveClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration registration = ClientRegistration
            .withRegistrationId("emf-gateway-service")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(issuerUri + "/protocol/openid-connect/token")
            .build();
        
        return new InMemoryReactiveClientRegistrationRepository(registration);
    }
    
    /**
     * Creates a WebClient configured with OAuth2 client credentials authentication.
     * This WebClient automatically adds Bearer tokens to requests using client credentials flow.
     */
    @Bean(name = "serviceAccountWebClient")
    public WebClient serviceAccountWebClient(
            @Value("${emf.gateway.worker-service-url:http://emf-worker:80}") String workerServiceUrl) {

        return WebClient.builder()
            .baseUrl(workerServiceUrl)
            .filter(oauth2ClientCredentialsFilter())
            .build();
    }
    
    /**
     * Creates an exchange filter that adds OAuth2 client credentials authentication.
     * This filter acquires a token using client credentials flow and adds it to the request.
     */
    private ExchangeFilterFunction oauth2ClientCredentialsFilter() {
        return (request, next) -> {
            return getAccessToken()
                .flatMap(token -> {
                    ClientRequest filtered = ClientRequest.from(request)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build();
                    return next.exchange(filtered);
                });
        };
    }
    
    /**
     * Acquires an access token using client credentials flow.
     * This method makes a direct call to the token endpoint with client credentials.
     */
    private Mono<String> getAccessToken() {
        String tokenUri = issuerUri + "/protocol/openid-connect/token";
        String credentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
        
        return WebClient.create()
            .post()
            .uri(tokenUri)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
            .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
            .bodyValue("grant_type=client_credentials")
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> (String) response.get("access_token"));
    }
}
