package io.kelta.worker.service.availability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WatchCriteria Tests")
class WatchCriteriaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WatchCriteria.ParseResult parse(String json) {
        return WatchCriteria.parse(json, objectMapper);
    }

    @Nested
    @DisplayName("Parsing")
    class Parsing {

        @Test
        @DisplayName("absent criteria means 'anything on this target', not an error")
        void absentMeansAny() {
            for (String input : new String[]{null, "", "   "}) {
                WatchCriteria.ParseResult result = parse(input);
                assertThat(result.isValid()).isTrue();
                assertThat(result.criteria()).isEqualTo(WatchCriteria.ANY);
            }
        }

        @Test
        @DisplayName("parses a full criteria object")
        void parsesFullObject() {
            WatchCriteria.ParseResult result = parse("""
                    {"v":1,"dateStart":"2026-08-14","dateEnd":"2026-08-16",
                     "quantity":2,"minDuration":2}
                    """);

            assertThat(result.isValid()).isTrue();
            WatchCriteria c = result.criteria();
            assertThat(c.dateStart()).isEqualTo(LocalDate.of(2026, 8, 14));
            assertThat(c.dateEnd()).isEqualTo(LocalDate.of(2026, 8, 16));
            assertThat(c.quantity()).isEqualTo(2);
            assertThat(c.minDuration()).isEqualTo(2);
        }

        @Test
        @DisplayName("ignores unknown keys so a newer client does not break an older pod")
        void ignoresUnknownKeys() {
            WatchCriteria.ParseResult result =
                    parse("{\"dateStart\":\"2026-08-14\",\"somethingNew\":true}");

            assertThat(result.isValid()).isTrue();
            assertThat(result.criteria().dateStart()).isEqualTo(LocalDate.of(2026, 8, 14));
        }

        @Test
        @DisplayName("defaults the version when absent")
        void defaultsVersion() {
            assertThat(parse("{\"dateStart\":\"2026-08-14\"}").criteria().version())
                    .isEqualTo(WatchCriteria.CURRENT_VERSION);
        }

        @Test
        @DisplayName("refuses a future version rather than misreading it")
        void refusesFutureVersion() {
            // Parsing a newer shape with older rules would produce a watch that
            // silently never matches — worse than a visible error.
            WatchCriteria.ParseResult result = parse("{\"v\":99,\"dateStart\":\"2026-08-14\"}");

            assertThat(result.isValid()).isFalse();
            assertThat(result.errors().get(0)).contains("newer than this platform supports");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("reports malformed JSON and non-objects")
        void reportsMalformed() {
            assertThat(parse("{not json").errors()).contains("criteria is not valid JSON");
            assertThat(parse("[1,2]").errors()).contains("criteria must be a JSON object");
            assertThat(parse("\"text\"").errors()).contains("criteria must be a JSON object");
        }

        @Test
        @DisplayName("rejects a non-ISO date instead of silently dropping it")
        void rejectsBadDate() {
            // Dropping it would leave a watch that looks saved but matches the
            // wrong window.
            assertThat(parse("{\"dateStart\":\"14/08/2026\"}").errors())
                    .anyMatch(e -> e.contains("dateStart") && e.contains("ISO date"));
            assertThat(parse("{\"dateStart\":20260814}").errors())
                    .anyMatch(e -> e.contains("dateStart"));
        }

        @Test
        @DisplayName("rejects an inverted date range")
        void rejectsInvertedRange() {
            assertThat(parse("{\"dateStart\":\"2026-08-16\",\"dateEnd\":\"2026-08-14\"}").errors())
                    .contains("dateEnd must not be before dateStart");
        }

        @Test
        @DisplayName("rejects non-positive or non-integral quantities")
        void rejectsBadQuantity() {
            assertThat(parse("{\"quantity\":0}").errors()).anyMatch(e -> e.contains("at least 1"));
            assertThat(parse("{\"quantity\":-3}").errors()).anyMatch(e -> e.contains("at least 1"));
            assertThat(parse("{\"quantity\":\"two\"}").errors())
                    .anyMatch(e -> e.contains("whole number"));
        }

        @Test
        @DisplayName("collects every problem, not just the first")
        void collectsAllErrors() {
            assertThat(parse("{\"dateStart\":\"nope\",\"quantity\":0}").errors()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Date overlap")
    class DateOverlap {

        private final WatchCriteria august14to16 = parse(
                "{\"dateStart\":\"2026-08-14\",\"dateEnd\":\"2026-08-16\"}").criteria();

        @Test
        @DisplayName("matches a slot inside the range")
        void matchesInside() {
            assertThat(august14to16.overlaps(
                    LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15))).isTrue();
        }

        @Test
        @DisplayName("boundaries are inclusive — asking for 14th-16th includes both")
        void boundariesInclusive() {
            assertThat(august14to16.overlaps(
                    LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 14))).isTrue();
            assertThat(august14to16.overlaps(
                    LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 16))).isTrue();
        }

        @Test
        @DisplayName("excludes slots wholly outside the range")
        void excludesOutside() {
            assertThat(august14to16.overlaps(
                    LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13))).isFalse();
            assertThat(august14to16.overlaps(
                    LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17))).isFalse();
        }

        @Test
        @DisplayName("matches a slot that straddles the range")
        void matchesStraddling() {
            assertThat(august14to16.overlaps(
                    LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20))).isTrue();
        }

        @Test
        @DisplayName("an absent bound is open-ended")
        void absentBoundIsOpenEnded() {
            WatchCriteria fromAug14 = parse("{\"dateStart\":\"2026-08-14\"}").criteria();
            assertThat(fromAug14.overlaps(
                    LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 1))).isTrue();
            assertThat(fromAug14.overlaps(
                    LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13))).isFalse();

            assertThat(WatchCriteria.ANY.overlaps(
                    LocalDate.of(1999, 1, 1), LocalDate.of(1999, 1, 1))).isTrue();
        }

        @Test
        @DisplayName("a slot with no dates cannot be excluded by a date filter")
        void undatedSlotMatches() {
            assertThat(august14to16.overlaps(null, null)).isTrue();
        }

        @Test
        @DisplayName("a one-sided slot window is treated as a single day")
        void oneSidedSlotWindow() {
            assertThat(august14to16.overlaps(LocalDate.of(2026, 8, 15), null)).isTrue();
            assertThat(august14to16.overlaps(null, LocalDate.of(2026, 8, 20))).isFalse();
        }
    }

    @Nested
    @DisplayName("Quantity")
    class Quantity {

        @Test
        @DisplayName("satisfied when enough units are available")
        void satisfiedWhenEnough() {
            WatchCriteria needsTwo = parse("{\"quantity\":2}").criteria();

            assertThat(needsTwo.satisfiesQuantity(2)).isTrue();
            assertThat(needsTwo.satisfiesQuantity(5)).isTrue();
            assertThat(needsTwo.satisfiesQuantity(1)).isFalse();
        }

        @Test
        @DisplayName("an unstated requirement or unknown availability does not exclude")
        void unknownDoesNotExclude() {
            assertThat(WatchCriteria.ANY.satisfiesQuantity(1)).isTrue();
            // Source does not report quantity — do not silently drop the alert.
            assertThat(parse("{\"quantity\":4}").criteria().satisfiesQuantity(null)).isTrue();
        }
    }

    @Nested
    @DisplayName("Round-trip")
    class RoundTrip {

        @Test
        @DisplayName("serializes back to the stored shape and always stamps the version")
        void roundTrips() {
            String json = parse("{\"dateStart\":\"2026-08-14\",\"dateEnd\":\"2026-08-16\","
                    + "\"quantity\":2}").criteria().toJson(objectMapper);

            assertThat(json).contains("\"v\":1");
            WatchCriteria.ParseResult reparsed = parse(json);
            assertThat(reparsed.isValid()).isTrue();
            assertThat(reparsed.criteria().dateStart()).isEqualTo(LocalDate.of(2026, 8, 14));
            assertThat(reparsed.criteria().quantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("omits absent fields rather than writing nulls")
        void omitsAbsentFields() {
            String json = WatchCriteria.ANY.toJson(objectMapper);

            assertThat(json).isEqualTo("{\"v\":1}");
        }
    }

    @Test
    @DisplayName("unwraps a legacy double-encoded criteria string (storage regression)")
    void unwrapsDoubleEncodedCriteria() {
        // Rows written before the storage fix hold the object as a JSON *string*.
        String legacy = "\"{\\\"v\\\":1,\\\"dateStart\\\":\\\"2026-09-01\\\"}\"";
        WatchCriteria.ParseResult result = WatchCriteria.parse(legacy, objectMapper);

        assertThat(result.isValid()).isTrue();
        assertThat(result.criteria().dateStart()).isEqualTo(java.time.LocalDate.of(2026, 9, 1));
    }
}
