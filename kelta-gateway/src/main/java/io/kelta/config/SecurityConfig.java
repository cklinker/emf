package io.kelta.gateway.config;

import io.kelta.gateway.auth.DynamicReactiveJwtDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
// Note: Bean returns DynamicReactiveJwtDecoder (which implements ReactiveJwtDecoder)
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${kelta.gateway.worker-service-url:http://kelta-worker:80}")
    private String workerServiceUrl;

    @Value("${CORS_ALLOWED_ORIGIN_PATTERN:}")
    private String corsAllowedOriginPattern;

    @Value("${kelta.gateway.security.jwt-clock-skew-seconds:30}")
    private long jwtClockSkewSeconds;

    /**
     * Configures CORS at the WebFlux security layer.
     * This ensures CORS headers are present on all responses, including
     * error responses that short-circuit before gateway route matching.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        if (corsAllowedOriginPattern == null || corsAllowedOriginPattern.isBlank()) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGIN_PATTERN must be configured. " +
                    "Set it to your UI domain (e.g., 'https://app.example.com'). " +
                    "Wildcard '*' is not permitted with allowCredentials=true.");
        }
        if ("*".equals(corsAllowedOriginPattern)) {
            log.warn("CORS_ALLOWED_ORIGIN_PATTERN is set to '*' which allows any origin " +
                    "to make credentialed requests. This is insecure for production.");
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(corsAllowedOriginPattern));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-Correlation-ID",
                "X-Tenant-ID", "X-Tenant-Slug", "X-Requested-With",
                "traceparent", "tracestate", "baggage",
                "b3", "X-B3-TraceId", "X-B3-SpanId", "X-B3-ParentSpanId", "X-B3-Sampled"));
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
     *
     * <p>OIDC provider info is fetched from the worker's internal API
     * ({@code /internal/oidc/by-issuer}).
     */
    @Bean
    public DynamicReactiveJwtDecoder jwtDecoder(@Nullable ReactiveStringRedisTemplate redisTemplate) {
        WebClient workerClient = WebClient.builder()
                .baseUrl(workerServiceUrl)
                .build();
        return new DynamicReactiveJwtDecoder(workerClient, redisTemplate, issuerUri,
                Duration.ofSeconds(jwtClockSkewSeconds));
    }
}
