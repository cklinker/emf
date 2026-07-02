package io.kelta.runtime.module.integration.spi;

import java.util.Map;
import java.util.Optional;

/**
 * SPI interface for email operations used by the EmailAlertActionHandler and
 * platform lifecycle code (user invite, password reset, MFA notifications, etc.).
 *
 * <p>Implementations provide template lookup, variable substitution, and email
 * queuing. The host application provides the real implementation backed by a
 * database and mail sender. A no-op logging implementation is provided for testing.
 *
 * @since 1.0.0
 */
public interface EmailService {

    /**
     * Retrieves an email template by ID.
     *
     * @param templateId the template ID
     * @return the template, or empty if not found
     */
    Optional<EmailTemplate> getTemplate(String templateId);

    /**
     * Queues an email for delivery.
     *
     * @param tenantId the tenant ID
     * @param to the recipient email address
     * @param subject the email subject
     * @param body the email body (HTML)
     * @param source the source of the email (e.g., "WORKFLOW")
     * @param sourceId the source ID (e.g., workflow rule ID)
     * @return the generated email log ID
     */
    String queueEmail(String tenantId, String to, String subject, String body,
                      String source, String sourceId);

    /**
     * Looks up a template by stable {@code templateKey} (tenant override or
     * platform default), substitutes {@code ${var}} placeholders from {@code vars},
     * and queues the result.
     *
     * @param tenantId    owning tenant
     * @param to          recipient email address
     * @param templateKey stable key (e.g. "user.invite")
     * @param vars        variables for {@code ${name}} substitution in subject + body
     * @param source      source tag for {@code email_log} (e.g. "USER_INVITE")
     * @param sourceId    optional source row id (e.g. user id)
     * @return the generated email log id; empty if the template is missing
     */
    Optional<String> sendByKey(String tenantId, String to, String templateKey,
                               Map<String, Object> vars, String source, String sourceId);

    /**
     * Looks up a template by its {@code name} column (tenant override or
     * platform default seeded under the {@code system} tenant), substitutes
     * {@code ${var}} placeholders from {@code vars}, and queues the result.
     *
     * <p>Use this when callers prefer the short-name convention
     * (e.g. {@code user_invite}, {@code welcome}) over the dotted
     * {@code templateKey} form.
     *
     * @param tenantId owning tenant
     * @param to       recipient email address
     * @param name     template name (e.g. "user_invite")
     * @param vars     variables for {@code ${name}} substitution in subject + body
     * @param source   source tag for {@code email_log}
     * @param sourceId optional source row id
     * @return the generated email log id; empty if the template is missing
     */
    Optional<String> sendByName(String tenantId, String to, String name,
                                Map<String, Object> vars, String source, String sourceId);

    /**
     * Looks up a template by its {@code id} scoped to the owning tenant (admin-authored
     * templates only — no {@code system} fallback), substitutes {@code ${var}} placeholders
     * from {@code vars}, and queues the result.
     *
     * <p>Used by the transactional-send endpoint ({@code POST /api/email/send}) where the
     * caller selects a concrete tenant template by id. Restricting resolution to the tenant's
     * own templates keeps the endpoint from being used to send arbitrary system templates.
     *
     * @param tenantId   owning tenant
     * @param to         recipient email address
     * @param templateId the template id (must belong to {@code tenantId} and be active)
     * @param vars       variables for {@code ${name}} substitution in subject + body
     * @param source     source tag for {@code email_log}
     * @param sourceId   optional source row id
     * @return the generated email log id; empty if the template is missing
     */
    Optional<String> sendById(String tenantId, String to, String templateId,
                              Map<String, Object> vars, String source, String sourceId);

    /**
     * Email template data.
     *
     * @param subject the template subject
     * @param bodyHtml the template HTML body
     */
    record EmailTemplate(String subject, String bodyHtml) {}
}
