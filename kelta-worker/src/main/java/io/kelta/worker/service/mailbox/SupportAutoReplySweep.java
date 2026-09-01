package io.kelta.worker.service.mailbox;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.repository.EmailRepository;
import io.kelta.worker.repository.MailboxAutoReplyDecisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Answers the routine share of inbound mail, or records what it would have answered.
 *
 * <p>Runs as a sweep rather than inline in the ingest webhook. Sending mail on the webhook thread
 * would put an SMTP round-trip inside a request SNS retries on timeout, so a slow provider would
 * turn one customer email into several replies.
 *
 * <p><b>Shadow mode is the default and is the point of this slice.</b> With
 * {@code kelta.support.autoreply.shadow-mode=true} the full pipeline runs — match, veto, render —
 * and records the decision without sending. Comparing a fortnight of those decisions against what
 * humans actually sent is the only honest way to find out whether the matcher is good enough to
 * trust. Switching it on without that is guessing with someone else's inbox.
 *
 * @since 1.0.0
 */
@Service
public class SupportAutoReplySweep {

    private static final Logger log = LoggerFactory.getLogger(SupportAutoReplySweep.class);

    private final JdbcTemplate jdbcTemplate;
    private final MailboxAutoReplyDecisionRepository decisionRepository;
    private final MailboxTemplateMatcher matcher;
    private final SupportAutoReplyPolicy policy;
    private final MailboxReplyService replyService;
    private final EmailRepository emailRepository;

    private final boolean enabled;
    private final boolean shadowMode;
    private final int batchSize;

    public SupportAutoReplySweep(JdbcTemplate jdbcTemplate,
                                 MailboxAutoReplyDecisionRepository decisionRepository,
                                 MailboxTemplateMatcher matcher,
                                 SupportAutoReplyPolicy policy,
                                 MailboxReplyService replyService,
                                 EmailRepository emailRepository,
                                 @Value("${kelta.support.autoreply.enabled:true}") boolean enabled,
                                 @Value("${kelta.support.autoreply.shadow-mode:true}") boolean shadowMode,
                                 @Value("${kelta.support.autoreply.batch-size:50}") int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.decisionRepository = decisionRepository;
        this.matcher = matcher;
        this.policy = policy;
        this.replyService = replyService;
        this.emailRepository = emailRepository;
        this.enabled = enabled;
        this.shadowMode = shadowMode;
        this.batchSize = batchSize;

        if (enabled && !shadowMode) {
            // Loud on purpose. This is the one setting in the feature that lets bytes leave the
            // tenant without a human, and it should never be discovered by surprise in a log.
            log.warn("Support auto-reply is LIVE — matched templates will be sent to customers "
                    + "without human review. Set kelta.support.autoreply.shadow-mode=true to "
                    + "record decisions without sending.");
        }
    }

    @Scheduled(fixedDelayString = "${kelta.support.autoreply.poll-interval-ms:60000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        try {
            List<Map<String, Object>> pending = findUndecided();
            for (Map<String, Object> row : pending) {
                String tenantId = (String) row.get("tenant_id");
                try {
                    TenantContext.runWithTenant(tenantId, () -> decide(row));
                } catch (Exception e) {
                    log.warn("Auto-reply decision failed for message {}: {}",
                            row.get("message_id"), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Support auto-reply sweep failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Inbound messages with no decision yet.
     *
     * <p>Cross-tenant and unbound, riding {@code admin_bypass} like the SLA sweep. Only the newest
     * inbound message per thread is considered: an older one that was never decided is a message
     * the customer has already followed up on, and answering it now would be answering the wrong
     * thing.
     */
    private List<Map<String, Object>> findUndecided() {
        return jdbcTemplate.queryForList("""
                SELECT m.id AS message_id, m.tenant_id, m.mailbox_id, m.thread_id,
                       m.body_text, m.subject, m.dmarc_result, m.is_bulk, m.is_bounce,
                       m.auto_submitted
                  FROM mailbox_message m
                  JOIN mailbox_thread t ON t.id = m.thread_id
                 WHERE m.direction = 'INBOUND'
                   AND m.received_at > now() - interval '1 day'
                   AND t.status NOT IN ('RESOLVED','CLOSED','SPAM','ARCHIVED')
                   AND NOT EXISTS (
                        SELECT 1 FROM mailbox_auto_reply_decision d
                         WHERE d.tenant_id = m.tenant_id AND d.message_id = m.id)
                   AND m.received_at = (
                        SELECT max(m2.received_at) FROM mailbox_message m2
                         WHERE m2.thread_id = m.thread_id AND m2.direction = 'INBOUND')
                 ORDER BY m.received_at
                 LIMIT ?
                """, batchSize);
    }

    private void decide(Map<String, Object> row) {
        String tenantId = (String) row.get("tenant_id");
        String mailboxId = (String) row.get("mailbox_id");
        String threadId = (String) row.get("thread_id");
        String messageId = (String) row.get("message_id");

        Map<String, Object> mailbox = one("SELECT * FROM mailbox WHERE id = ? AND tenant_id = ?",
                mailboxId, tenantId);
        Map<String, Object> thread = one("SELECT * FROM mailbox_thread WHERE id = ? AND tenant_id = ?",
                threadId, tenantId);
        if (mailbox == null || thread == null) {
            return;
        }

        MailboxTemplateMatcher.Match match = matcher
                .match(tenantId, mailboxId, (String) row.get("subject"), (String) row.get("body_text"))
                .orElse(null);

        SupportAutoReplyPolicy.Context ctx = new SupportAutoReplyPolicy.Context(
                mailbox, thread, row,
                countAttachments(tenantId, messageId),
                countInbound(tenantId, threadId),
                decisionRepository.countSentToday(tenantId, mailboxId));

        Optional<SupportAutoReplyPolicy.Veto> veto = policy.evaluate(ctx, match);

        if (veto.isPresent()) {
            decisionRepository.record(tenantId, mailboxId, threadId, messageId,
                    "VETOED", veto.get().name(), match, null);
            return;
        }

        if (shadowMode) {
            // Everything above ran. Nothing goes out. This row is the evidence used to decide
            // whether going live is safe.
            decisionRepository.record(tenantId, mailboxId, threadId, messageId,
                    "SHADOW", null, match, null);
            log.info("Auto-reply SHADOW: would have answered thread {} with '{}' (confidence {})",
                    threadId, match.category(), String.format("%.2f", match.confidence()));
            return;
        }

        send(tenantId, mailboxId, threadId, messageId, match);
    }

    private void send(String tenantId, String mailboxId, String threadId, String messageId,
                      MailboxTemplateMatcher.Match match) {
        Optional<Map<String, Object>> template =
                emailRepository.findTemplateByKey(tenantId, match.templateKey());
        if (template.isEmpty()) {
            decisionRepository.record(tenantId, mailboxId, threadId, messageId,
                    "VETOED", "TEMPLATE_MISSING", match, null);
            return;
        }

        // Only ever the rendered template body. There is no parameter on this path through which
        // generated or caller-supplied prose could arrive, which is what makes "auto-send is
        // template-only" a property of the code rather than a convention.
        String body = String.valueOf(template.get().getOrDefault("body_html", ""));

        MailboxReplyService.Result result =
                replyService.reply(tenantId, threadId, body, null, true);

        if (result.sent()) {
            jdbcTemplate.update("""
                    UPDATE mailbox_thread
                       SET auto_reply_count = auto_reply_count + 1, updated_at = now()
                     WHERE id = ? AND tenant_id = ?
                    """, threadId, tenantId);
            decisionRepository.record(tenantId, mailboxId, threadId, messageId,
                    "SENT", null, match, result.messageId());
            log.info("Auto-replied to thread {} with '{}'", threadId, match.category());
        } else {
            // The reply service has its own guards — suppression, bounces, unattended addresses —
            // and its refusal is recorded here so the shadow report shows one decision per message
            // regardless of which layer declined.
            decisionRepository.record(tenantId, mailboxId, threadId, messageId,
                    "VETOED", result.refusal() == null ? "REFUSED" : result.refusal().name(),
                    match, null);
        }
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private int countAttachments(String tenantId, String messageId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mailbox_attachment WHERE tenant_id = ? AND message_id = ?",
                Integer.class, tenantId, messageId);
        return n == null ? 0 : n;
    }

    private int countInbound(String tenantId, String threadId) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM mailbox_message
                 WHERE tenant_id = ? AND thread_id = ? AND direction = 'INBOUND'
                """, Integer.class, tenantId, threadId);
        return n == null ? 0 : n;
    }

    /** Whether this pod is recording decisions rather than sending. Read by the admin API. */
    public boolean isShadowMode() {
        return shadowMode;
    }
}
