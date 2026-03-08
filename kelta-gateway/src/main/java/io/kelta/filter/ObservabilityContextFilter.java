package io.kelta.gateway.filter;

import io.kelta.gateway.auth.GatewayPrincipal;
import io.kelta.gateway.auth.JwtAuthenticationFilter;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive global filter that enriches MDC and OTEL context with tenant/user information.
 * Generates X-Correlation-ID if absent and propagates it downstream.
 * Runs early (order -90) so all downstream filters/handlers have context available.
 */
@Component
public class ObservabilityContextFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityContextFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Generate or extract correlation ID
        String correlationId = request.getHeaders().getFirst("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Extract tenant context from exchange attributes (set by TenantResolutionFilter)
        String tenantId = TenantResolutionFilter.getTenantId(exchange);
        String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);

        // Read user context from the authenticated principal (set by JwtAuthenticationFilter).
        // Cannot read from X-User-Id headers here because HeaderTransformationFilter
        // (order 50) hasn't run yet to set them.
        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);
        String userEmail = principal != null ? principal.getUsername() : null;
        String userId = principal != null ? resolveUserId(principal) : null;

        // Set MDC values for structured logging
        MDC.put("correlationId", correlationId);
        if (tenantId != null) MDC.put("tenantId", tenantId);
        if (tenantSlug != null) MDC.put("tenantSlug", tenantSlug);
        if (userId != null) MDC.put("userId", userId);
        if (userEmail != null) MDC.put("userEmail", userEmail);

        // Capture the actual request path for Jaeger visibility.
        // The OTEL agent names gateway spans generically (e.g., "GET") because
        // Spring Cloud Gateway doesn't expose route templates to the agent.
        // Adding the concrete path as an attribute makes traces far easier to read.
        String requestPath = request.getURI().getPath();
        String requestMethod = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String queryString = request.getURI().getRawQuery();

        // Enrich current OTEL span with custom attributes
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            span.setAttribute("http.url.path", requestPath);
            if (queryString != null) span.setAttribute("http.url.query", queryString);
            if (tenantId != null) span.setAttribute("kelta.tenant.id", tenantId);
            if (tenantSlug != null) span.setAttribute("kelta.tenant.slug", tenantSlug);
            if (userId != null) span.setAttribute("kelta.user.id", userId);
            if (userEmail != null) span.setAttribute("kelta.user.email", userEmail);
            span.setAttribute("kelta.correlation.id", correlationId);

            // Update span name to include the path for Jaeger readability
            span.updateName(requestMethod + " " + requestPath);
        }

        // Set OTEL Baggage for downstream propagation
        Baggage baggage = Baggage.current().toBuilder()
                .put("tenant.id", tenantId != null ? tenantId : "")
                .put("tenant.slug", tenantSlug != null ? tenantSlug : "")
                .put("user.id", userId != null ? userId : "")
                .build();

        // Add correlation ID header to downstream request
        String finalCorrelationId = correlationId;
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Correlation-ID", finalCorrelationId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .contextWrite(ctx -> ctx.put("correlationId", finalCorrelationId))
                .doFinally(signalType -> {
                    MDC.remove("correlationId");
                    MDC.remove("tenantId");
                    MDC.remove("tenantSlug");
                    MDC.remove("userId");
                    MDC.remove("userEmail");
                });
    }

    /**
     * Resolves the user ID from the JWT principal.
     * Uses the same logic as HeaderTransformationFilter for consistency.
     */
    private String resolveUserId(GatewayPrincipal principal) {
        Object sub = principal.getClaims().get("sub");
        if (sub instanceof String s && !s.isEmpty()) {
            return s;
        }
        Object userId = principal.getClaims().get("user_id");
        if (userId instanceof String s && !s.isEmpty()) {
            return s;
        }
        return principal.getUsername();
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
