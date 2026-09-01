package io.kelta.worker.service.mailbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The boundary that decides whether a stranger gets an unreviewed message from our domain.
 *
 * <p>Every check is a veto and any one is decisive. These tests assert each in isolation, because
 * the failure mode of a scoring model — something slipping through on aggregate confidence — is
 * exactly what the veto shape exists to prevent.
 */
@DisplayName("SupportAutoReplyPolicy")
class SupportAutoReplyPolicyTest {

    private final SupportAutoReplyPolicy policy =
            new SupportAutoReplyPolicy(JsonMapper.builder().build());

    private Map<String, Object> mailbox() {
        Map<String, Object> m = new HashMap<>();
        m.put("auto_reply_enabled", true);
        m.put("max_auto_replies_per_thread", 2);
        m.put("max_auto_replies_per_day", 200);
        m.put("auto_reply_blocked_categories", "[\"billing\",\"refund\"]");
        return m;
    }

    private Map<String, Object> thread() {
        Map<String, Object> t = new HashMap<>();
        t.put("auto_reply_count", 0);
        t.put("requester_verified", false);
        return t;
    }

    private Map<String, Object> message() {
        Map<String, Object> m = new HashMap<>();
        m.put("dmarc_result", "PASS");
        m.put("body_text", "How does this work?");
        m.put("is_bulk", false);
        m.put("is_bounce", false);
        return m;
    }

    private MailboxTemplateMatcher.Match match() {
        return new MailboxTemplateMatcher.Match("tpl-1", "how_it_works", "support.how_it_works",
                0.95, true, 0.9, false, false, false);
    }

    private SupportAutoReplyPolicy.Context ctx() {
        return new SupportAutoReplyPolicy.Context(mailbox(), thread(), message(), 0, 1, 0);
    }

    private Optional<SupportAutoReplyPolicy.Veto> evaluate(SupportAutoReplyPolicy.Context ctx) {
        return policy.evaluate(ctx, match());
    }

    @Test
    @DisplayName("Allows a confident, eligible, first-contact match from a DMARC-passing sender")
    void allowsTheHappyPath() {
        assertThat(evaluate(ctx())).isEmpty();
    }

    @Test
    @DisplayName("The mailbox kill switch vetoes everything")
    void killSwitchVetoes() {
        Map<String, Object> mb = mailbox();
        mb.put("auto_reply_enabled", false);

        assertThat(policy.evaluate(
                new SupportAutoReplyPolicy.Context(mb, thread(), message(), 0, 1, 0), match()))
                .contains(SupportAutoReplyPolicy.Veto.DISABLED_ON_MAILBOX);
    }

    @Test
    @DisplayName("No match, an ineligible template, or low confidence each veto")
    void matchQualityVetoes() {
        assertThat(policy.evaluate(ctx(), null))
                .contains(SupportAutoReplyPolicy.Veto.NO_TEMPLATE_MATCH);

        MailboxTemplateMatcher.Match ineligible = new MailboxTemplateMatcher.Match(
                "t", "c", "k", 0.99, false, 0.9, false, false, false);
        assertThat(policy.evaluate(ctx(), ineligible))
                .contains(SupportAutoReplyPolicy.Veto.TEMPLATE_NOT_ELIGIBLE);

        MailboxTemplateMatcher.Match weak = new MailboxTemplateMatcher.Match(
                "t", "c", "k", 0.40, true, 0.9, false, false, false);
        assertThat(policy.evaluate(ctx(), weak))
                .contains(SupportAutoReplyPolicy.Veto.LOW_CONFIDENCE);
    }

    @Test
    @DisplayName("An ambiguous match vetoes — a coin-flip answer is worse than a human")
    void ambiguousVetoes() {
        MailboxTemplateMatcher.Match ambiguous = new MailboxTemplateMatcher.Match(
                "t", "c", "k", 0.99, true, 0.9, false, false, true);

        assertThat(policy.evaluate(ctx(), ambiguous))
                .contains(SupportAutoReplyPolicy.Veto.AMBIGUOUS_MATCH);
    }

    @Test
    @DisplayName("Money, access and legal categories are never auto-answered")
    void blockedCategoryVetoes() {
        MailboxTemplateMatcher.Match billing = new MailboxTemplateMatcher.Match(
                "t", "billing", "k", 0.99, true, 0.9, false, false, false);

        // A correct canned answer to a refund request is still the wrong thing to have sent.
        assertThat(policy.evaluate(ctx(), billing))
                .contains(SupportAutoReplyPolicy.Veto.BLOCKED_CATEGORY);
    }

    @Test
    @DisplayName("A malformed blocked-category list falls back to the defaults, never to none")
    void malformedBlocklistFallsBackClosed() {
        Map<String, Object> mb = mailbox();
        mb.put("auto_reply_blocked_categories", "not json");
        MailboxTemplateMatcher.Match security = new MailboxTemplateMatcher.Match(
                "t", "security", "k", 0.99, true, 0.9, false, false, false);

        // A parse error must not widen what may be auto-sent.
        assertThat(policy.evaluate(
                new SupportAutoReplyPolicy.Context(mb, thread(), message(), 0, 1, 0), security))
                .contains(SupportAutoReplyPolicy.Veto.BLOCKED_CATEGORY);
    }

    @Test
    @DisplayName("Anything short of a DMARC pass vetoes")
    void dmarcVetoes() {
        for (Object verdict : new Object[] {"FAIL", "NONE", null}) {
            Map<String, Object> msg = message();
            msg.put("dmarc_result", verdict);
            assertThat(policy.evaluate(
                    new SupportAutoReplyPolicy.Context(mailbox(), thread(), msg, 0, 1, 0), match()))
                    .as("dmarc=%s", verdict)
                    .contains(SupportAutoReplyPolicy.Veto.DMARC_NOT_PASSED);
        }
    }

    @Test
    @DisplayName("Account-specific copy needs a verified sender, not merely a verified domain")
    void accountDataNeedsVerifiedSender() {
        MailboxTemplateMatcher.Match discloses = new MailboxTemplateMatcher.Match(
                "t", "c", "k", 0.99, true, 0.9, false, true, false);

        // DMARC passes in this context, and it is still not enough: the domain is proven, the
        // person is not.
        assertThat(policy.evaluate(ctx(), discloses))
                .contains(SupportAutoReplyPolicy.Veto.DISCLOSES_ACCOUNT_DATA);
    }

    @Test
    @DisplayName("Only first contact is auto-answered")
    void followUpVetoes() {
        // A follow-up means the first answer did not land; sending the same text again is how a
        // customer concludes nobody is reading.
        assertThat(policy.evaluate(
                new SupportAutoReplyPolicy.Context(mailbox(), thread(), message(), 0, 2, 0), match()))
                .contains(SupportAutoReplyPolicy.Veto.NOT_FIRST_CONTACT);
    }

    @Test
    @DisplayName("An attachment means someone wanted it looked at")
    void attachmentVetoes() {
        assertThat(policy.evaluate(
                new SupportAutoReplyPolicy.Context(mailbox(), thread(), message(), 1, 1, 0), match()))
                .contains(SupportAutoReplyPolicy.Veto.HAS_ATTACHMENTS);
    }

    @Test
    @DisplayName("A long message is a person explaining something specific")
    void longBodyVetoes() {
        Map<String, Object> msg = message();
        msg.put("body_text", "x".repeat(4_001));

        assertThat(policy.evaluate(
                new SupportAutoReplyPolicy.Context(mailbox(), thread(), msg, 0, 1, 0), match()))
                .contains(SupportAutoReplyPolicy.Veto.BODY_TOO_LONG);
    }

    @Test
    @DisplayName("Bulk, bounce and auto-submitted mail is never answered")
    void automatedMailVetoes() {
        for (String key : new String[] {"is_bulk", "is_bounce"}) {
            Map<String, Object> msg = message();
            msg.put(key, true);
            assertThat(policy.evaluate(
                    new SupportAutoReplyPolicy.Context(mailbox(), thread(), msg, 0, 1, 0), match()))
                    .as(key)
                    .contains(SupportAutoReplyPolicy.Veto.AUTOMATED_OR_BULK);
        }

        Map<String, Object> auto = message();
        auto.put("auto_submitted", "auto-replied");
        assertThat(policy.evaluate(
                new SupportAutoReplyPolicy.Context(mailbox(), thread(), auto, 0, 1, 0), match()))
                .contains(SupportAutoReplyPolicy.Veto.AUTOMATED_OR_BULK);
    }

    @Test
    @DisplayName("Auto-Submitted: no is ordinary human mail and does not veto")
    void autoSubmittedNoIsFine() {
        Map<String, Object> msg = message();
        msg.put("auto_submitted", "no");

        assertThat(policy.evaluate(
                new SupportAutoReplyPolicy.Context(mailbox(), thread(), msg, 0, 1, 0), match()))
                .isEmpty();
    }

    @Test
    @DisplayName("Per-thread and per-day limits both veto")
    void limitsVeto() {
        Map<String, Object> busyThread = thread();
        busyThread.put("auto_reply_count", 2);
        assertThat(policy.evaluate(
                new SupportAutoReplyPolicy.Context(mailbox(), busyThread, message(), 0, 1, 0), match()))
                .contains(SupportAutoReplyPolicy.Veto.THREAD_LIMIT_REACHED);

        // A blast of auto-replies is how a sending domain gets blocklisted.
        assertThat(policy.evaluate(
                new SupportAutoReplyPolicy.Context(mailbox(), thread(), message(), 0, 1, 200), match()))
                .contains(SupportAutoReplyPolicy.Veto.DAILY_BUDGET_EXHAUSTED);
    }
}
