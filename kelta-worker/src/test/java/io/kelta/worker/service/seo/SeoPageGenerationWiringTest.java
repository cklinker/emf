package io.kelta.worker.service.seo;

import io.kelta.worker.repository.SeoPageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Boot-wiring guard for {@link SeoPageGenerationService}. It injects an {@code ObjectMapper}; the
 * worker is on Jackson 3 ({@code tools.jackson.databind.ObjectMapper}), so a classic
 * {@code com.fasterxml} import would compile and unit-test green but fail the real worker at boot.
 * This exercises the actual constructor injection so that mistake fails here. (See
 * {@code AnalyticsCaptureWiringTest} for the same guard.)
 */
@DisplayName("SEO generation wiring")
class SeoPageGenerationWiringTest {

    @Configuration
    static class Stubs {
        @Bean
        SeoPageRepository seoPageRepository() {
            return mock(SeoPageRepository.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Stubs.class)
            .withBean(SeoPageGenerationService.class);

    @Test
    @DisplayName("wires against the platform's tools.jackson ObjectMapper")
    void wiresWithJackson3() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SeoPageGenerationService.class);
        });
    }
}
