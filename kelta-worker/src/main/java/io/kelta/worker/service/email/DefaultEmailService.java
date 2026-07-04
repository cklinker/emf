package io.kelta.worker.service.email;

import io.kelta.runtime.module.integration.spi.EmailService;
import io.kelta.worker.repository.EmailRepository;
import io.kelta.worker.repository.EmailRepository.TenantEmailConfigRow;
import io.kelta.worker.service.credential.CredentialNotFoundException;
import io.kelta.worker.service.credential.CredentialResolver;
import io.kelta.worker.service.credential.ResolutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of the {@link EmailService} SPI.
 *
 * <p>Orchestrates email delivery by:
 * <ol>
 *   <li>Resolving per-tenant SMTP settings — preferring the credential vault row
 *       referenced by {@code tenant.email_smtp_credential_id}, falling back to
 *       the legacy {@code tenant.settings.email} JSONB blob.</li>
 *   <li>Logging the email attempt to {@code email_log}.</li>
 *   <li>Delegating delivery to the configured {@link EmailProvider}.</li>
 *   <li>Updating the log with success/failure status.</li>
 * </ol>
 *
 * <p>Delivery is asynchronous via {@code @Async("emailExecutor")} to avoid
 * blocking the caller. Failures are logged but never raised back to the caller.
 *
 * @since 1.0.0
 */
public class DefaultEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmailService.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_.]+)\\}");

    private final EmailProvider emailProvider;
    private final EmailRepository emailRepository;
    private final CredentialResolver credentialResolver;
    private final String platformFromAddress;
    private final String platformFromName;
    private final boolean enabled;

    public DefaultEmailService(EmailProvider emailProvider,
                               EmailRepository emailRepository,
                               CredentialResolver credentialResolver,
                               String platformFromAddress,
                               String platformFromName,
                               boolean enabled) {
        this.emailProvider = emailProvider;
        this.emailRepository = emailRepository;
        this.credentialResolver = credentialResolver;
        this.platformFromAddress = platformFromAddress;
        this.platformFromName = platformFromName;
        this.enabled = enabled;
    }

    @Override
    public Optional<EmailTemplate> getTemplate(String templateId) {
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

        String logId = emailRepository.createEmailLog(tenantId, to, subject, source, sourceId);
        TenantEmailSettings tenantSettings = resolveTenantSettings(tenantId);
        sendAsync(logId, to, subject, body, tenantSettings);
        return logId;
    }

    @Override
    public Optional<String> sendByKey(String tenantId, String to, String templateKey,
                                      Map<String, Object> vars, String source, String sourceId) {
        var templateRow = emailRepository.findTemplateByKey(tenantId, templateKey);
        if (templateRow.isEmpty()) {
            log.warn("No template found for key '{}' (tenant {} or system fallback)", templateKey, tenantId);
            return Optional.empty();
        }
        Map<String, Object> safeVars = vars == null ? Map.of() : vars;
        String subject = substitute((String) templateRow.get().get("subject"), safeVars);
        String body    = substitute((String) templateRow.get().get("body_html"), safeVars);
        return Optional.of(queueEmail(tenantId, to, subject, body, source, sourceId));
    }

    @Override
    public Optional<String> sendByName(String tenantId, String to, String name,
                                       Map<String, Object> vars, String source, String sourceId) {
        var templateRow = emailRepository.findTemplateByName(tenantId, name);
        if (templateRow.isEmpty()) {
            log.warn("No template found for name '{}' (tenant {} or system fallback)", name, tenantId);
            return Optional.empty();
        }
        Map<String, Object> safeVars = vars == null ? Map.of() : vars;
        String subject = substitute((String) templateRow.get().get("subject"), safeVars);
        String body    = substitute((String) templateRow.get().get("body_html"), safeVars);
        return Optional.of(queueEmail(tenantId, to, subject, body, source, sourceId));
    }

    @Override
    public Optional<String> sendById(String tenantId, String to, String templateId,
                                     Map<String, Object> vars, String source, String sourceId) {
        var templateRow = emailRepository.findTemplateByTenantAndId(tenantId, templateId);
        if (templateRow.isEmpty()) {
            log.warn("No active template found for id '{}' (tenant {})", templateId, tenantId);
            return Optional.empty();
        }
        Map<String, Object> safeVars = vars == null ? Map.of() : vars;
        String subject = substitute((String) templateRow.get().get("subject"), safeVars);
        String body    = substitute((String) templateRow.get().get("body_html"), safeVars);
        return Optional.of(queueEmail(tenantId, to, subject, body, source, sourceId));
    }

    /**
     * Replaces {@code ${name}} placeholders in {@code template} with values from {@code vars}.
     * Unknown variables are left untouched so the message is still readable when a caller forgets
     * a key — and the gap is obvious in QA.
     */
    static String substitute(String template, Map<String, Object> vars) {
        if (template == null) return null;
        Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object value = vars.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(
                    value == null ? m.group(0) : value.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Queues an email with file attachments. Same log/async pipeline as
     * {@link #queueEmail}; not part of the {@code EmailService} SPI — callers
     * needing attachments (scheduled report delivery) inject this class.
     *
     * @return the email log id
     */
    public String queueEmailWithAttachments(String tenantId, String to, String subject, String body,
                                            String source, String sourceId,
                                            java.util.List<EmailAttachment> attachments) {
        if (!enabled) {
            log.warn("Email delivery disabled — skipping email to {} with subject '{}'", to, subject);
            return UUID.randomUUID().toString();
        }
        String logId = emailRepository.createEmailLog(tenantId, to, subject, source, sourceId);
        TenantEmailSettings tenantSettings = resolveTenantSettings(tenantId);
        sendAsync(logId, to, subject, body, tenantSettings, attachments);
        return logId;
    }

    @Async("emailExecutor")
    public void sendAsync(String logId, String to, String subject, String body,
                          TenantEmailSettings tenantSettings) {
        sendAsync(logId, to, subject, body, tenantSettings, java.util.List.of());
    }

    @Async("emailExecutor")
    public void sendAsync(String logId, String to, String subject, String body,
                          TenantEmailSettings tenantSettings,
                          java.util.List<EmailAttachment> attachments) {
        String smtpHost = resolveSmtpHost(tenantSettings);

        try {
            emailRepository.markSending(logId);

            EmailMessage message = new EmailMessage(to, subject, body, null, attachments);
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
            TenantEmailConfigRow row = emailRepository.findTenantEmailConfig(tenantId);
            if (row == null) {
                return null;
            }
            if (row.smtpCredentialId() != null && credentialResolver != null) {
                try {
                    var resolved = credentialResolver.resolve(
                            tenantId,
                            row.smtpCredentialId(),
                            ResolutionContext.forUser(null, "EMAIL_SEND"));
                    return TenantEmailSettings.fromCredential(
                            tenantId,
                            resolved.secretFields(),
                            new HashMap<>(resolved.metadataFields()),
                            row.fromAddress(),
                            row.fromName());
                } catch (CredentialNotFoundException e) {
                    log.warn("Tenant {} references missing SMTP credential {} — falling back",
                            tenantId, row.smtpCredentialId());
                }
            }
            // Legacy JSONB or From-only override
            TenantEmailSettings legacy = row.legacySettings() == null
                    ? null
                    : TenantEmailSettings.fromJsonNode(tenantId, row.legacySettings());
            if (legacy == null && row.fromAddress() == null && row.fromName() == null) {
                return null;
            }
            String fromAddr = row.fromAddress() != null ? row.fromAddress()
                    : (legacy != null ? legacy.fromAddress() : null);
            String fromName = row.fromName() != null ? row.fromName()
                    : (legacy != null ? legacy.fromName() : null);
            return new TenantEmailSettings(
                    tenantId,
                    legacy != null ? legacy.smtpHost() : null,
                    legacy != null ? legacy.smtpPort() : 587,
                    legacy != null ? legacy.smtpUsername() : null,
                    legacy != null ? legacy.smtpPassword() : null,
                    legacy == null || legacy.smtpStartTls(),
                    fromAddr,
                    fromName);
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
