package io.kelta.worker.config;

import io.kelta.runtime.flow.FlowEngine;
import io.kelta.runtime.flow.FlowStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlowResumePollerConfig")
class FlowResumePollerConfigTest {

    @Mock
    private FlowStore flowStore;

    @Mock
    private FlowEngine flowEngine;

    @Test
    @DisplayName("resumes every claimed execution in claim order")
    void resumesClaimedExecutions() {
        when(flowStore.claimPendingResumes(anyString(), anyInt()))
                .thenReturn(List.of("exec-1", "exec-2"));

        new FlowResumePollerConfig(flowStore, flowEngine).pollAndResume();

        InOrder order = inOrder(flowEngine);
        order.verify(flowEngine).resumeExecution("exec-1");
        order.verify(flowEngine).resumeExecution("exec-2");
    }

    @Test
    @DisplayName("no due resumes → no engine calls")
    void noDueResumes() {
        when(flowStore.claimPendingResumes(anyString(), anyInt())).thenReturn(List.of());

        new FlowResumePollerConfig(flowStore, flowEngine).pollAndResume();

        verify(flowEngine, never()).resumeExecution(anyString());
    }

    @Test
    @DisplayName("a claim failure is swallowed — the poll cycle never throws")
    void claimFailureSwallowed() {
        when(flowStore.claimPendingResumes(anyString(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        new FlowResumePollerConfig(flowStore, flowEngine).pollAndResume();

        verify(flowEngine, never()).resumeExecution(anyString());
    }
}
