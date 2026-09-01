package io.kelta.worker.service.mailbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether a matched template may be sent without a human reading it.
 *
 * <p>Every check here is a veto, and any one of them is decisive. That shape is deliberate: the
 * question is not "is this probably fine" but "is there any reason it might not be", and a scoring
 * model that could be outvoted would eventually let something through on aggregate confidence.
 *
 * <p>The failure this guards against is not an unhelpful answer. It is sending a stranger — who
 * chose their own From address — an unreviewed message from an authenticated domain.
 *
 * @since 1.0.0
 */
@Service
public class SupportAutoReplyPolicy {

    private static final Logger log = LoggerFactory.getLogger(SupportAutoReplyPolicy.class);

    /**
     * Bodies longer than this are never auto-answered.
     *
     * <p>Not a performance limit. A long message is a person explaining something specific, and a
     * canned answer to it reads as not having been read — which is worse than a slower human reply.
     */
    private static final int MAX_AUTO_REPLY_BODY_CHARS = 4_000;

    private static final Set<String> DEFAULT_BLOCKED_CATEGORIES = Set.of(
            "billing", "refund", "cancellation", "legal", "security", "complaint", "account_access");

    private final ObjectMapper objectMapper;

    public SupportAutoReplyPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Why an auto-reply was withheld. Recorded verbatim so the shadow report is readable. */
    public enum Veto {
        DISABLED_ON_MAILBOX,
        NO_TEMPLATE_MATCH,
        TEMPLATE_NOT_ELIGIBLE,
        LOW_CONFIDENCE,
        AMBIGUOUS_MATCH,
        BLOCKED_CATEGORY,
        DMARC_NOT_PASSED,
        UNVERIFIED_SENDER,
        DISCLOSES_ACCOUNT_DATA,
        NOT_FIRST_CONTACT,
        HAS_ATTACHMENTS,
        BODY_TOO_LONG,
        AUTOMATED_OR_BULK,
        THREAD_LIMIT_REACHED,
        DAILY_BUDGET_EXHAUSTED
    }

    /** Everything the decision depends on, gathered by the caller. */
    public record Context(
            Map<String, Object> mailbox,
            Map<String, Object> thread,
            Map<String, Object> message,
            int attachmentCount,
            int inboundMessageCount,
            int sentToday) {
    }

    /**
     * Returns the veto, or empty when the reply may be sent.
     *
     * <p>Order matters only for the readability of the reason recorded; the outcome is the same
     * whichever fires first. Cheapest and most categorical checks come first so the common "not
     * switched on" case does not read like a near miss.
     */
    public Optional<Veto> evaluate(Context ctx, MailboxTemplateMatcher.Match match) {
        Map<String, Object> mailbox = ctx.mailbox();

        if (!Boolean.TRUE.equals(mailbox.get("auto_reply_enabled"))) {
            return Optional.of(Veto.DISABLED_ON_MAILBOX);
        }
        if (match == null) {
            return Optional.of(Veto.NO_TEMPLATE_MATCH);
        }
        if (!match.autoSendEligible()) {
            return Optional.of(Veto.TEMPLATE_NOT_ELIGIBLE);
        }
        if (!match.confident()) {
            return Optional.of(Veto.LOW_CONFIDENCE);
        }
        // Two templates fitting equally well means the message is about both, or neither.
        if (match.ambiguous()) {
            return Optional.of(Veto.AMBIGUOUS_MATCH);
        }

        if (blockedCategories(mailbox).contains(lower(match.category()))) {
            // Money, access and legal questions get a person. A correct canned answer to a refund
            // request is still the wrong thing to have sent.
            return Optional.of(Veto.BLOCKED_CATEGORY);
        }

        // DMARC proves the sending domain, which is the weakest claim worth acting on. It does not
        // prove the person, which is why account-specific copy needs more than this.
        String dmarc = str(ctx.message().get("dmarc_result"));
        if (dmarc == null || !"pass".equalsIgnoreCase(dmarc)) {
            return Optional.of(Veto.DMARC_NOT_PASSED);
        }

        if (match.requiresVerifiedSender()
                && !Boolean.TRUE.equals(ctx.thread().get("requester_verified"))) {
            return Optional.of(Veto.UNVERIFIED_SENDER);
        }
        if (match.disclosesAccountData()
                && !Boolean.TRUE.equals(ctx.thread().get("requester_verified"))) {
            // Belt and braces with the author-time guard: that one refuses to mark such a template
            // eligible, this one refuses to send it even if the flag were set another way.
            return Optional.of(Veto.DISCLOSES_ACCOUNT_DATA);
        }

        if (Boolean.TRUE.equals(ctx.message().get("is_bulk"))
                || Boolean.TRUE.equals(ctx.message().get("is_bounce"))
                || isAutomated(str(ctx.message().get("auto_submitted")))) {
            return Optional.of(Veto.AUTOMATED_OR_BULK);
        }

        // Only ever the opening message. A follow-up means the first answer did not land, and
        // sending the same canned text again is how a customer concludes nobody is reading.
        if (ctx.inboundMessageCount() > 1) {
            return Optional.of(Veto.NOT_FIRST_CONTACT);
        }
        if (ctx.attachmentCount() > 0) {
            // An attachment is evidence the sender wanted looked at.
            return Optional.of(Veto.HAS_ATTACHMENTS);
        }

        String body = str(ctx.message().get("body_text"));
        if (body != null && body.length() > MAX_AUTO_REPLY_BODY_CHARS) {
            return Optional.of(Veto.BODY_TOO_LONG);
        }

        int threadCount = intOf(ctx.thread().get("auto_reply_count"), 0);
        int threadMax = intOf(mailbox.get("max_auto_replies_per_thread"), 2);
        if (threadCount >= threadMax) {
            return Optional.of(Veto.THREAD_LIMIT_REACHED);
        }

        int dailyMax = intOf(mailbox.get("max_auto_replies_per_day"), 200);
        if (ctx.sentToday() >= dailyMax) {
            // A blast of auto-replies is how a sending domain gets blocklisted, so the budget is a
            // hard stop rather than a warning.
            return Optional.of(Veto.DAILY_BUDGET_EXHAUSTED);
        }

        return Optional.empty();
    }

    private static boolean isAutomated(String autoSubmitted) {
        // RFC 3834 uses "no" for ordinary human mail; anything else means a machine sent it.
        return autoSubmitted != null && !autoSubmitted.isBlank()
                && !"no".equalsIgnoreCase(autoSubmitted.trim());
    }

    @SuppressWarnings("unchecked")
    private Set<String> blockedCategories(Map<String, Object> mailbox) {
        Object raw = mailbox.get("auto_reply_blocked_categories");
        if (raw == null) {
            return DEFAULT_BLOCKED_CATEGORIES;
        }
        try {
            List<Object> parsed = objectMapper.readValue(raw.toString(), List.class);
            Set<String> out = new java.util.HashSet<>();
            for (Object o : parsed) {
                if (o != null) {
                    out.add(lower(o.toString()));
                }
            }
            return out.isEmpty() ? DEFAULT_BLOCKED_CATEGORIES : out;
        } catch (Exception e) {
            // A malformed list falls back to the built-in denylist rather than to "nothing is
            // blocked" — a parse error must not widen what may be auto-sent.
            log.warn("Unreadable auto_reply_blocked_categories — falling back to the defaults");
            return DEFAULT_BLOCKED_CATEGORIES;
        }
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT).trim();
    }

    private static String str(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private static int intOf(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }
}
