package io.kelta.worker.config;

import io.kelta.runtime.module.integration.spi.EmailService;
import io.kelta.worker.repository.EmailRepository;
import io.kelta.worker.service.email.DefaultEmailService;
import io.kelta.worker.service.email.EmailProvider;
import io.kelta.worker.service.email.SmtpEmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Configuration for the email delivery subsystem.
 *
 * <p>Provides the {@link EmailProvider} (SMTP by default), the {@link DefaultEmailService},
 * and an async executor for non-blocking email delivery. Enabled by default;
 * disable with {@code kelta.email.enabled=false}.
 *
 * @since 1.0.0
 */
@Configuration
@EnableAsync
public class EmailConfig {

    @Bean
    @ConditionalOnProperty(name = "kelta.email.enabled", havingValue = "true", matchIfMissing = true)
    public EmailProvider emailProvider(
            JavaMailSender javaMailSender,
            @Value("${kelta.email.from-address:noreply@kelta.io}") String fromAddress,
            @Value("${kelta.email.from-name:Kelta Platform}") String fromName) {
        return new SmtpEmailProvider(javaMailSender, fromAddress, fromName);
    }

    @Bean
    @ConditionalOnProperty(name = "kelta.email.enabled", havingValue = "true", matchIfMissing = true)
    public DefaultEmailService defaultEmailService(
            EmailProvider emailProvider,
            EmailRepository emailRepository,
            @Value("${kelta.email.from-address:noreply@kelta.io}") String fromAddress,
            @Value("${kelta.email.from-name:Kelta Platform}") String fromName,
            @Value("${kelta.email.enabled:true}") boolean enabled) {
        return new DefaultEmailService(emailProvider, emailRepository, fromAddress, fromName, enabled);
    }

    /**
     * No-op {@link EmailService} for use when {@code kelta.email.enabled=false}.
     * Satisfies the constructor dependency of {@link io.kelta.worker.controller.InternalEmailController}
     * so the application context starts cleanly even without SMTP configuration.
     */
    @Bean
    @ConditionalOnProperty(name = "kelta.email.enabled", havingValue = "false")
    public EmailService noOpEmailService() {
        Logger log = LoggerFactory.getLogger(EmailConfig.class);
        return new EmailService() {
            @Override
            public Optional<EmailTemplate> getTemplate(String templateId) {
                return Optional.empty();
            }

            @Override
            public String queueEmail(String tenantId, String to, String subject, String body,
                                     String source, String sourceId) {
                log.debug("Email disabled — dropping email to {} (subject: {})", to, subject);
                return "disabled";
            }
        };
    }

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        executor.initialize();
        return executor;
    }
}
