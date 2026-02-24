package com.emf.runtime.module.integration.spi.noop;

import com.emf.runtime.module.integration.spi.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * No-op implementation of {@link EmailService} that logs operations
 * and returns dummy IDs. Used as a default when no real email infrastructure is configured.
 *
 * @since 1.0.0
 */
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public Optional<EmailTemplate> getTemplate(String templateId) {
        log.info("[NOOP] EmailService.getTemplate: templateId={} — returning empty", templateId);
        return Optional.empty();
    }

    @Override
    public String queueEmail(String tenantId, String to, String subject, String body,
                             String source, String sourceId) {
        String id = UUID.randomUUID().toString();
        log.info("[NOOP] EmailService.queueEmail: id={}, tenant={}, to={}, subject='{}', source={}",
            id, tenantId, to, subject, source);
        return id;
    }
}
