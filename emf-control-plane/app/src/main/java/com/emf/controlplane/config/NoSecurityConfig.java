package com.emf.controlplane.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration that permits all requests when security is disabled.
 * Used for local development only.
 * 
 * WARNING: Do not use in production!
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "emf.control-plane.security.enabled", havingValue = "false")
public class NoSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(NoSecurityConfig.class);

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.warn("Security is DISABLED - all endpoints are accessible without authentication");
        log.warn("This configuration should only be used for local development!");

        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
