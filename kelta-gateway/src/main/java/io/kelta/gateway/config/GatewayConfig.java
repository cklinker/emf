package io.kelta.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration class for Spring Cloud Gateway routing.
 *
 * Routes are provided by {@link io.kelta.gateway.route.DynamicRouteLocator}, which is
 * a {@code @Component} implementing {@code RouteLocator}. Spring Cloud Gateway's
 * {@code GatewayAutoConfiguration} automatically discovers all {@code RouteLocator}
 * beans and includes them in its {@code CompositeRouteLocator}.
 *
 * <p><strong>Note:</strong> Do NOT define a {@code @Bean RouteLocator routeLocator(...)}
 * here — that would override Spring Cloud Gateway's auto-configured
 * {@code CachingRouteLocator} bean (which is also named "routeLocator"),
 * breaking route caching and the {@code RefreshRoutesEvent} mechanism.
 *
 * Validates: Requirements 9.1, 9.2
 */
@Configuration
public class GatewayConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
