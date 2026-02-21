package com.emf.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that adds security headers to all gateway responses.
 * Runs with order 100 (after all business logic filters, before response is sent).
 *
 * This filter adds the following security headers:
 * - X-Content-Type-Options: nosniff — Prevents MIME type sniffing
 * - X-Frame-Options: DENY — Prevents clickjacking by disallowing framing
 * - Strict-Transport-Security — Enforces HTTPS with max-age of 1 year
 * - Referrer-Policy: strict-origin-when-cross-origin — Controls referrer information
 * - Permissions-Policy — Disables camera, microphone, and geolocation
 * - Cache-Control: no-store — Prevents caching of API responses
 * - Pragma: no-cache — HTTP/1.0 backward-compatible cache prevention
 *
 * These headers follow OWASP security best practices for API gateways.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SecurityHeadersFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();

            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
            headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            headers.set("Cache-Control", "no-store");
            headers.set("Pragma", "no-cache");

            log.debug("Added security headers to response for path: {}",
                    exchange.getRequest().getPath().value());
        }));
    }

    @Override
    public int getOrder() {
        return 100; // Run after business logic filters but before response is sent
    }
}
