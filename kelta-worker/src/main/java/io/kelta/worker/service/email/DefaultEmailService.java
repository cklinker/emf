package io.kelta.worker.service.email;

import com.fasterxml.jackson.databind.JsonNode;
import io.kelta.runtime.module.integration.spi.EmailService;
import io.kelta.worker.repository.EmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of the {@link EmailService} SPI.
 *
 * <p>Orchestrates email delivery by:
 * <ol>
 *   <li>Resolving per-tenant SMTP settings from {@code tenant.settings} JSONB</li>
 *   <li>Logging the email attempt to {@code email_log}</li>
 *   <li>Delegating delivery to the configured {@link EmailProvider}</li>
 *   <li>Updating the log with success/failure status</li>
 * </ol>
 *
 * <p>Email delivery is asynchronous via {@code @Async("emailExecutor")} to avoid
 * blocking the calling thread. Failures are logged gracefully — the caller
 * receives the log ID immediately and never sees a delivery exception.
 *
 * @since 1.0.0
 */
public class DefaultEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmailService.class);

    private final EmailProvider emailProvider;
    private final EmailRepository emailRepository;
    private final String platformFromAddress;
    private final String platformFromName;
    private final boolean enabled;

    public DefaultEmailService(EmailProvider emailProvider,
                               EmailRepository emailRepository,
                               String platformFromAddress,
                               String platformFromName,
                               boolean enabled) {
        this.emailProvider = emailProvider;
        this.emailRepository = emailRepository;
        this.platformFromAddress = platformFromAddress;
        this.platformFromName = platformFromName;
        this.enabled = enabled;
    }

    @Override
    public Optional<EmailTemplate> getTemplate(String templateId) {
        // TenantContext provides the current tenant ID via thread-local
        String tenantId = io.kelta.runtime.context.TenantContext.get();
        if (tenantId == null) {
            log.warn("No tenant context available for template lookup");
            return Optional.empty();
        }

        return emailRepository.findTemplateByTenantAndId(tenantId, templateId)
                .map(row -> new EmailTemplate(
                        (String) row.get("subject"),
                        (String) row.get("body_html")
                ));
    }

    @Override
    public String queueEmail(String tenantId, String to, String subject, String body,
                             String source, String sourceId) {
        if (!enabled) {
            log.warn("Email delivery disabled — skipping email to {} with subject '{}'", to, subject);
            return UUID.randomUUID().toString();
        }

        // Create log entry immediately (synchronous — gives caller the ID)
        String logId = emailRepository.createEmailLog(tenantId, to, subject, source, sourceId);

        // Resolve tenant settings and send asynchronously
        TenantEmailSettings tenantSettings = resolveTenantSettings(tenantId);
        sendAsync(logId, to, subject, body, tenantSettings);

        return logId;
    }

    @Async("emailExecutor")
    public void sendAsync(String logId, String to, String subject, String body,
                          TenantEmailSettings tenantSettings) {
        String smtpHost = resolveSmtpHost(tenantSettings);

        try {
            emailRepository.markSending(logId);

            EmailMessage message = new EmailMessage(to, subject, body, null);
            emailProvider.send(message, tenantSettings);

            emailRepository.markSent(logId, smtpHost);
            log.info("Email sent: logId={}, to={}, smtpHost={}", logId, to, smtpHost);

        } catch (EmailDeliveryException e) {
            emailRepository.markFailed(logId, e.getMessage(), smtpHost);
            log.error("Email delivery failed: logId={}, to={}, smtpHost={}, error={}",
                    logId, to, smtpHost, e.getMessage());
        } catch (Exception e) {
            emailRepository.markFailed(logId, e.getMessage(), smtpHost);
            log.error("Unexpected error during email delivery: logId={}, to={}", logId, to, e);
        }
    }

    private TenantEmailSettings resolveTenantSettings(String tenantId) {
        try {
            JsonNode settings = emailRepository.getTenantSettings(tenantId);
            return TenantEmailSettings.fromJsonNode(settings);
        } catch (Exception e) {
            log.warn("Failed to load tenant email settings for tenant {}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    private String resolveSmtpHost(TenantEmailSettings tenantSettings) {
        if (emailProvider instanceof SmtpEmailProvider smtp) {
            return smtp.resolveSmtpHost(tenantSettings);
        }
        return "unknown-provider";
    }
}
