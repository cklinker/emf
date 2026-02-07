package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_action")
public class WorkflowAction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_rule_id", nullable = false)
    private WorkflowRule workflowRule;

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "execution_order")
    private int executionOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private String config;

    @Column(name = "active")
    private boolean active = true;

    public WorkflowAction() { super(); }

    public WorkflowRule getWorkflowRule() { return workflowRule; }
    public void setWorkflowRule(WorkflowRule workflowRule) { this.workflowRule = workflowRule; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public int getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
