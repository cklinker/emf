package io.kelta.worker.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PerTenantOtlpSpanProcessor")
class PerTenantOtlpSpanProcessorTest {

    private static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("kelta.tenant.id");

    @Mock
    private TenantOtlpRegistry registry;

    @Mock
    private TenantSpanExporterFactory factory;

    private PerTenantOtlpSpanProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PerTenantOtlpSpanProcessor(registry, factory);
    }

    private ReadableSpan spanForTenant(String tenantId) {
        ReadableSpan span = mock(ReadableSpan.class);
        lenient().when(span.getAttribute(TENANT_ID)).thenReturn(tenantId);
        return span;
    }

    @Test
    @DisplayName("routes a tenant-tagged span to that tenant's exporter")
    void routesTaggedSpan() {
        OtlpTarget target = new OtlpTarget("https://otlp.t1/v1/traces", Map.of());
        SpanProcessor delegate = mock(SpanProcessor.class);
        when(registry.targetFor("t1")).thenReturn(Optional.of(target));
        when(factory.create(target)).thenReturn(delegate);

        ReadableSpan span = spanForTenant("t1");
        processor.onEnd(span);

        verify(factory).create(target);
        verify(delegate).onEnd(span);
    }

    @Test
    @DisplayName("reuses one exporter per tenant across spans")
    void cachesExporterPerTenant() {
        OtlpTarget target = new OtlpTarget("https://otlp.t1/v1/traces", Map.of());
        SpanProcessor delegate = mock(SpanProcessor.class);
        when(registry.targetFor("t1")).thenReturn(Optional.of(target));
        when(factory.create(target)).thenReturn(delegate);

        processor.onEnd(spanForTenant("t1"));
        processor.onEnd(spanForTenant("t1"));

        verify(factory, times(1)).create(target);
        verify(delegate, times(2)).onEnd(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("re-pointing a tenant's target swaps the exporter and shuts down the old one")
    void repointSwapsExporter() {
        OtlpTarget first = new OtlpTarget("https://otlp.old/v1/traces", Map.of());
        OtlpTarget second = new OtlpTarget("https://otlp.new/v1/traces", Map.of());
        SpanProcessor oldDelegate = mock(SpanProcessor.class);
        SpanProcessor newDelegate = mock(SpanProcessor.class);
        when(registry.targetFor("t1")).thenReturn(Optional.of(first)).thenReturn(Optional.of(second));
        when(factory.create(first)).thenReturn(oldDelegate);
        when(factory.create(second)).thenReturn(newDelegate);

        ReadableSpan spanA = spanForTenant("t1");
        ReadableSpan spanB = spanForTenant("t1");
        processor.onEnd(spanA);
        processor.onEnd(spanB);

        verify(oldDelegate).onEnd(spanA);
        verify(oldDelegate).shutdown();
        verify(newDelegate).onEnd(spanB);
        verify(newDelegate, never()).shutdown();
    }

    @Test
    @DisplayName("unchanged target keeps the cached exporter (no churn per span)")
    void unchangedTargetNoChurn() {
        OtlpTarget target = new OtlpTarget("https://otlp.t1/v1/traces", Map.of("x", "y"));
        SpanProcessor delegate = mock(SpanProcessor.class);
        when(registry.targetFor("t1")).thenReturn(Optional.of(target));
        when(factory.create(target)).thenReturn(delegate);

        processor.onEnd(spanForTenant("t1"));
        processor.onEnd(spanForTenant("t1"));
        processor.onEnd(spanForTenant("t1"));

        verify(factory, times(1)).create(target);
        verify(delegate, never()).shutdown();
    }

    @Test
    @DisplayName("ignores a span with no tenant attribute")
    void ignoresUntaggedSpan() {
        processor.onEnd(spanForTenant(null));
        verifyNoInteractions(factory);
    }

    @Test
    @DisplayName("ignores a tenant with no configured target")
    void ignoresTenantWithoutTarget() {
        when(registry.targetFor("t2")).thenReturn(Optional.empty());
        processor.onEnd(spanForTenant("t2"));
        verify(factory, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("flushes every per-tenant exporter on forceFlush")
    void flushesDelegates() {
        OtlpTarget target = new OtlpTarget("https://otlp.t1/v1/traces", Map.of());
        SpanProcessor delegate = mock(SpanProcessor.class);
        when(registry.targetFor("t1")).thenReturn(Optional.of(target));
        when(factory.create(target)).thenReturn(delegate);
        when(delegate.forceFlush()).thenReturn(CompletableResultCode.ofSuccess());
        processor.onEnd(spanForTenant("t1"));

        processor.forceFlush();

        verify(delegate).forceFlush();
    }

    @Test
    @DisplayName("declares end-required and not start-required")
    void lifecycleFlags() {
        org.assertj.core.api.Assertions.assertThat(processor.isEndRequired()).isTrue();
        org.assertj.core.api.Assertions.assertThat(processor.isStartRequired()).isFalse();
    }

    @Test
    @DisplayName("registry only returns enabled targets with an endpoint (PropertiesTenantOtlpRegistry)")
    void propertiesRegistry() {
        TenantOtlpProperties props = new TenantOtlpProperties();
        TenantOtlpProperties.Target enabled = new TenantOtlpProperties.Target();
        enabled.setEndpoint("https://otlp.acme/v1/traces");
        TenantOtlpProperties.Target disabled = new TenantOtlpProperties.Target();
        disabled.setEndpoint("https://otlp.off/v1/traces");
        disabled.setEnabled(false);
        TenantOtlpProperties.Target noEndpoint = new TenantOtlpProperties.Target();
        props.setTargets(Map.of("on", enabled, "off", disabled, "blank", noEndpoint));

        PropertiesTenantOtlpRegistry reg = new PropertiesTenantOtlpRegistry(props);

        org.assertj.core.api.Assertions.assertThat(reg.targetFor("on"))
                .get()
                .extracting(OtlpTarget::endpoint)
                .isEqualTo("https://otlp.acme/v1/traces");
        org.assertj.core.api.Assertions.assertThat(reg.targetFor("off")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(reg.targetFor("blank")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(reg.targetFor("missing")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(reg.targetFor(null)).isEmpty();
    }
}
