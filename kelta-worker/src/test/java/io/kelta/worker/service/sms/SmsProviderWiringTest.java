package io.kelta.worker.service.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Bean-wiring guard for the SMS providers.
 *
 * <p>Adding {@link TwilioSmsProvider} puts a second {@code SmsProvider} implementation in the
 * codebase; if both it and {@link LogOnlySmsProvider} could be active at once, the single-typed
 * injection into {@link DefaultSmsService} (and {@code AlertDispatchService}) would be ambiguous
 * and the worker would fail to start — the exact class of trap that has bitten the push providers
 * before. The two are mutually exclusive via {@code @ConditionalOnProperty}; this pins that shut.
 * Nothing else in the suite exercises the Spring context, so without this test the failure would
 * first appear in a deployed environment.
 */
@DisplayName("SMS provider wiring")
class SmsProviderWiringTest {

    @Configuration
    static class Stubs {
        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Stubs.class)
            .withBean(DefaultSmsService.class)
            .withUserConfiguration(LogOnlySmsProvider.class, TwilioSmsProvider.class);

    @Test
    @DisplayName("defaults to exactly one provider (log) and starts cleanly")
    void defaultsToLogOnly() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(SmsProvider.class)).hasSize(1);
            assertThat(context).hasSingleBean(LogOnlySmsProvider.class);
            assertThat(context).doesNotHaveBean(TwilioSmsProvider.class);
            assertThat(context).hasSingleBean(DefaultSmsService.class);
        });
    }

    @Test
    @DisplayName("provider=twilio activates Twilio only — even with blank credentials the worker boots")
    void twilioSelectedBootsEvenUnconfigured() {
        runner.withPropertyValues("kelta.sms.provider=twilio").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(SmsProvider.class)).hasSize(1);
            assertThat(context).hasSingleBean(TwilioSmsProvider.class);
            assertThat(context).doesNotHaveBean(LogOnlySmsProvider.class);
        });
    }

    @Test
    @DisplayName("provider=log keeps the log provider")
    void logSelectedKeepsLog() {
        runner.withPropertyValues("kelta.sms.provider=log").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LogOnlySmsProvider.class);
            assertThat(context).doesNotHaveBean(TwilioSmsProvider.class);
        });
    }
}
