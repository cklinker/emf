package io.kelta.runtime.event;

import java.util.Objects;

/**
 * Payload for {@code kelta.config.flow.changed.<tenantId>} events.
 *
 * <p>Carries just enough metadata for downstream listeners to invalidate the
 * tenant-scoped flow trigger cache and refresh local state if needed. The full
 * flow definition is intentionally not included — consumers that need it should
 * load the row directly from the {@code flow} table.
 */
public class FlowChangedPayload {

    private String id;
    private String name;
    private String flowType;
    private boolean active;
    private ChangeType changeType;

    public FlowChangedPayload() {
    }

    public FlowChangedPayload(String id, String name, String flowType, boolean active,
                               ChangeType changeType) {
        this.id = id;
        this.name = name;
        this.flowType = flowType;
        this.active = active;
        this.changeType = changeType;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFlowType() { return flowType; }
    public void setFlowType(String flowType) { this.flowType = flowType; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowChangedPayload that)) return false;
        return active == that.active
            && Objects.equals(id, that.id)
            && Objects.equals(name, that.name)
            && Objects.equals(flowType, that.flowType)
            && changeType == that.changeType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, flowType, active, changeType);
    }

    @Override
    public String toString() {
        return "FlowChangedPayload{id=" + id + ", name=" + name
            + ", flowType=" + flowType + ", active=" + active
            + ", changeType=" + changeType + '}';
    }
}
