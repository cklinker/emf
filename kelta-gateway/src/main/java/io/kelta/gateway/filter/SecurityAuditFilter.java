package io.kelta.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that logs security-relevant events for audit purposes.
 *
 * <p>Captures authentication failures (401) and authorization denials (403)
 * with structured logging for security monitoring and alerting.
 *
 * <p>Runs after all business logic filters (order 200) to capture final response status.
 */
@Component
public class SecurityAuditFilter implements GlobalFilter, Ordered {

    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpStatus status = (HttpStatus) exchange.getResponse().getStatusCode();
            if (status == null) return;

            String path = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();
            String clientIp = extractClientIp(exchange);

            if (status == HttpStatus.UNAUTHORIZED) {
                logSecurityEvent("AUTH_FAILURE", method, path, clientIp, "401");
            } else if (status == HttpStatus.FORBIDDEN) {
                logSecurityEvent("AUTHZ_DENIED", method, path, clientIp, "403");
            } else if (status == HttpStatus.TOO_MANY_REQUESTS) {
                logSecurityEvent("RATE_LIMIT_HIT", method, path, clientIp, "429");
            }
        }));
    }

    private void logSecurityEvent(String event, String method, String path, String clientIp, String status) {
        try {
            MDC.put("security.event", event);
            MDC.put("security.client_ip", clientIp);
            securityLog.warn("security_event={} method={} path={} client_ip={} status={}",
                    event, method, path, clientIp, status);
        } finally {
            MDC.remove("security.event");
            MDC.remove("security.client_ip");
        }
    }

    private String extractClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return 200; // After SecurityHeadersFilter (100), captures final response status
    }
}
