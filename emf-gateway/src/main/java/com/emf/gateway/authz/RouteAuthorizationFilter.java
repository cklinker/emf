package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that enforces route-level authorization.
 *
 * <p>Currently enforces authentication only (valid JWT required). Authenticated
 * requests are forwarded to the backend service, which handles its own
 * fine-grained authorization via Spring Security.
 *
 * <p>Bootstrap endpoints ({@code /control/bootstrap} and {@code /control/ui-bootstrap})
 * are allowed without authentication so the UI and gateway can fetch initial config.
 */
@Component
public class RouteAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Allow unauthenticated access to bootstrap endpoints
        if ("/control/bootstrap".equals(path) || "/control/ui-bootstrap".equals(path)) {
            return chain.filter(exchange);
        }

        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);

        if (principal == null) {
            log.warn("No principal found in exchange for path: {}", path);
            return forbidden(exchange, "Authentication required");
        }

        log.debug("Authenticated user: {} accessing path: {}", principal.getUsername(), path);
        return chain.filter(exchange);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");

        String errorJson = String.format(
            "{\"errors\":[{\"status\":\"403\",\"code\":\"FORBIDDEN\",\"detail\":\"%s\",\"meta\":{\"path\":\"%s\"}}]}",
            message,
            exchange.getRequest().getPath().value()
        );

        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(errorJson.getBytes()))
        );
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
