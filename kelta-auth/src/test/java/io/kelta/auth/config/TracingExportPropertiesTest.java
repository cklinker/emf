package io.kelta.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the one tracing property that a GraalVM native image cannot pick up from the
 * environment.
 *
 * <p>Spring Boot only registers the OTLP span exporter when
 * {@code management.opentelemetry.tracing.export.otlp.endpoint} is set:
 * {@code OtlpTracingConfigurations.ConnectionDetails} is {@code @ConditionalOnProperty} on it
 * with no {@code matchIfMissing}, and {@code Exporters} is {@code @ConditionalOnBean} on the
 * connection-details bean it creates. Native images evaluate conditions at BUILD time and
 * freeze the result, so supplying the endpoint only as a runtime env var is too late — the
 * exporter bean never exists and the service exports no spans at all, with no error logged.
 * Spans are still created (log MDC carries real trace ids), which makes this look like a
 * collector problem rather than a missing bean.
 *
 * <p>Declaring the property here with an env-overridable default makes the condition true at
 * AOT time. The env var still selects the collector URL at runtime.
 */
@DisplayName("kelta-auth tracing defaults")
class TracingExportPropertiesTest {

    private static final String ENDPOINT = "management.opentelemetry.tracing.export.otlp.endpoint";

    private Properties load() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        // FileSystemResource, not ClassPathResource: src/test/resources/application.yml
        // shadows the shipped one on the test classpath, and the shipped file is what
        // the AOT build reads. Surefire runs with the module directory as its cwd.
        factory.setResources(new FileSystemResource("src/main/resources/application.yml"));
        factory.afterPropertiesSet();
        Properties props = factory.getObject();
        assertNotNull(props, "application.yml did not parse");
        return props;
    }

    @Test
    @DisplayName("declares the OTLP tracing endpoint so AOT registers the span exporter")
    void declaresOtlpTracingEndpoint() {
        String value = load().getProperty(ENDPOINT);
        assertNotNull(value, ENDPOINT + " must be declared in application.yml. Setting it only via "
                + "the environment leaves the native image without an OTLP span exporter, and "
                + "kelta-auth exports no traces.");
        assertTrue(!value.isBlank(), "the OTLP tracing endpoint must not be blank");
    }

    @Test
    @DisplayName("the endpoint stays overridable by the environment")
    void endpointIsEnvironmentOverridable() {
        String value = load().getProperty(ENDPOINT);
        assertNotNull(value);
        assertTrue(value.startsWith("${") && value.contains(":"),
                "the endpoint must keep an ${ENV_VAR:default} placeholder so each environment can "
                        + "point at its own collector; found: " + value);
    }
}
