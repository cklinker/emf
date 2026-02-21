package com.emf.gateway.config;

import com.emf.gateway.auth.DynamicReactiveJwtDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the API Gateway.
 * Configures JWT validation, authentication with multi-provider support,
 * and CORS at the security layer.
 * <p>
 * CORS is configured here (rather than relying solely on Spring Cloud Gateway's
 * globalcors) so that CORS headers are added to ALL responses, including error
 * responses (e.g. 404 from tenant slug resolution) that occur before gateway
 * route matching.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${emf.gateway.control-plane.url}")
    private String controlPlaneUrl;

    @Value("${CORS_ALLOWED_ORIGIN_PATTERN:*}")
    private String corsAllowedOriginPattern;

    @Value("${emf.gateway.security.jwt-clock-skew-seconds:30}")
    private long jwtClockSkewSeconds;

    /**
     * Configures CORS at the WebFlux security layer.
     * This ensures CORS headers are present on all responses, including
     * error responses that short-circuit before gateway route matching.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(corsAllowedOriginPattern));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-Correlation-ID",
                "X-Tenant-ID", "X-Tenant-Slug", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        configuration.setExposedHeaders(Arrays.asList(
                "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset", "Retry-After"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Configures the security filter chain.
     * Since we're using a custom GlobalFilter for authentication,
     * we disable the default Spring Security filters to avoid conflicts.
     * CORS is enabled and delegates to the CorsConfigurationSource bean.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll()
            )
            .build();
    }

    /**
     * Creates a DynamicReactiveJwtDecoder bean that supports multi-provider JWT validation.
     * Resolves the correct JWKS URI based on the token's issuer claim.
     * Falls back to the configured default issuer if no provider is found.
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Nullable ReactiveStringRedisTemplate redisTemplate) {
        WebClient controlPlaneClient = WebClient.builder()
                .baseUrl(controlPlaneUrl)
                .build();
        return new DynamicReactiveJwtDecoder(controlPlaneClient, redisTemplate, issuerUri,
                Duration.ofSeconds(jwtClockSkewSeconds));
    }
}
