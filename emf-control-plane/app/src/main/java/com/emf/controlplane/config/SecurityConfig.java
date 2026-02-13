package com.emf.controlplane.config;

import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.repository.OidcProviderRepository;
import com.emf.controlplane.service.JwksCache;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.ConcurrentHashMap;

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
            
            // Disable CORS — the gateway handles CORS in production.
            // Enabling it here causes duplicate Access-Control-Allow-Origin headers.
            .cors(AbstractHttpConfigurer::disable)
            
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

                // Permit tenant slug-map endpoint - used by gateway to resolve URL slugs
                .requestMatchers("/control/tenants/slug-map").permitAll()

                // Permit worker endpoints - internal cluster communication (registration, heartbeat, assignments)
                .requestMatchers("/control/workers/**").permitAll()
                .requestMatchers("/control/collections/**").permitAll()
                .requestMatchers("/control/assignments/**").permitAll()

                // Permit worker metrics endpoints - used by KEDA and Prometheus for autoscaling
                .requestMatchers("/control/metrics/**").permitAll()

                // Permit internal endpoints - used by gateway for JWKS lookup before JWT validation
                .requestMatchers("/internal/**").permitAll()

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
     * Converter that extracts roles from JWT claims and applies provider-specific role mappings.
     *
     * Role extraction strategy:
     * 1. If the matching OIDC provider has a rolesClaim configured, extract from that claim
     * 2. Otherwise, extract from default locations: roles, realm_access.roles, groups
     * 3. Apply rolesMapping (if configured) to translate provider roles to internal roles
     * 4. Also extract scope-based authorities
     */
    private class RoleExtractingConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        private static final ObjectMapper objectMapper = new ObjectMapper();

        // Cache parsed role mappings per provider ID to avoid re-parsing JSON on every request
        private final ConcurrentHashMap<String, Map<String, String>> roleMappingCache = new ConcurrentHashMap<>();

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            // Find the matching provider by issuer
            OidcProvider provider = findProviderByIssuer(jwt.getClaimAsString("iss"));

            // Collect raw role strings from JWT claims
            Set<String> rawRoles = new LinkedHashSet<>();
            if (provider != null && provider.getRolesClaim() != null) {
                // Use the provider's configured roles claim
                collectRolesFromClaimPath(jwt, provider.getRolesClaim(), rawRoles);
            } else {
                // Default: extract from standard locations
                collectRolesFromClaim(jwt, "roles", rawRoles);
                collectKeycloakRoles(jwt, rawRoles);
                collectRolesFromClaim(jwt, "groups", rawRoles);
            }

            // Apply role mapping from provider config
            Map<String, String> mapping = getProviderRoleMapping(provider);

            Set<GrantedAuthority> authorities = new HashSet<>();
            for (String rawRole : rawRoles) {
                // Check mapping (try exact match, then case-insensitive)
                String mappedRole = mapping.getOrDefault(rawRole,
                        mapping.getOrDefault(rawRole.toLowerCase(), rawRole));
                authorities.add(new SimpleGrantedAuthority("ROLE_" + mappedRole.toUpperCase()));
            }

            // Extract scope-based authorities (not affected by role mapping)
            extractScopeAuthorities(jwt, authorities);

            // Check if any raw role matches the configured admin role
            String adminRole = properties.getSecurity().getAdminRole();
            boolean isAdmin = rawRoles.stream().anyMatch(r -> r.equalsIgnoreCase(adminRole));
            // Also check mapped roles
            if (!isAdmin) {
                isAdmin = rawRoles.stream()
                        .map(r -> mapping.getOrDefault(r, mapping.getOrDefault(r.toLowerCase(), r)))
                        .anyMatch(r -> r.equalsIgnoreCase(adminRole));
            }
            if (isAdmin) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + adminRole));
            }

            log.debug("Extracted authorities from JWT (issuer={}): {}", jwt.getClaimAsString("iss"), authorities);
            return authorities;
        }

        private OidcProvider findProviderByIssuer(String issuer) {
            if (issuer == null) return null;
            try {
                List<OidcProvider> providers = oidcProviderRepository.findByActiveTrue();
                for (OidcProvider provider : providers) {
                    if (issuer.equals(provider.getIssuer())) {
                        return provider;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to look up OIDC provider for issuer {}: {}", issuer, e.getMessage());
            }
            return null;
        }

        private Map<String, String> getProviderRoleMapping(OidcProvider provider) {
            if (provider == null || provider.getRolesMapping() == null || provider.getRolesMapping().isBlank()) {
                return Collections.emptyMap();
            }
            return roleMappingCache.computeIfAbsent(provider.getId(), id -> {
                try {
                    Map<String, String> parsed = objectMapper.readValue(
                            provider.getRolesMapping(),
                            new TypeReference<Map<String, String>>() {});
                    // Build case-insensitive lookup by also adding lowercase keys
                    Map<String, String> result = new HashMap<>(parsed);
                    for (Map.Entry<String, String> entry : parsed.entrySet()) {
                        result.putIfAbsent(entry.getKey().toLowerCase(), entry.getValue());
                    }
                    return result;
                } catch (Exception e) {
                    log.warn("Failed to parse rolesMapping for provider {}: {}", provider.getName(), e.getMessage());
                    return Collections.emptyMap();
                }
            });
        }

        /**
         * Extract roles from a claim path, supporting dot notation for nested claims.
         * E.g., "groups" extracts jwt.groups, "realm_access.roles" extracts jwt.realm_access.roles.
         */
        @SuppressWarnings("unchecked")
        private void collectRolesFromClaimPath(Jwt jwt, String claimPath, Set<String> roles) {
            if (claimPath.contains(".")) {
                String[] parts = claimPath.split("\\.", 2);
                Object parent = jwt.getClaim(parts[0]);
                if (parent instanceof Map) {
                    Object nested = ((Map<String, Object>) parent).get(parts[1]);
                    collectFromValue(nested, roles);
                }
            } else {
                collectFromValue(jwt.getClaim(claimPath), roles);
            }
        }

        private void collectRolesFromClaim(Jwt jwt, String claimName, Set<String> roles) {
            collectFromValue(jwt.getClaim(claimName), roles);
        }

        private void collectFromValue(Object value, Set<String> roles) {
            if (value instanceof Collection) {
                for (Object item : (Collection<?>) value) {
                    if (item instanceof String) {
                        roles.add((String) item);
                    }
                }
            } else if (value instanceof String) {
                Arrays.stream(((String) value).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(roles::add);
            }
        }

        @SuppressWarnings("unchecked")
        private void collectKeycloakRoles(Jwt jwt, Set<String> roles) {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                collectFromValue(realmAccess.get("roles"), roles);
            }

            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null) {
                resourceAccess.values().forEach(clientAccess -> {
                    if (clientAccess instanceof Map) {
                        collectFromValue(((Map<String, Object>) clientAccess).get("roles"), roles);
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
