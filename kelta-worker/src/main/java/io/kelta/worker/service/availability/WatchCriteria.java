package io.kelta.worker.service.availability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The match predicate stored on {@code watch.criteria}.
 *
 * <p>Shape: {@code {v, dateStart, dateEnd, quantity?, minDuration?}}. Dates are
 * plain calendar dates (ISO {@code yyyy-MM-dd}), not instants — a member watching
 * "August 14–16" means those days wherever the campsite is, and converting to an
 * instant would silently shift the window by the timezone offset.
 *
 * <p><b>Why this is versioned.</b> The slice-4 matcher pushes the date-range
 * overlap into SQL, so the stored shape is effectively part of a query plan. A
 * silent change to the field names would not fail loudly — it would just stop
 * matching, and members would quietly receive nothing. {@code v} lets a future
 * shape be detected and migrated instead of misread.
 *
 * <p>Parsing is lenient in one specific direction: unknown keys are ignored (so a
 * newer client can add fields without breaking older pods), but malformed
 * <em>known</em> keys are reported as validation errors rather than silently
 * dropped — a watch that looks saved but never matches is the worst outcome.
 */
public record WatchCriteria(
        int version,
        LocalDate dateStart,
        LocalDate dateEnd,
        Integer quantity,
        Integer minDuration) {

    private static final Logger log = LoggerFactory.getLogger(WatchCriteria.class);

    /** Current shape version, written into every criteria object the platform stores. */
    public static final int CURRENT_VERSION = 1;

    /** An empty criteria: matches any slot on the target. */
    public static final WatchCriteria ANY =
            new WatchCriteria(CURRENT_VERSION, null, null, null, null);

    /** Outcome of parsing tenant/member-supplied criteria JSON. */
    public record ParseResult(WatchCriteria criteria, List<String> errors) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /**
     * Parses criteria JSON, collecting every problem rather than failing on the
     * first — a member fixing one field at a time is a poor experience.
     *
     * <p>Null or blank input is valid and yields {@link #ANY}: a watch with no
     * criteria legitimately means "tell me about anything on this target".
     */
    public static ParseResult parse(String json, ObjectMapper objectMapper) {
        List<String> errors = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return new ParseResult(ANY, errors);
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (RuntimeException e) {
            errors.add("criteria is not valid JSON");
            return new ParseResult(ANY, errors);
        }
        // Legacy rows carry the object as a JSON string (see the storage note in
        // WatchController) — unwrap once so their date windows still match. Only
        // when an object actually falls out: a plain string stays the malformed
        // input it always was.
        if (node.isTextual()) {
            try {
                JsonNode unwrapped = objectMapper.readTree(node.stringValue());
                if (unwrapped.isObject()) {
                    node = unwrapped;
                }
            } catch (RuntimeException e) {
                // Not JSON inside — leave it textual for the object check below.
            }
        }
        if (!node.isObject()) {
            errors.add("criteria must be a JSON object");
            return new ParseResult(ANY, errors);
        }

        int version = node.has("v") && node.get("v").isIntegralNumber()
                ? node.get("v").intValue()
                : CURRENT_VERSION;
        if (version > CURRENT_VERSION) {
            // Refuse rather than misread: a newer shape parsed with older rules
            // would produce a watch that silently never matches.
            errors.add("criteria version " + version + " is newer than this platform supports ("
                    + CURRENT_VERSION + ")");
            return new ParseResult(ANY, errors);
        }

        LocalDate start = date(node, "dateStart", errors);
        LocalDate end = date(node, "dateEnd", errors);
        if (start != null && end != null && end.isBefore(start)) {
            errors.add("dateEnd must not be before dateStart");
        }

        Integer quantity = positiveInt(node, "quantity", errors);
        Integer minDuration = positiveInt(node, "minDuration", errors);

        return new ParseResult(
                new WatchCriteria(version, start, end, quantity, minDuration), errors);
    }

    private static LocalDate date(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            errors.add(field + " must be an ISO date string (yyyy-MM-dd)");
            return null;
        }
        try {
            return LocalDate.parse(value.stringValue());
        } catch (DateTimeException e) {
            errors.add(field + " is not a valid ISO date (yyyy-MM-dd)");
            return null;
        }
    }

    private static Integer positiveInt(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber()) {
            errors.add(field + " must be a whole number");
            return null;
        }
        int parsed = value.intValue();
        if (parsed < 1) {
            errors.add(field + " must be at least 1");
            return null;
        }
        return parsed;
    }

    /**
     * True when a slot window overlaps this watch's date range.
     *
     * <p>Half-open at neither end: a slot on exactly {@code dateStart} or
     * {@code dateEnd} matches, because a member asking for "the 14th to the 16th"
     * means those days inclusive. An absent bound is open-ended.
     *
     * <p>The matcher pushes this same comparison into SQL; this method is the
     * in-Java reference for it, and the two must agree.
     */
    public boolean overlaps(LocalDate slotStart, LocalDate slotEnd) {
        LocalDate effectiveStart = slotStart != null ? slotStart : slotEnd;
        LocalDate effectiveEnd = slotEnd != null ? slotEnd : slotStart;
        if (effectiveStart == null || effectiveEnd == null) {
            // A slot with no dates cannot be excluded by a date filter.
            return true;
        }
        if (dateStart != null && effectiveEnd.isBefore(dateStart)) {
            return false;
        }
        return dateEnd == null || !effectiveStart.isAfter(dateEnd);
    }

    /** True when a slot offering {@code available} units satisfies this watch. */
    public boolean satisfiesQuantity(Integer available) {
        if (quantity == null || available == null) {
            return true;
        }
        return available >= quantity;
    }

    /** Serializes back to the stored shape, always stamping the current version. */
    public String toJson(ObjectMapper objectMapper) {
        var node = objectMapper.createObjectNode();
        node.put("v", CURRENT_VERSION);
        if (dateStart != null) {
            node.put("dateStart", dateStart.toString());
        }
        if (dateEnd != null) {
            node.put("dateEnd", dateEnd.toString());
        }
        if (quantity != null) {
            node.put("quantity", quantity);
        }
        if (minDuration != null) {
            node.put("minDuration", minDuration);
        }
        return node.toString();
    }
}
