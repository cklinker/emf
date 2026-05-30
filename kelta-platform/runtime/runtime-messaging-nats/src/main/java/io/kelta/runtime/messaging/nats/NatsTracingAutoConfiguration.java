package io.kelta.runtime.messaging.nats;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link OtelNatsTracing} as the {@link NatsTracing} bean when the
 * application provides an {@link OpenTelemetry} instance and the OTel API is on
 * the classpath. Loaded before {@link NatsAutoConfiguration} so this bean wins
 * over the no-op fallback registered there.
 */
@AutoConfiguration(before = NatsAutoConfiguration.class)
@ConditionalOnClass(OpenTelemetry.class)
public class NatsTracingAutoConfiguration {

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnMissingBean(NatsTracing.class)
    public NatsTracing otelNatsTracing(OpenTelemetry openTelemetry) {
        return new OtelNatsTracing(openTelemetry);
    }
}
