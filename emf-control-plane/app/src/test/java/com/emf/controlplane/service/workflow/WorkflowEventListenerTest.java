package com.emf.controlplane.service.workflow;

import com.emf.runtime.event.RecordChangeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        listener = new WorkflowEventListener(workflowEngine, objectMapper);
    }

    @Test
    @DisplayName("Should deserialize event and delegate to engine")
    void shouldDeserializeAndDelegate() throws Exception {
        RecordChangeEvent event = RecordChangeEvent.created(
            "tenant-1", "orders", "rec-1", Map.of("id", "rec-1"), "user-1");
        String json = objectMapper.writeValueAsString(event);

        listener.onRecordChanged(json);

        verify(workflowEngine).evaluate(any(RecordChangeEvent.class));
    }

    @Test
    @DisplayName("Should handle invalid JSON gracefully")
    void shouldHandleInvalidJson() {
        listener.onRecordChanged("not valid json");

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
        listener.onRecordChanged(json);
    }
}
