package com.emf.runtime.module.integration.handlers;

import com.emf.runtime.module.integration.spi.EmailService;
import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Action handler that sends an email notification.
 *
 * <p>Config format:
 * <pre>
 * {
 *   "to": "recipient@example.com",
 *   "subject": "Order Confirmed",
 *   "body": "Your order has been confirmed.",
 *   "templateId": "optional-template-id"
 * }
 * </pre>
 *
 * <p>When a {@code templateId} is provided, the handler loads the email template
 * and uses its subject/body. Otherwise, the subject/body from the config are used.
 *
 * <p>Uses {@link EmailService} SPI to look up templates and queue emails.
 *
 * @since 1.0.0
 */
public class EmailAlertActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertActionHandler.class);

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public EmailAlertActionHandler(ObjectMapper objectMapper, EmailService emailService) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    @Override
    public String getActionTypeKey() {
        return "EMAIL_ALERT";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String to = (String) config.get("to");
            String subject;
            String body;

            // Load template if specified
            String templateId = (String) config.get("templateId");
            if (templateId != null && !templateId.isBlank()) {
                Optional<EmailService.EmailTemplate> templateOpt = emailService.getTemplate(templateId);
                if (templateOpt.isPresent()) {
                    subject = templateOpt.get().subject();
                    body = templateOpt.get().bodyHtml();
                } else {
                    log.warn("Email template not found: {}, falling back to config", templateId);
                    subject = (String) config.get("subject");
                    body = (String) config.get("body");
                }
            } else {
                subject = (String) config.get("subject");
                body = (String) config.get("body");
            }

            if (to == null || to.isBlank()) {
                return ActionResult.failure("Email 'to' address is required");
            }

            if (subject == null || subject.isBlank()) {
                return ActionResult.failure("Email 'subject' is required");
            }

            String emailLogId = emailService.queueEmail(
                context.tenantId(), to, subject, body != null ? body : "",
                "WORKFLOW", context.workflowRuleId()
            );

            log.info("Email alert queued: to={}, subject='{}', workflowRule={}",
                to, subject, context.workflowRuleId());

            return ActionResult.success(Map.of(
                "emailLogId", emailLogId,
                "to", to,
                "subject", subject,
                "status", "QUEUED"
            ));
        } catch (Exception e) {
            log.error("Failed to execute email alert action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("to") == null) {
                throw new IllegalArgumentException("Config must contain 'to' email address");
            }

            String templateId = (String) config.get("templateId");
            if (templateId == null || templateId.isBlank()) {
                if (config.get("subject") == null) {
                    throw new IllegalArgumentException(
                        "Config must contain 'subject' when no templateId is provided");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
