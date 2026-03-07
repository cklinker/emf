package com.emf.gateway.filter;

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

        // Extract tenant and user context from exchange attributes (set by earlier filters)
        String tenantId = request.getHeaders().getFirst("X-Tenant-ID");
        String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);
        String userId = request.getHeaders().getFirst("X-User-Id");
        String userEmail = request.getHeaders().getFirst("X-Forwarded-User");

        // Set MDC values for structured logging
        MDC.put("correlationId", correlationId);
        if (tenantId != null) MDC.put("tenantId", tenantId);
        if (tenantSlug != null) MDC.put("tenantSlug", tenantSlug);
        if (userId != null) MDC.put("userId", userId);
        if (userEmail != null) MDC.put("userEmail", userEmail);

        // Enrich current OTEL span with custom attributes
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            if (tenantId != null) span.setAttribute("emf.tenant.id", tenantId);
            if (tenantSlug != null) span.setAttribute("emf.tenant.slug", tenantSlug);
            if (userId != null) span.setAttribute("emf.user.id", userId);
            if (userEmail != null) span.setAttribute("emf.user.email", userEmail);
            span.setAttribute("emf.correlation.id", correlationId);
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

    @Override
    public int getOrder() {
        return -90;
    }
}
