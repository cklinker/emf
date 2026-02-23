package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.entity.EmailLog;
import com.emf.controlplane.entity.EmailTemplate;
import com.emf.controlplane.repository.EmailLogRepository;
import com.emf.controlplane.service.EmailTemplateService;
import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionHandler;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Action handler that sends an email notification.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "to": "recipient@example.com",
 *   "subject": "Order Confirmed",
 *   "body": "Your order has been confirmed.",
 *   "templateId": "optional-template-id"
 * }
 * </pre>
 * <p>
 * When a {@code templateId} is provided, the handler loads the email template
 * and uses its subject/body. Otherwise, the subject/body from the config are used.
 * <p>
 * Creates an {@link EmailLog} entry with QUEUED status for async delivery.
 */
@Component
public class EmailAlertActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertActionHandler.class);

    private final ObjectMapper objectMapper;
    private final EmailTemplateService emailTemplateService;
    private final EmailLogRepository emailLogRepository;

    public EmailAlertActionHandler(ObjectMapper objectMapper,
                                   EmailTemplateService emailTemplateService,
                                   EmailLogRepository emailLogRepository) {
        this.objectMapper = objectMapper;
        this.emailTemplateService = emailTemplateService;
        this.emailLogRepository = emailLogRepository;
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
                try {
                    EmailTemplate template = emailTemplateService.getTemplate(templateId);
                    subject = template.getSubject();
                    body = template.getBodyHtml();
                } catch (Exception e) {
                    log.warn("Failed to load email template {}: {}", templateId, e.getMessage());
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

            // Create email log entry for async delivery
            EmailLog emailLog = new EmailLog();
            emailLog.setTenantId(context.tenantId());
            emailLog.setRecipientEmail(to);
            emailLog.setSubject(subject);
            emailLog.setStatus("QUEUED");
            emailLog.setSource("WORKFLOW");
            emailLog.setSourceId(context.workflowRuleId());
            emailLogRepository.save(emailLog);

            log.info("Email alert queued: to={}, subject={}, workflowRule={}",
                to, subject, context.workflowRuleId());

            return ActionResult.success(Map.of(
                "emailLogId", emailLog.getId(),
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

            String to = (String) config.get("to");
            String templateId = (String) config.get("templateId");

            if ((to == null || to.isBlank())) {
                throw new IllegalArgumentException("Config must contain 'to' email address");
            }

            if (templateId == null || templateId.isBlank()) {
                if (config.get("subject") == null) {
                    throw new IllegalArgumentException("Config must contain 'subject' when no templateId is provided");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
