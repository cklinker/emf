package io.kelta.worker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration that enforces OAuth2 bearer-token authentication
 * on the worker's {@code /internal/**} endpoints and leaves every other path
 * untouched so the existing gateway-trusts-headers model keeps working.
 *
 * <p>The platform's gateway, ai, and auth services call into {@code /internal/**}
 * for bootstrap data, slug-maps, OIDC lookups, and JIT user provisioning. Those
 * calls now carry a short-lived JWT obtained from kelta-auth via the
 * {@code client_credentials} grant; this filter chain validates the JWT via
 * kelta-auth's JWKS and requires the {@code SCOPE_internal} authority on the
 * bearer token.
 *
 * <p>If {@code kelta.auth.issuer-uri} (or the Spring Boot standard
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}) is blank — for
 * example in a local dev container started before kelta-auth — the filter chain
 * is still registered, but requests will fail closed with 401. Configure the
 * issuer explicitly to enable internal calls.
 */
@Configuration
@EnableWebSecurity
public class InternalEndpointSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(InternalEndpointSecurityConfig.class);

    private static final String INTERNAL_PATH = "/internal/**";
    private static final String INTERNAL_SCOPE = "SCOPE_internal";

    /**
     * Security chain for the service-to-service {@code /internal/**} paths. Runs
     * ahead of {@link #defaultPermitAllFilterChain} so non-internal requests
     * never see the JWT filter.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalEndpointFilterChain(HttpSecurity http,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:${kelta.auth.issuer-uri:}}") String issuerUri)
            throws Exception {

        if (issuerUri == null || issuerUri.isBlank()) {
            log.warn("OAuth2 issuer URI is not configured — /internal/** endpoints will reject every request. "
                    + "Set spring.security.oauth2.resourceserver.jwt.issuer-uri to enable service-to-service calls.");
        } else {
            log.info("Worker /internal/** protected by OAuth2 JWT (issuer={}, required scope=internal)", issuerUri);
        }

        http
                .securityMatcher(INTERNAL_PATH)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasAuthority(INTERNAL_SCOPE))
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * Catch-all chain that preserves the worker's existing posture: the gateway
     * authenticates users upstream and forwards trusted tenant/user headers, so
     * the worker does not re-authenticate user traffic. Without this chain
     * Spring Security auto-config would lock every endpoint down.
     */
    @Bean
    @Order(100)
    public SecurityFilterChain defaultPermitAllFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
