package com.emf.controlplane.config;

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

/**
 * Simplified security configuration for integration testing.
 * Uses standard Spring Boot JWT decoder with Keycloak.
 * 
 * This configuration is active when:
 * - Profile is "integration-test"
 * - Security is enabled
 * - spring.security.oauth2.resourceserver.jwt.jwk-set-uri is configured
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("integration-test")
@ConditionalOnProperty(name = "emf.control-plane.security.enabled", havingValue = "true", matchIfMissing = true)
public class IntegrationTestSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(IntegrationTestSecurityConfig.class);

    /**
     * Configures CORS for integration testing.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Configures the security filter chain for integration testing.
     * Uses standard Spring Boot JWT decoder with Keycloak.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring integration test security with standard JWT decoder");

        http
            // Disable CSRF for stateless API
            .csrf(AbstractHttpConfigurer::disable)
            
            // Enable CORS
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
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                
                // Permit UI bootstrap endpoint
                .requestMatchers("/ui/config/bootstrap").permitAll()
                
                // Permit control plane bootstrap endpoint
                .requestMatchers("/control/bootstrap").permitAll()
                
                // Require authentication for all other endpoints
                .anyRequest().authenticated()
            )
            
            // Configure OAuth2 Resource Server with JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Creates a custom JWT decoder that accepts tokens from both localhost and emf-keycloak issuers.
     * This is needed because:
     * - Browser gets tokens with issuer: http://localhost:8180/realms/emf
     * - Container needs to fetch JWKS from: http://emf-keycloak:8180/realms/emf/protocol/openid-connect/certs
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        String jwksUri = "http://emf-keycloak:8180/realms/emf/protocol/openid-connect/certs";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        
        // Don't validate issuer - accept tokens from both localhost and emf-keycloak
        decoder.setJwtValidator(token -> {
            // Skip issuer validation - we trust tokens signed by our JWKS
            return org.springframework.security.oauth2.jwt.JwtValidators.createDefault().validate(token);
        });
        
        log.info("Configured JWT decoder with JWKS URI: {} (issuer validation disabled for local dev)", jwksUri);
        return decoder;
    }

    /**
     * Creates a JWT authentication converter that extracts roles from JWT claims.
     * Supports Keycloak format with realm_access.roles.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RoleExtractingConverter());
        return converter;
    }

    /**
     * Converter that extracts roles from Keycloak JWT claims.
     */
    private static class RoleExtractingConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

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
    }
}
