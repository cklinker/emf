package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workflow_rule")
public class WorkflowRule extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "active")
    private boolean active = true;

    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    @Column(name = "filter_formula", columnDefinition = "TEXT")
    private String filterFormula;

    @Column(name = "re_evaluate_on_update")
    private boolean reEvaluateOnUpdate = false;

    @Column(name = "execution_order")
    private int executionOrder = 0;

    @OneToMany(mappedBy = "workflowRule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("executionOrder ASC")
    private List<WorkflowAction> actions = new ArrayList<>();

    public WorkflowRule() { super(); }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Collection getCollection() { return collection; }
    public void setCollection(Collection collection) { this.collection = collection; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getFilterFormula() { return filterFormula; }
    public void setFilterFormula(String filterFormula) { this.filterFormula = filterFormula; }
    public boolean isReEvaluateOnUpdate() { return reEvaluateOnUpdate; }
    public void setReEvaluateOnUpdate(boolean reEvaluateOnUpdate) { this.reEvaluateOnUpdate = reEvaluateOnUpdate; }
    public int getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }
    public List<WorkflowAction> getActions() { return actions; }
    public void setActions(List<WorkflowAction> actions) { this.actions = actions; }
}
