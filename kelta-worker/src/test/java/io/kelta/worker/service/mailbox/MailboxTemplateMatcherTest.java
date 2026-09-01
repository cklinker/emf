package io.kelta.worker.service.mailbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The deterministic matcher.
 *
 * <p>A model never picks the template. If it did, "template-matched auto-send" would quietly
 * become "model-controlled send", and the human-approval requirement the design rests on would be
 * void.
 */
@DisplayName("MailboxTemplateMatcher")
class MailboxTemplateMatcherTest {

    private static final String TENANT = "t1";
    private static final String MAILBOX = "mb1";

    private JdbcTemplate jdbcTemplate;
    private MailboxTemplateMatcher matcher;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        matcher = new MailboxTemplateMatcher(jdbcTemplate, JsonMapper.builder().build());
    }

    private Map<String, Object> template(String category, String keywords, String excludes,
                                         boolean autoSend, double minConfidence) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", "tpl-" + category);
        t.put("category", category);
        t.put("template_key", "support." + category);
        t.put("match_keywords", keywords);
        t.put("exclude_keywords", excludes);
        t.put("auto_send_eligible", autoSend);
        t.put("min_confidence", minConfidence);
        t.put("requires_verified_sender", false);
        t.put("discloses_account_data", false);
        return t;
    }

    private void templates(Map<String, Object>... rows) {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("Matches on keywords and scores by the share found")
    void matchesOnKeywords() {
        templates(template("magic_link", "[\"magic link\",\"log in\"]", "[]", true, 0.5));

        Optional<MailboxTemplateMatcher.Match> match =
                matcher.match(TENANT, MAILBOX, "Cannot log in", "I never got the magic link.");

        assertThat(match).isPresent();
        assertThat(match.get().category()).isEqualTo("magic_link");
        // Both keywords present out of two.
        assertThat(match.get().confidence()).isEqualTo(1.0);
        assertThat(match.get().confident()).isTrue();
    }

    @Test
    @DisplayName("Partial keyword coverage yields partial confidence")
    void scoresPartialMatches() {
        templates(template("magic_link", "[\"magic link\",\"log in\",\"password\"]", "[]", true, 0.9));

        MailboxTemplateMatcher.Match match =
                matcher.match(TENANT, MAILBOX, "help", "the magic link never arrived").orElseThrow();

        assertThat(match.confidence()).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(0.001));
        // A template that asks for more evidence needs more of it before it will auto-send.
        assertThat(match.confident()).isFalse();
    }

    @Test
    @DisplayName("An exclude keyword vetoes the template outright")
    void excludeKeywordVetoes() {
        // "Refund" on a how-does-it-work answer means the message is not what the template is for,
        // however many other words happen to line up.
        templates(template("how_it_works", "[\"how does\",\"work\"]", "[\"refund\"]", true, 0.5));

        Optional<MailboxTemplateMatcher.Match> match = matcher.match(
                TENANT, MAILBOX, "How does this work?", "Also I want a refund please.");

        assertThat(match).isEmpty();
    }

    @Test
    @DisplayName("Two templates scoring comparably are flagged ambiguous")
    void flagsAmbiguousMatches() {
        // Answering with a coin-flip is worse than routing to a person.
        templates(
                template("billing", "[\"invoice\"]", "[]", true, 0.5),
                template("cancel", "[\"cancel\"]", "[]", true, 0.5));

        MailboxTemplateMatcher.Match match = matcher
                .match(TENANT, MAILBOX, "invoice", "I want to cancel and query an invoice")
                .orElseThrow();

        assertThat(match.ambiguous()).isTrue();
    }

    @Test
    @DisplayName("A clear winner is not ambiguous")
    void clearWinnerIsNotAmbiguous() {
        templates(
                template("billing", "[\"invoice\",\"charge\",\"payment\"]", "[]", true, 0.5),
                template("cancel", "[\"cancel\",\"delete account\",\"close\"]", "[]", true, 0.5));

        MailboxTemplateMatcher.Match match = matcher
                .match(TENANT, MAILBOX, "invoice", "invoice charge payment question")
                .orElseThrow();

        assertThat(match.category()).isEqualTo("billing");
        assertThat(match.ambiguous()).isFalse();
    }

    @Test
    @DisplayName("Matching is case-insensitive")
    void caseInsensitive() {
        templates(template("magic_link", "[\"magic link\"]", "[]", true, 0.5));

        assertThat(matcher.match(TENANT, MAILBOX, "HELP", "My MAGIC LINK is broken")).isPresent();
    }

    @Test
    @DisplayName("No keywords means the template is never selected")
    void noKeywordsNeverMatches() {
        templates(template("empty", "[]", "[]", true, 0.5));

        assertThat(matcher.match(TENANT, MAILBOX, "anything", "at all")).isEmpty();
    }

    @Test
    @DisplayName("A malformed keyword list matches nothing rather than everything")
    void malformedKeywordsMatchNothing() {
        // The safe direction: a broken template is simply never selected.
        templates(template("broken", "not json", "[]", true, 0.5));

        assertThat(matcher.match(TENANT, MAILBOX, "anything", "at all")).isEmpty();
    }

    @Test
    @DisplayName("Empty message text matches nothing")
    void emptyTextMatchesNothing() {
        templates(template("magic_link", "[\"magic link\"]", "[]", true, 0.5));

        assertThat(matcher.match(TENANT, MAILBOX, null, null)).isEmpty();
        assertThat(matcher.match(TENANT, MAILBOX, "  ", "  ")).isEmpty();
    }

    @Test
    @DisplayName("Policy flags travel with the match for the send guard to read")
    void carriesPolicyFlags() {
        Map<String, Object> t = template("billing", "[\"invoice\"]", "[]", false, 0.95);
        t.put("requires_verified_sender", true);
        t.put("discloses_account_data", true);
        templates(t);

        MailboxTemplateMatcher.Match match =
                matcher.match(TENANT, MAILBOX, "invoice", "about my invoice").orElseThrow();

        assertThat(match.autoSendEligible()).isFalse();
        assertThat(match.requiresVerifiedSender()).isTrue();
        assertThat(match.disclosesAccountData()).isTrue();
        assertThat(match.minConfidence()).isEqualTo(0.95);
    }
}
