package io.kelta.worker.service.mailbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Picks the canned answer for an inbound message, deterministically.
 *
 * <p><b>A model never chooses the template.</b> If it did, "template-matched auto-send" would
 * quietly become "model-controlled send", and the human-approval requirement that the whole design
 * rests on would be void. A model's opinion can later be used as a second vote on the same
 * category — agreement raising confidence — but the candidate set and the winner come from here.
 *
 * <p>Matching is keyword scoring rather than regex. An author-supplied regex is an unbounded
 * computation run against attacker-supplied text, which is a denial of service with extra steps.
 *
 * @since 1.0.0
 */
@Service
public class MailboxTemplateMatcher {

    private static final Logger log = LoggerFactory.getLogger(MailboxTemplateMatcher.class);

    /** Beyond this, stop scanning. Scoring is linear, but the body is attacker-sized. */
    private static final int MAX_SCANNED_CHARS = 20_000;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MailboxTemplateMatcher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * One candidate answer.
     *
     * @param confidence 0..1, the share of this template's keywords the message contained
     * @param ambiguous  true when another template scored comparably — see {@link #match}
     */
    public record Match(String templateId, String category, String templateKey,
                        double confidence, boolean autoSendEligible, double minConfidence,
                        boolean requiresVerifiedSender, boolean disclosesAccountData,
                        boolean ambiguous) {

        /** Whether this match clears its own configured bar. Auto-send needs more than this. */
        public boolean confident() {
            return confidence >= minConfidence;
        }
    }

    /**
     * Scores every active template for the mailbox against the message text.
     *
     * <p>Returns the winner, or empty when nothing matched at all.
     *
     * <p>{@code ambiguous} is set when the runner-up scored within a hair of the winner. Two
     * templates fitting equally well means the message is genuinely about both, or about neither —
     * and answering with a coin-flip is worse than routing it to a person.
     */
    public Optional<Match> match(String tenantId, String mailboxId, String subject, String bodyText) {
        String haystack = haystack(subject, bodyText);
        if (haystack.isBlank()) {
            return Optional.empty();
        }

        List<Map<String, Object>> templates = jdbcTemplate.queryForList("""
                SELECT * FROM mailbox_template
                 WHERE tenant_id = ? AND mailbox_id = ? AND active = true
                 ORDER BY priority, category
                """, tenantId, mailboxId);

        List<Match> scored = new ArrayList<>();
        for (Map<String, Object> template : templates) {
            score(template, haystack).ifPresent(scored::add);
        }
        if (scored.isEmpty()) {
            return Optional.empty();
        }

        scored.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));
        Match winner = scored.getFirst();

        boolean ambiguous = scored.size() > 1
                && scored.get(1).confidence() >= winner.confidence() - 0.05;
        if (ambiguous) {
            log.debug("Template match for mailbox {} is ambiguous: {} and {} scored within 0.05",
                    mailboxId, winner.category(), scored.get(1).category());
        }

        return Optional.of(new Match(winner.templateId(), winner.category(), winner.templateKey(),
                winner.confidence(), winner.autoSendEligible(), winner.minConfidence(),
                winner.requiresVerifiedSender(), winner.disclosesAccountData(), ambiguous));
    }

    private Optional<Match> score(Map<String, Object> template, String haystack) {
        List<String> exclude = terms(template.get("exclude_keywords"));
        for (String term : exclude) {
            if (haystack.contains(term)) {
                // A veto is absolute. "Refund" on a how-does-it-work answer means this message is
                // not what the template is for, however many other words happen to line up.
                return Optional.empty();
            }
        }

        List<String> keywords = terms(template.get("match_keywords"));
        if (keywords.isEmpty()) {
            return Optional.empty();
        }

        long hits = keywords.stream().filter(haystack::contains).count();
        if (hits == 0) {
            return Optional.empty();
        }

        // Share of the template's own keywords present, so a template that asks for more evidence
        // needs more of it. A single-keyword template scoring 1.0 off one common word is exactly
        // why min_confidence is per template rather than global.
        double confidence = (double) hits / keywords.size();

        return Optional.of(new Match(
                (String) template.get("id"),
                (String) template.get("category"),
                (String) template.get("template_key"),
                confidence,
                Boolean.TRUE.equals(template.get("auto_send_eligible")),
                template.get("min_confidence") instanceof Number n ? n.doubleValue() : 0.9,
                Boolean.TRUE.equals(template.get("requires_verified_sender")),
                Boolean.TRUE.equals(template.get("discloses_account_data")),
                false));
    }

    /** Subject and body, lowercased and bounded. */
    private static String haystack(String subject, String bodyText) {
        StringBuilder sb = new StringBuilder();
        if (subject != null) {
            sb.append(subject).append('\n');
        }
        if (bodyText != null) {
            sb.append(bodyText);
        }
        String s = sb.toString().toLowerCase(Locale.ROOT);
        return s.length() > MAX_SCANNED_CHARS ? s.substring(0, MAX_SCANNED_CHARS) : s;
    }

    @SuppressWarnings("unchecked")
    private List<String> terms(Object raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            List<Object> parsed = objectMapper.readValue(raw.toString(), List.class);
            List<String> out = new ArrayList<>();
            for (Object o : parsed) {
                if (o != null && !o.toString().isBlank()) {
                    out.add(o.toString().toLowerCase(Locale.ROOT).trim());
                }
            }
            return out;
        } catch (Exception e) {
            // A malformed keyword list must not match everything. Empty means this template is
            // simply never selected, which is the safe direction.
            log.warn("Unreadable keyword list on a mailbox template — treating as no keywords");
            return List.of();
        }
    }
}
