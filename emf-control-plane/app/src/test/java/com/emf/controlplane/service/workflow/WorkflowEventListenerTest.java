package com.emf.controlplane.service.workflow;

import com.emf.runtime.event.RecordChangeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowEventListenerTest {

    private WorkflowEngine workflowEngine;
    private ObjectMapper objectMapper;
    private WorkflowEventListener listener;

    @BeforeEach
    void setUp() {
        workflowEngine = mock(WorkflowEngine.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // null executor — runs inline (same thread)
        listener = new WorkflowEventListener(workflowEngine, objectMapper, null);
    }

    @Test
    @DisplayName("Should deserialize event and delegate to engine")
    void shouldDeserializeAndDelegate() throws Exception {
        RecordChangeEvent event = RecordChangeEvent.created(
            "tenant-1", "orders", "rec-1", Map.of("id", "rec-1"), "user-1");
        String json = objectMapper.writeValueAsString(event);

        listener.onRecordChanged(List.of(json));

        verify(workflowEngine).evaluate(any(RecordChangeEvent.class));
    }

    @Test
    @DisplayName("Should handle invalid JSON gracefully")
    void shouldHandleInvalidJson() {
        listener.onRecordChanged(List.of("not valid json"));

        verify(workflowEngine, never()).evaluate(any());
    }

    @Test
    @DisplayName("Should handle engine exception gracefully")
    void shouldHandleEngineException() throws Exception {
        RecordChangeEvent event = RecordChangeEvent.created(
            "tenant-1", "orders", "rec-1", Map.of("id", "rec-1"), "user-1");
        String json = objectMapper.writeValueAsString(event);

        doThrow(new RuntimeException("Engine error")).when(workflowEngine).evaluate(any());

        // Should not throw
        listener.onRecordChanged(List.of(json));
    }

    @Test
    @DisplayName("Should process batch of multiple events")
    void shouldProcessBatchOfEvents() throws Exception {
        RecordChangeEvent event1 = RecordChangeEvent.created(
            "tenant-1", "orders", "rec-1", Map.of("id", "rec-1"), "user-1");
        RecordChangeEvent event2 = RecordChangeEvent.created(
            "tenant-1", "orders", "rec-2", Map.of("id", "rec-2"), "user-1");
        String json1 = objectMapper.writeValueAsString(event1);
        String json2 = objectMapper.writeValueAsString(event2);

        listener.onRecordChanged(List.of(json1, json2));

        verify(workflowEngine, times(2)).evaluate(any(RecordChangeEvent.class));
    }

    @Test
    @DisplayName("Should continue processing when one event in batch fails")
    void shouldContinueOnBatchFailure() throws Exception {
        RecordChangeEvent event1 = RecordChangeEvent.created(
            "tenant-1", "orders", "rec-1", Map.of("id", "rec-1"), "user-1");
        RecordChangeEvent event2 = RecordChangeEvent.created(
            "tenant-1", "orders", "rec-2", Map.of("id", "rec-2"), "user-1");
        String json1 = objectMapper.writeValueAsString(event1);
        String json2 = objectMapper.writeValueAsString(event2);

        // First event valid JSON but engine throws, second event also valid
        doThrow(new RuntimeException("Engine error")).doNothing()
            .when(workflowEngine).evaluate(any());

        listener.onRecordChanged(List.of(json1, json2));

        // Both should be attempted even though first fails
        verify(workflowEngine, times(2)).evaluate(any(RecordChangeEvent.class));
    }

    @Test
    @DisplayName("Should use executor when provided")
    void shouldUseExecutorWhenProvided() throws Exception {
        // Create a direct executor for testing
        Executor directExecutor = Runnable::run;
        WorkflowEventListener listenerWithExecutor =
            new WorkflowEventListener(workflowEngine, objectMapper, directExecutor);

        RecordChangeEvent event = RecordChangeEvent.created(
            "tenant-1", "orders", "rec-1", Map.of("id", "rec-1"), "user-1");
        String json = objectMapper.writeValueAsString(event);

        listenerWithExecutor.onRecordChanged(List.of(json));

        verify(workflowEngine).evaluate(any(RecordChangeEvent.class));
    }

    @Test
    @DisplayName("Should handle empty batch")
    void shouldHandleEmptyBatch() {
        listener.onRecordChanged(List.of());

        verify(workflowEngine, never()).evaluate(any());
    }
}
