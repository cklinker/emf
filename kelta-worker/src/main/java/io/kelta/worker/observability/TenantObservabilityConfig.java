package io.kelta.worker.observability;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables per-tenant OTLP export configuration (Rec 7). The registry, exporter
 * factory, and {@link PerTenantOtlpSpanProcessor} are component-scanned; the
 * Spring Boot OpenTelemetry auto-configuration adds the {@code SpanProcessor} bean
 * to the SDK tracer provider.
 */
@Configuration
@EnableConfigurationProperties(TenantOtlpProperties.class)
public class TenantObservabilityConfig {
}
