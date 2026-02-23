package com.emf.controlplane.event;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.runtime.event.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowRuleChangedPayloadTest {

    @Test
    @DisplayName("Should create payload from workflow rule entity")
    void shouldCreateFromEntity() {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("orders");

        WorkflowRule rule = new WorkflowRule();
        rule.setTenantId("tenant-1");
        rule.setCollection(collection);
        rule.setName("Test Rule");
        rule.setActive(true);
        rule.setTriggerType("ON_CREATE");

        WorkflowRuleChangedPayload payload =
            WorkflowRuleChangedPayload.create(rule, ChangeType.CREATED);

        assertNotNull(payload.getRuleId());
        assertEquals("tenant-1", payload.getTenantId());
        assertEquals("col-1", payload.getCollectionId());
        assertEquals("Test Rule", payload.getName());
        assertTrue(payload.isActive());
        assertEquals("ON_CREATE", payload.getTriggerType());
        assertEquals(ChangeType.CREATED, payload.getChangeType());
        assertNotNull(payload.getTimestamp());
    }

    @Test
    @DisplayName("Should create payload for delete change type")
    void shouldCreateForDelete() {
        Collection collection = new Collection();
        collection.setId("col-2");

        WorkflowRule rule = new WorkflowRule();
        rule.setTenantId("tenant-1");
        rule.setCollection(collection);
        rule.setName("Delete Rule");
        rule.setActive(false);
        rule.setTriggerType("ON_UPDATE");

        WorkflowRuleChangedPayload payload =
            WorkflowRuleChangedPayload.create(rule, ChangeType.DELETED);

        assertEquals(ChangeType.DELETED, payload.getChangeType());
        assertFalse(payload.isActive());
    }

    @Test
    @DisplayName("Should have valid toString representation")
    void shouldHaveToString() {
        Collection collection = new Collection();
        collection.setId("col-1");

        WorkflowRule rule = new WorkflowRule();
        rule.setTenantId("tenant-1");
        rule.setCollection(collection);
        rule.setName("Test Rule");
        rule.setTriggerType("ON_CREATE");

        WorkflowRuleChangedPayload payload =
            WorkflowRuleChangedPayload.create(rule, ChangeType.UPDATED);

        String str = payload.toString();
        assertTrue(str.contains("tenantId='tenant-1'"));
        assertTrue(str.contains("collectionId='col-1'"));
        assertTrue(str.contains("UPDATED"));
    }
}
