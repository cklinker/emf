package com.emf.worker.listener;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.workflow.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("WorkflowEventListener")
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
    @DisplayName("Should deserialize event and call workflow engine")
    void shouldDeserializeAndCallEngine() throws Exception {
        RecordChangeEvent event = RecordChangeEvent.created(
            "t1", "orders", "r1", Map.of("status", "Active"), "user-1");
        String json = objectMapper.writeValueAsString(event);

        listener.handleRecordChanged(json);

        ArgumentCaptor<RecordChangeEvent> captor = ArgumentCaptor.forClass(RecordChangeEvent.class);
        verify(workflowEngine).evaluate(captor.capture());

        RecordChangeEvent captured = captor.getValue();
        assertEquals("t1", captured.getTenantId());
        assertEquals("orders", captured.getCollectionName());
        assertEquals("r1", captured.getRecordId());
        assertEquals(ChangeType.CREATED, captured.getChangeType());
    }

    @Test
    @DisplayName("Should handle UPDATE events with changed fields")
    void shouldHandleUpdateEvents() throws Exception {
        RecordChangeEvent event = RecordChangeEvent.updated(
            "t1", "orders", "r1",
            Map.of("status", "Done"), Map.of("status", "Active"),
            List.of("status"), "user-1");
        String json = objectMapper.writeValueAsString(event);

        listener.handleRecordChanged(json);

        verify(workflowEngine).evaluate(any(RecordChangeEvent.class));
    }

    @Test
    @DisplayName("Should handle DELETE events")
    void shouldHandleDeleteEvents() throws Exception {
        RecordChangeEvent event = RecordChangeEvent.deleted(
            "t1", "orders", "r1", Map.of("status", "Active"), "user-1");
        String json = objectMapper.writeValueAsString(event);

        listener.handleRecordChanged(json);

        verify(workflowEngine).evaluate(any(RecordChangeEvent.class));
    }

    @Test
    @DisplayName("Should not throw on invalid JSON")
    void shouldNotThrowOnInvalidJson() {
        assertDoesNotThrow(() -> listener.handleRecordChanged("invalid-json"));
        verify(workflowEngine, never()).evaluate(any());
    }

    @Test
    @DisplayName("Should not throw when engine throws")
    void shouldNotThrowWhenEngineThrows() throws Exception {
        doThrow(new RuntimeException("engine error")).when(workflowEngine).evaluate(any());

        RecordChangeEvent event = RecordChangeEvent.created(
            "t1", "orders", "r1", Map.of(), "user-1");
        String json = objectMapper.writeValueAsString(event);

        assertDoesNotThrow(() -> listener.handleRecordChanged(json));
    }
}
