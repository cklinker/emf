package com.emf.controlplane.service.workflow;

import com.emf.controlplane.entity.WorkflowAction;
import com.emf.controlplane.entity.WorkflowActionLog;
import com.emf.controlplane.entity.WorkflowExecutionLog;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.repository.WorkflowActionLogRepository;
import com.emf.controlplane.repository.WorkflowExecutionLogRepository;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import com.emf.controlplane.service.CollectionService;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.formula.FormulaEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowEngineRetryTest {

    private WorkflowRuleRepository ruleRepository;
    private WorkflowExecutionLogRepository executionLogRepository;
    private WorkflowActionLogRepository actionLogRepository;
    private ActionHandlerRegistry handlerRegistry;
    private FormulaEvaluator formulaEvaluator;
    private CollectionService collectionService;
    private ObjectMapper objectMapper;
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(WorkflowRuleRepository.class);
        executionLogRepository = mock(WorkflowExecutionLogRepository.class);
        actionLogRepository = mock(WorkflowActionLogRepository.class);
        handlerRegistry = mock(ActionHandlerRegistry.class);
        formulaEvaluator = mock(FormulaEvaluator.class);
        collectionService = mock(CollectionService.class);
        objectMapper = new ObjectMapper();

        engine = new WorkflowEngine(ruleRepository, executionLogRepository,
            actionLogRepository, handlerRegistry, formulaEvaluator,
            collectionService, objectMapper);
    }

    @Test
    @DisplayName("Should not retry when retryCount is 0")
    void shouldNotRetryWhenRetryCountIsZero() {
        WorkflowAction action = createAction(0, 60, "FIXED");
        WorkflowRule rule = createRule();
        RecordChangeEvent event = createEvent();

        ActionHandler handler = mock(ActionHandler.class);
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));
        when(handler.execute(any())).thenReturn(ActionResult.failure("First failure"));

        ActionResult result = engine.executeActionWithRetry(action, rule, event, "exec-1");

        assertFalse(result.successful());
        verify(handler, times(1)).execute(any());
    }

    @Test
    @DisplayName("Should retry on failure with FIXED backoff")
    void shouldRetryWithFixedBackoff() {
        WorkflowAction action = createAction(2, 1, "FIXED");
        WorkflowRule rule = createRule();
        RecordChangeEvent event = createEvent();

        ActionHandler handler = mock(ActionHandler.class);
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));
        when(handler.execute(any()))
            .thenReturn(ActionResult.failure("Attempt 1"))
            .thenReturn(ActionResult.failure("Attempt 2"))
            .thenReturn(ActionResult.success());

        ActionResult result = engine.executeActionWithRetry(action, rule, event, "exec-1");

        assertTrue(result.successful());
        verify(handler, times(3)).execute(any());
    }

    @Test
    @DisplayName("Should stop retrying after maxAttempts")
    void shouldStopAfterMaxAttempts() {
        WorkflowAction action = createAction(1, 1, "FIXED");
        WorkflowRule rule = createRule();
        RecordChangeEvent event = createEvent();

        ActionHandler handler = mock(ActionHandler.class);
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));
        when(handler.execute(any()))
            .thenReturn(ActionResult.failure("Fail 1"))
            .thenReturn(ActionResult.failure("Fail 2"));

        ActionResult result = engine.executeActionWithRetry(action, rule, event, "exec-1");

        assertFalse(result.successful());
        assertEquals("Fail 2", result.errorMessage());
        verify(handler, times(2)).execute(any()); // 1 + 1 retry = 2
    }

    @Test
    @DisplayName("Should succeed on first attempt without retry")
    void shouldSucceedOnFirstAttempt() {
        WorkflowAction action = createAction(3, 1, "FIXED");
        WorkflowRule rule = createRule();
        RecordChangeEvent event = createEvent();

        ActionHandler handler = mock(ActionHandler.class);
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));
        when(handler.execute(any())).thenReturn(ActionResult.success());

        ActionResult result = engine.executeActionWithRetry(action, rule, event, "exec-1");

        assertTrue(result.successful());
        verify(handler, times(1)).execute(any());
    }

    @Test
    @DisplayName("Should log each retry attempt")
    void shouldLogEachAttempt() {
        WorkflowAction action = createAction(1, 1, "FIXED");
        action.setId("action-1");
        WorkflowRule rule = createRule();
        RecordChangeEvent event = createEvent();

        ActionHandler handler = mock(ActionHandler.class);
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));
        when(handler.execute(any()))
            .thenReturn(ActionResult.failure("Retry fail"))
            .thenReturn(ActionResult.success());

        engine.executeActionWithRetry(action, rule, event, "exec-1");

        // Should log 2 action log entries (one per attempt)
        verify(actionLogRepository, times(2)).save(argThat(log -> {
            WorkflowActionLog actionLog = (WorkflowActionLog) log;
            return actionLog.getExecutionLogId().equals("exec-1");
        }));
    }

    @Test
    @DisplayName("Should use EXPONENTIAL backoff when configured")
    void shouldUseExponentialBackoff() {
        // We can't easily test actual sleep timing, but we verify it
        // handles EXPONENTIAL config without error and retries properly
        WorkflowAction action = createAction(1, 1, "EXPONENTIAL");
        WorkflowRule rule = createRule();
        RecordChangeEvent event = createEvent();

        ActionHandler handler = mock(ActionHandler.class);
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));
        when(handler.execute(any()))
            .thenReturn(ActionResult.failure("Fail"))
            .thenReturn(ActionResult.success());

        ActionResult result = engine.executeActionWithRetry(action, rule, event, "exec-1");

        assertTrue(result.successful());
        verify(handler, times(2)).execute(any());
    }

    @Test
    @DisplayName("Should handle no handler registered during retry")
    void shouldHandleNoHandlerDuringRetry() {
        WorkflowAction action = createAction(2, 1, "FIXED");
        WorkflowRule rule = createRule();
        RecordChangeEvent event = createEvent();

        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.empty());

        ActionResult result = engine.executeActionWithRetry(action, rule, event, "exec-1");

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("No handler registered"));
    }

    // --- Helpers ---

    private WorkflowAction createAction(int retryCount, int retryDelaySeconds, String retryBackoff) {
        WorkflowAction action = new WorkflowAction();
        action.setActionType("FIELD_UPDATE");
        action.setConfig("{}");
        action.setActive(true);
        action.setRetryCount(retryCount);
        action.setRetryDelaySeconds(retryDelaySeconds);
        action.setRetryBackoff(retryBackoff);
        return action;
    }

    private WorkflowRule createRule() {
        WorkflowRule rule = new WorkflowRule();
        rule.setName("Test Rule");
        rule.setTriggerType("ON_CREATE");
        rule.setErrorHandling("STOP_ON_ERROR");

        com.emf.controlplane.entity.Collection collection = mock(com.emf.controlplane.entity.Collection.class);
        when(collection.getId()).thenReturn("col-1");
        when(collection.getName()).thenReturn("orders");
        rule.setCollection(collection);

        return rule;
    }

    private RecordChangeEvent createEvent() {
        return new RecordChangeEvent(
            "evt-1", "tenant-1", "orders", "rec-1", ChangeType.CREATED,
            Map.of("name", "Test"), null, List.of(), "user-1", Instant.now());
    }
}
