package com.emf.controlplane.event;

import com.emf.controlplane.config.CacheConfig;
import com.emf.runtime.event.ConfigEvent;
import com.emf.runtime.event.EventFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Map;

import static org.mockito.Mockito.*;

class WorkflowConfigListenerTest {

    private CacheManager cacheManager;
    private Cache workflowCache;
    private WorkflowConfigListener listener;

    @BeforeEach
    void setUp() {
        cacheManager = mock(CacheManager.class);
        workflowCache = mock(Cache.class);
        when(cacheManager.getCache(CacheConfig.WORKFLOW_RULES_CACHE)).thenReturn(workflowCache);

        listener = new WorkflowConfigListener(cacheManager, new ObjectMapper());
    }

    @Test
    @DisplayName("Should evict cache for specific tenant+collection on rule change")
    void shouldEvictSpecificCacheKey() {
        Map<String, Object> payload = Map.of(
            "ruleId", "rule-1",
            "tenantId", "tenant-1",
            "collectionId", "col-1",
            "changeType", "UPDATED"
        );
        ConfigEvent<Object> event = EventFactory.createEvent(
                "emf.config.workflow.changed", payload);

        listener.handleWorkflowRuleChanged(event);

        verify(workflowCache).evict("tenant-1:col-1");
        verify(workflowCache, never()).clear();
    }

    @Test
    @DisplayName("Should clear entire cache when tenant or collection is missing")
    void shouldClearEntireCacheWhenKeyMissing() {
        Map<String, Object> payload = Map.of(
            "ruleId", "rule-1",
            "changeType", "DELETED"
        );
        ConfigEvent<Object> event = EventFactory.createEvent(
                "emf.config.workflow.changed", payload);

        listener.handleWorkflowRuleChanged(event);

        verify(workflowCache).clear();
        verify(workflowCache, never()).evict(any());
    }

    @Test
    @DisplayName("Should handle null payload gracefully")
    void shouldHandleNullPayload() {
        ConfigEvent<Object> event = EventFactory.createEvent(
                "emf.config.workflow.changed", (Object) null);

        // Should not throw
        listener.handleWorkflowRuleChanged(event);

        verify(workflowCache, never()).evict(any());
        verify(workflowCache, never()).clear();
    }

    @Test
    @DisplayName("Should handle CREATED event")
    void shouldHandleCreatedEvent() {
        Map<String, Object> payload = Map.of(
            "ruleId", "rule-1",
            "tenantId", "tenant-1",
            "collectionId", "col-1",
            "changeType", "CREATED"
        );
        ConfigEvent<Object> event = EventFactory.createEvent(
                "emf.config.workflow.changed", payload);

        listener.handleWorkflowRuleChanged(event);

        verify(workflowCache).evict("tenant-1:col-1");
    }

    @Test
    @DisplayName("Should handle DELETED event")
    void shouldHandleDeletedEvent() {
        Map<String, Object> payload = Map.of(
            "ruleId", "rule-1",
            "tenantId", "tenant-1",
            "collectionId", "col-1",
            "changeType", "DELETED"
        );
        ConfigEvent<Object> event = EventFactory.createEvent(
                "emf.config.workflow.changed", payload);

        listener.handleWorkflowRuleChanged(event);

        verify(workflowCache).evict("tenant-1:col-1");
    }
}
