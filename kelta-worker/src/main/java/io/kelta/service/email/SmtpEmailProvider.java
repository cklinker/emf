package io.kelta.worker.service.email;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Properties;

/**
 * SMTP-based email provider using Jakarta Mail (RFC 5321).
 *
 * <p>This is the opinionated default provider. It sends emails via standard SMTP
 * and supports per-tenant SMTP configuration overrides. Tenant-specific
 * {@link JavaMailSender} instances are cached (Caffeine, 5-min TTL, max 100 entries)
 * to avoid creating new SMTP connections per email.
 *
 * @since 1.0.0
 */
public class SmtpEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailProvider.class);

    private final JavaMailSender platformMailSender;
    private final String platformFromAddress;
    private final String platformFromName;

    private final Cache<String, JavaMailSenderImpl> tenantSenderCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public SmtpEmailProvider(JavaMailSender platformMailSender,
                             String platformFromAddress,
                             String platformFromName) {
        this.platformMailSender = platformMailSender;
        this.platformFromAddress = platformFromAddress;
        this.platformFromName = platformFromName;
    }

    @Override
    public void send(EmailMessage message, TenantEmailSettings tenantSettings) throws EmailDeliveryException {
        JavaMailSender sender = resolveSender(tenantSettings);
        String fromAddress = resolveFromAddress(tenantSettings);
        String fromName = resolveFromName(tenantSettings);

        try {
            MimeMessage mimeMessage = ((JavaMailSenderImpl) sender instanceof JavaMailSenderImpl impl
                    ? impl : (JavaMailSenderImpl) platformMailSender).createMimeMessage();

            // Use the resolved sender's createMimeMessage if available
            if (sender instanceof JavaMailSenderImpl impl) {
                mimeMessage = impl.createMimeMessage();
            }

            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(message.to());
            helper.setFrom(new InternetAddress(fromAddress, fromName));
            helper.setSubject(message.subject());

            if (message.bodyText() != null) {
                helper.setText(message.bodyText(), message.bodyHtml());
            } else {
                helper.setText(message.bodyHtml(), true);
            }

            sender.send(mimeMessage);

        } catch (MailAuthenticationException e) {
            // Invalidate cached sender on auth failure — credentials may have changed
            if (tenantSettings != null && tenantSettings.hasSmtpOverride()) {
                tenantSenderCache.invalidate(tenantSettings.smtpHost() + ":" + tenantSettings.smtpPort());
                log.warn("SMTP auth failed for tenant SMTP host {}, cache invalidated", tenantSettings.smtpHost());
            }
            throw new EmailDeliveryException("SMTP authentication failed for host " + resolveSmtpHost(tenantSettings), e);
        } catch (MailException e) {
            throw new EmailDeliveryException("SMTP delivery failed: " + e.getMessage(), e);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new EmailDeliveryException("Failed to build email message: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the SMTP host used for a given tenant settings (for audit trail).
     */
    public String resolveSmtpHost(TenantEmailSettings tenantSettings) {
        if (tenantSettings != null && tenantSettings.hasSmtpOverride()) {
            return tenantSettings.smtpHost();
        }
        return platformMailSender instanceof JavaMailSenderImpl impl ? impl.getHost() : "platform-default";
    }

    private JavaMailSender resolveSender(TenantEmailSettings tenantSettings) {
        if (tenantSettings == null || !tenantSettings.hasSmtpOverride()) {
            return platformMailSender;
        }

        String cacheKey = tenantSettings.smtpHost() + ":" + tenantSettings.smtpPort();
        return tenantSenderCache.get(cacheKey, key -> createTenantSender(tenantSettings));
    }

    private JavaMailSenderImpl createTenantSender(TenantEmailSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.smtpHost());
        sender.setPort(settings.smtpPort());
        if (settings.smtpUsername() != null) {
            sender.setUsername(settings.smtpUsername());
            sender.setPassword(settings.smtpPassword());
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", settings.smtpUsername() != null ? "true" : "false");
        props.put("mail.smtp.starttls.enable", String.valueOf(settings.smtpStartTls()));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        log.info("Created tenant SMTP sender for host {}:{}", settings.smtpHost(), settings.smtpPort());
        return sender;
    }

    private String resolveFromAddress(TenantEmailSettings tenantSettings) {
        if (tenantSettings != null && tenantSettings.hasFromOverride()) {
            return tenantSettings.fromAddress();
        }
        return platformFromAddress;
    }

    private String resolveFromName(TenantEmailSettings tenantSettings) {
        if (tenantSettings != null && tenantSettings.fromName() != null && !tenantSettings.fromName().isBlank()) {
            return tenantSettings.fromName();
        }
        return platformFromName;
    }
}
