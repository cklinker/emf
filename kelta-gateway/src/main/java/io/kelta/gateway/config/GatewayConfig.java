package io.kelta.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
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

    /**
     * Shared {@link WebClient.Builder} for outbound calls — every worker-bound
     * caller in the gateway injects this builder and customizes (e.g. sets a
     * baseUrl).
     *
     * <p>When the {@code internal-auth} rollout flag is on, the builder also
     * carries an {@link ExchangeFilterFunction} from
     * {@link InternalServiceAuthConfig} that attaches a short-lived bearer
     * token to any request whose URL path starts with {@code /internal/}. The
     * filter is path-aware, so non-internal traffic (e.g. OIDC discovery, JWKS,
     * health probes against other services) is untouched.
     */
    @Bean
    public WebClient.Builder webClientBuilder(
            @Autowired(required = false) @Qualifier("internalBearerExchangeFilter")
            @Nullable ExchangeFilterFunction internalAuthFilter) {
        WebClient.Builder builder = WebClient.builder();
        if (internalAuthFilter != null) {
            builder.filter(internalAuthFilter);
        }
        return builder;
    }
}
