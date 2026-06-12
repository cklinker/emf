package io.kelta.worker.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CronExpressions")
class CronExpressionsTest {

    @Test
    @DisplayName("Normalizes a 5-field cron by prepending seconds")
    void normalizesFiveFieldCron() {
        assertEquals("0 0 */4 * * *", CronExpressions.normalize("0 */4 * * *"));
    }

    @Test
    @DisplayName("Passes a valid 6-field cron through unchanged")
    void passesSixFieldCronThrough() {
        assertEquals("0 0 */4 * * *", CronExpressions.normalize("0 0 */4 * * *"));
    }

    @Test
    @DisplayName("Collapses runs of whitespace before normalization")
    void collapsesWhitespace() {
        assertEquals("0 0 */4 * * *", CronExpressions.normalize("0   */4  *   *  *"));
    }

    @Test
    @DisplayName("Rejects garbage with a clear, actionable error")
    void rejectsGarbage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CronExpressions.normalize("not-a-cron"));
        assertTrue(ex.getMessage().contains("5- or 6-field"),
                "error should describe the expected shape, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Rejects 6-field input that fails Spring's parser with the bad value quoted")
    void rejectsInvalidSixFieldCron() {
        // 99 is out of range for the seconds field — passes the field-count
        // check but fails CronExpression.parse.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CronExpressions.normalize("99 * * * * *"));
        assertTrue(ex.getMessage().contains("'99 * * * * *'"),
                "error should quote the offending expression, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("6-field Spring expression"),
                "error should mention the 6-field requirement, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Rejects null and blank")
    void rejectsNullAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> CronExpressions.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> CronExpressions.normalize(""));
        assertThrows(IllegalArgumentException.class, () -> CronExpressions.normalize("   "));
    }

    @Test
    @DisplayName("parse() returns a working CronExpression for 5-field input")
    void parseReturnsWorkingExpressionForFiveField() {
        assertNotNull(CronExpressions.parse("0 */4 * * *").next(java.time.ZonedDateTime.now()));
    }

    @Test
    @DisplayName("isValid() reports validity without throwing")
    void isValidReportsValidity() {
        assertTrue(CronExpressions.isValid("0 */4 * * *"));
        assertTrue(CronExpressions.isValid("0 0 */4 * * *"));
        assertFalse(CronExpressions.isValid("not-a-cron"));
        assertFalse(CronExpressions.isValid(null));
    }
}
