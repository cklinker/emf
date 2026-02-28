package com.emf.runtime.flow;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.formula.FormulaEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link FlowTriggerEvaluator}.
 */
class FlowTriggerEvaluatorTest {

    private FormulaEvaluator formulaEvaluator;
    private FlowTriggerEvaluator evaluator;

    @BeforeEach
    void setUp() {
        formulaEvaluator = mock(FormulaEvaluator.class);
        evaluator = new FlowTriggerEvaluator(formulaEvaluator);
    }

    @Test
    void nullTriggerConfigReturnsFalse() {
        RecordChangeEvent event = createEvent(ChangeType.CREATED, "orders");
        assertFalse(evaluator.matchesRecordTrigger(event, null));
    }

    @Test
    void emptyTriggerConfigMatchesAll() {
        RecordChangeEvent event = createEvent(ChangeType.CREATED, "orders");
        assertTrue(evaluator.matchesRecordTrigger(event, Map.of()));
    }

    @Test
    void collectionMatchesExact() {
        RecordChangeEvent event = createEvent(ChangeType.CREATED, "orders");

        assertTrue(evaluator.matchesRecordTrigger(event,
                Map.of("collection", "orders")));
    }

    @Test
    void collectionMismatchReturnsFalse() {
        RecordChangeEvent event = createEvent(ChangeType.CREATED, "orders");

        assertFalse(evaluator.matchesRecordTrigger(event,
                Map.of("collection", "contacts")));
    }

    @Test
    void eventTypeMatchesSingleEvent() {
        RecordChangeEvent event = createEvent(ChangeType.CREATED, "orders");

        assertTrue(evaluator.matchesRecordTrigger(event,
                Map.of("events", List.of("CREATED"))));
    }

    @Test
    void eventTypeMatchesMultipleEvents() {
        RecordChangeEvent event = createEvent(ChangeType.UPDATED, "orders");

        assertTrue(evaluator.matchesRecordTrigger(event,
                Map.of("events", List.of("CREATED", "UPDATED"))));
    }

    @Test
    void eventTypeMismatchReturnsFalse() {
        RecordChangeEvent event = createEvent(ChangeType.DELETED, "orders");

        assertFalse(evaluator.matchesRecordTrigger(event,
                Map.of("events", List.of("CREATED", "UPDATED"))));
    }

    @Test
    void emptyEventsMatchesAll() {
        RecordChangeEvent event = createEvent(ChangeType.DELETED, "orders");

        assertTrue(evaluator.matchesRecordTrigger(event,
                Map.of("events", List.of())));
    }

    @Test
    void triggerFieldsMatchOnUpdate() {
        RecordChangeEvent event = RecordChangeEvent.updated(
                "tenant-1", "orders", "rec-1",
                Map.of("status", "ACTIVE", "amount", 100),
                Map.of("status", "DRAFT"),
                List.of("status"),
                "user-1");

        assertTrue(evaluator.matchesRecordTrigger(event,
                Map.of("triggerFields", List.of("status", "amount"))));
    }

    @Test
    void triggerFieldsMismatchOnUpdateReturnsFalse() {
        RecordChangeEvent event = RecordChangeEvent.updated(
                "tenant-1", "orders", "rec-1",
                Map.of("status", "ACTIVE", "amount", 100),
                Map.of("name", "old"),
                List.of("name"),
                "user-1");

        assertFalse(evaluator.matchesRecordTrigger(event,
                Map.of("triggerFields", List.of("status", "amount"))));
    }

    @Test
    void triggerFieldsIgnoredForNonUpdateEvents() {
        RecordChangeEvent event = createEvent(ChangeType.CREATED, "orders");

        // triggerFields only apply to UPDATE events — CREATED should match regardless
        assertTrue(evaluator.matchesRecordTrigger(event,
                Map.of("triggerFields", List.of("status"))));
    }

    @Test
    void filterFormulaMatchesWhenTrue() {
        RecordChangeEvent event = createEvent(ChangeType.CREATED, "orders");
        when(formulaEvaluator.evaluateBoolean(eq("status == 'ACTIVE'"), anyMap())).thenReturn(true);

        assertTrue(evaluator.matchesRecordTrigger(event,
                Map.of("filterFormula", "status == 'ACTIVE'")));
    }

    @Test
    void filterFormulaMismatchReturnsFalse() {
        RecordChangeEvent event = createEvent(ChangeType.CREATED, "orders");
        when(formulaEvaluator.evaluateBoolean(eq("status == 'ACTIVE'"), anyMap())).thenReturn(false);

        assertFalse(evaluator.matchesRecordTrigger(event,
                Map.of("filterFormula", "status == 'ACTIVE'")));
    }

    @Test
    void filterFormulaExceptionReturnsFalse() {
        RecordChangeEvent event = createEvent(ChangeType.CREATED, "orders");
        when(formulaEvaluator.evaluateBoolean(anyString(), anyMap()))
                .thenThrow(new RuntimeException("parse error"));

        assertFalse(evaluator.matchesRecordTrigger(event,
                Map.of("filterFormula", "invalid formula")));
    }

    @Test
    void blankFilterFormulaIsIgnored() {
        RecordChangeEvent event = createEvent(ChangeType.CREATED, "orders");

        assertTrue(evaluator.matchesRecordTrigger(event,
                Map.of("filterFormula", "   ")));
    }

    @Test
    void combinedConditionsAllMatch() {
        RecordChangeEvent event = RecordChangeEvent.updated(
                "tenant-1", "orders", "rec-1",
                Map.of("status", "ACTIVE"),
                Map.of("status", "DRAFT"),
                List.of("status"),
                "user-1");

        when(formulaEvaluator.evaluateBoolean(eq("status == 'ACTIVE'"), anyMap())).thenReturn(true);

        Map<String, Object> triggerConfig = Map.of(
                "collection", "orders",
                "events", List.of("UPDATED"),
                "triggerFields", List.of("status"),
                "filterFormula", "status == 'ACTIVE'"
        );

        assertTrue(evaluator.matchesRecordTrigger(event, triggerConfig));
    }

    @Test
    void combinedConditionsFailOnCollection() {
        RecordChangeEvent event = createEvent(ChangeType.UPDATED, "contacts");

        Map<String, Object> triggerConfig = Map.of(
                "collection", "orders",
                "events", List.of("UPDATED")
        );

        assertFalse(evaluator.matchesRecordTrigger(event, triggerConfig));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RecordChangeEvent createEvent(ChangeType changeType, String collection) {
        return switch (changeType) {
            case CREATED -> RecordChangeEvent.created(
                    "tenant-1", collection, "rec-1",
                    Map.of("status", "ACTIVE"), "user-1");
            case UPDATED -> RecordChangeEvent.updated(
                    "tenant-1", collection, "rec-1",
                    Map.of("status", "ACTIVE"),
                    Map.of("status", "DRAFT"),
                    List.of("status"),
                    "user-1");
            case DELETED -> RecordChangeEvent.deleted(
                    "tenant-1", collection, "rec-1",
                    Map.of("status", "ACTIVE"), "user-1");
        };
    }
}
