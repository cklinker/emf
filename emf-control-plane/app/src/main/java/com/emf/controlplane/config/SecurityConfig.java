package com.emf.controlplane.config;

import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.repository.OidcProviderRepository;
import com.emf.controlplane.service.JwksCache;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Security configuration for the Control Plane Service.
 * Configures JWT authentication with OAuth2 Resource Server and role extraction.
 * 
 * Requirements satisfied:
 * - 12.1: Require valid JWT token for all API endpoints except health checks
 * - 12.2: Extract roles from JWT claims for authorization
 * - 12.3: Support multiple OIDC providers for JWT validation
 * - 12.4: Validate JWT signature using JWKS from configured providers
 * - 12.5: Cache JWKS keys to minimize external requests (via JwksCache integration)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@ConditionalOnProperty(name = "emf.control-plane.security.enabled", havingValue = "true", matchIfMissing = true)
@Profile("!integration-test")  // Don't activate for integration-test profile
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwksCache jwksCache;
    private final OidcProviderRepository oidcProviderRepository;
    private final ControlPlaneProperties properties;

    public SecurityConfig(JwksCache jwksCache, 
                         OidcProviderRepository oidcProviderRepository,
                         ControlPlaneProperties properties) {
        this.jwksCache = jwksCache;
        this.oidcProviderRepository = oidcProviderRepository;
        this.properties = properties;
    }

    /**
     * Configures CORS for local development.
     * Allows requests from common development ports.
     * 
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // In production, CORS is enforced by the gateway. The control plane
        // is only accessible from within the cluster, so allow all origins.
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Configures the security filter chain with JWT authentication.
     * 
     * - Permits health check endpoints without authentication
     * - Permits OpenAPI and Swagger UI endpoints without authentication
     * - Requires authentication for all other endpoints
     * - Uses stateless session management
     * 
     * @param http HttpSecurity to configure
     * @return Configured SecurityFilterChain
     * @throws Exception if configuration fails
     * 
     * Validates: Requirements 12.1, 12.2, 12.3
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring security filter chain with JWT authentication");

        http
            // Disable CSRF for stateless API
            .csrf(AbstractHttpConfigurer::disable)
            
            // Enable CORS for local development
            .cors(cors -> cors.configure(http))
            
            // Configure stateless session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Configure authorization rules
            .authorizeHttpRequests(authz -> authz
                // Permit health check endpoints
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                
                // Permit OpenAPI and Swagger UI endpoints
                .requestMatchers("/openapi/**").permitAll()
                .requestMatchers("/openapi.json").permitAll()
                .requestMatchers("/openapi.yaml").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/swagger-ui.html").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                
                // Permit UI bootstrap endpoint - required for UI to initialize before auth
                .requestMatchers("/control/ui-bootstrap").permitAll()
                
                // Permit control plane bootstrap endpoint - required for gateway health check
                .requestMatchers("/control/bootstrap").permitAll()
                
                // Require authentication for all other endpoints
                .anyRequest().authenticated()
            )
            
            // Configure OAuth2 Resource Server with JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(multiProviderJwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Creates a JWT authentication converter that extracts roles from JWT claims.
     * 
     * Supports multiple claim locations for roles:
     * - "roles" claim (direct array)
     * - "realm_access.roles" (Keycloak format)
     * - "groups" claim
     * - "scope" claim (space-separated)
     * 
     * @return JwtAuthenticationConverter configured for role extraction
     * 
     * Validates: Requirement 12.2
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RoleExtractingConverter());
        return converter;
    }

    /**
     * Creates a JWT decoder that supports multiple OIDC providers.
     * Uses JwksCache for efficient key retrieval and caching.
     * 
     * @return JwtDecoder configured for multi-provider support
     * 
     * Validates: Requirements 12.3, 12.4, 12.5
     */
    @Bean
    public JwtDecoder multiProviderJwtDecoder() {
        log.info("Creating multi-provider JWT decoder with JWKS caching");
        
        // Create a JWK source that aggregates keys from all active providers
        JWKSource<SecurityContext> jwkSource = new MultiProviderJwkSource();
        
        // Create JWT processor with the multi-provider JWK source
        DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        
        // Configure key selector for common algorithms
        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(
            Set.of(JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
                   JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512),
            jwkSource
        );
        jwtProcessor.setJWSKeySelector(keySelector);
        
        return new NimbusJwtDecoder(jwtProcessor);
    }

    /**
     * Converter that extracts roles from various JWT claim locations.
     * Supports multiple OIDC provider formats.
     */
    private class RoleExtractingConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Set<GrantedAuthority> authorities = new HashSet<>();

            // Extract from "roles" claim (direct array)
            extractRolesFromClaim(jwt, "roles", authorities);

            // Extract from "realm_access.roles" (Keycloak format)
            extractKeycloakRoles(jwt, authorities);

            // Extract from "groups" claim
            extractRolesFromClaim(jwt, "groups", authorities);

            // Extract from "scope" claim (space-separated)
            extractScopeAuthorities(jwt, authorities);

            // Extract from custom claim if configured
            String adminRole = properties.getSecurity().getAdminRole();
            if (hasAdminRole(jwt, adminRole)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + adminRole));
            }

            log.debug("Extracted authorities from JWT: {}", authorities);
            return authorities;
        }

        @SuppressWarnings("unchecked")
        private void extractRolesFromClaim(Jwt jwt, String claimName, Set<GrantedAuthority> authorities) {
            Object claim = jwt.getClaim(claimName);
            if (claim instanceof Collection) {
                ((Collection<String>) claim).forEach(role -> 
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                );
            } else if (claim instanceof String) {
                // Handle comma-separated roles
                Arrays.stream(((String) claim).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
            }
        }

        @SuppressWarnings("unchecked")
        private void extractKeycloakRoles(Jwt jwt, Set<GrantedAuthority> authorities) {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object roles = realmAccess.get("roles");
                if (roles instanceof Collection) {
                    ((Collection<String>) roles).forEach(role ->
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    );
                }
            }

            // Also check resource_access for client-specific roles
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null) {
                resourceAccess.values().forEach(clientAccess -> {
                    if (clientAccess instanceof Map) {
                        Object clientRoles = ((Map<String, Object>) clientAccess).get("roles");
                        if (clientRoles instanceof Collection) {
                            ((Collection<String>) clientRoles).forEach(role ->
                                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                            );
                        }
                    }
                });
            }
        }

        private void extractScopeAuthorities(Jwt jwt, Set<GrantedAuthority> authorities) {
            String scope = jwt.getClaimAsString("scope");
            if (scope != null) {
                Arrays.stream(scope.split(" "))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + s)));
            }
        }

        @SuppressWarnings("unchecked")
        private boolean hasAdminRole(Jwt jwt, String adminRole) {
            // Check various claim locations for admin role
            Object roles = jwt.getClaim("roles");
            if (roles instanceof Collection && ((Collection<String>) roles).stream()
                    .anyMatch(r -> r.equalsIgnoreCase(adminRole))) {
                return true;
            }

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object realmRoles = realmAccess.get("roles");
                if (realmRoles instanceof Collection && ((Collection<String>) realmRoles).stream()
                        .anyMatch(r -> r.equalsIgnoreCase(adminRole))) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * JWK source that aggregates keys from all active OIDC providers.
     * Uses JwksCache for efficient key retrieval with caching.
     * 
     * Validates: Requirements 12.3, 12.4, 12.5
     */
    private class MultiProviderJwkSource implements JWKSource<SecurityContext> {

        @Override
        public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) {
            List<JWK> matchingKeys = new ArrayList<>();

            // Get all active OIDC providers
            List<OidcProvider> providers = oidcProviderRepository.findByActiveTrue();
            
            if (providers.isEmpty()) {
                log.warn("No active OIDC providers configured - JWT validation may fail");
                return matchingKeys;
            }

            // Aggregate keys from all providers
            for (OidcProvider provider : providers) {
                try {
                    JwksCache.JwkSetWrapper wrapper = jwksCache.getJwkSetWithFallback(provider.getId());
                    if (wrapper != null) {
                        JWKSet jwkSet = wrapper.getJwkSet();
                        if (jwkSet != null) {
                            // Select matching keys from this provider's JWKS
                            List<JWK> providerKeys = jwkSelector.select(jwkSet);
                            matchingKeys.addAll(providerKeys);
                            log.debug("Found {} matching keys from provider: {}", 
                                providerKeys.size(), provider.getName());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to get JWKS from provider {}: {}", 
                        provider.getName(), e.getMessage());
                }
            }

            if (matchingKeys.isEmpty()) {
                log.warn("No matching JWK found across {} providers", providers.size());
            }

            return matchingKeys;
        }
    }
}
