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
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Security configuration for the API Gateway.
 * Configures JWT validation and authentication with multi-provider support.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${emf.gateway.control-plane.url}")
    private String controlPlaneUrl;

    /**
     * Configures the security filter chain.
     * Since we're using a custom GlobalFilter for authentication,
     * we disable the default Spring Security filters to avoid conflicts.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
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
        return new DynamicReactiveJwtDecoder(controlPlaneClient, redisTemplate, issuerUri);
    }
}
