package com.emf.controlplane.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "script_trigger")
public class ScriptTrigger extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", nullable = false)
    private Script script;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private Collection collection;

    @Column(name = "trigger_event", nullable = false, length = 50)
    private String triggerEvent;

    @Column(name = "execution_order")
    private int executionOrder = 0;

    @Column(name = "active")
    private boolean active = true;

    public ScriptTrigger() { super(); }

    public Script getScript() { return script; }
    public void setScript(Script script) { this.script = script; }
    public Collection getCollection() { return collection; }
    public void setCollection(Collection collection) { this.collection = collection; }
    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
    public int getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
