package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "approval_process")
public class ApprovalProcess extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "active")
    private boolean active = true;

    @Column(name = "entry_criteria", columnDefinition = "TEXT")
    private String entryCriteria;

    @Column(name = "record_editability", length = 20)
    private String recordEditability = "LOCKED";

    @Column(name = "initial_submitter_field", length = 100)
    private String initialSubmitterField;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "on_submit_field_updates", columnDefinition = "jsonb")
    private String onSubmitFieldUpdates = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "on_approval_field_updates", columnDefinition = "jsonb")
    private String onApprovalFieldUpdates = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "on_rejection_field_updates", columnDefinition = "jsonb")
    private String onRejectionFieldUpdates = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "on_recall_field_updates", columnDefinition = "jsonb")
    private String onRecallFieldUpdates = "[]";

    @Column(name = "allow_recall")
    private boolean allowRecall = true;

    @Column(name = "execution_order")
    private int executionOrder = 0;

    @OneToMany(mappedBy = "approvalProcess", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    private List<ApprovalStep> steps = new ArrayList<>();

    public ApprovalProcess() { super(); }

    public Collection getCollection() { return collection; }
    public void setCollection(Collection collection) { this.collection = collection; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getEntryCriteria() { return entryCriteria; }
    public void setEntryCriteria(String entryCriteria) { this.entryCriteria = entryCriteria; }
    public String getRecordEditability() { return recordEditability; }
    public void setRecordEditability(String recordEditability) { this.recordEditability = recordEditability; }
    public String getInitialSubmitterField() { return initialSubmitterField; }
    public void setInitialSubmitterField(String initialSubmitterField) { this.initialSubmitterField = initialSubmitterField; }
    public String getOnSubmitFieldUpdates() { return onSubmitFieldUpdates; }
    public void setOnSubmitFieldUpdates(String onSubmitFieldUpdates) { this.onSubmitFieldUpdates = onSubmitFieldUpdates; }
    public String getOnApprovalFieldUpdates() { return onApprovalFieldUpdates; }
    public void setOnApprovalFieldUpdates(String onApprovalFieldUpdates) { this.onApprovalFieldUpdates = onApprovalFieldUpdates; }
    public String getOnRejectionFieldUpdates() { return onRejectionFieldUpdates; }
    public void setOnRejectionFieldUpdates(String onRejectionFieldUpdates) { this.onRejectionFieldUpdates = onRejectionFieldUpdates; }
    public String getOnRecallFieldUpdates() { return onRecallFieldUpdates; }
    public void setOnRecallFieldUpdates(String onRecallFieldUpdates) { this.onRecallFieldUpdates = onRecallFieldUpdates; }
    public boolean isAllowRecall() { return allowRecall; }
    public void setAllowRecall(boolean allowRecall) { this.allowRecall = allowRecall; }
    public int getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }
    public List<ApprovalStep> getSteps() { return steps; }
    public void setSteps(List<ApprovalStep> steps) { this.steps = steps; }
}
