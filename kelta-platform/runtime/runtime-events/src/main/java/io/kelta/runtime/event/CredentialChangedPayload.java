package io.kelta.runtime.event;

import java.util.Objects;

/**
 * Payload for {@code kelta.config.credential.changed.<id>} events.
 *
 * <p>Contains only non-secret metadata. The encrypted credential blob never
 * appears in events — listeners that need decrypted material must call
 * {@code CredentialResolver.resolve(...)} themselves.
 */
public class CredentialChangedPayload {

    private String id;
    private String name;
    private String type;
    private ChangeType changeType;

    public CredentialChangedPayload() {
    }

    public CredentialChangedPayload(String id, String name, String type, ChangeType changeType) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.changeType = changeType;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CredentialChangedPayload that)) return false;
        return Objects.equals(id, that.id)
            && Objects.equals(name, that.name)
            && Objects.equals(type, that.type)
            && changeType == that.changeType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, changeType);
    }

    @Override
    public String toString() {
        return "CredentialChangedPayload{id=" + id + ", name=" + name
            + ", type=" + type + ", changeType=" + changeType + '}';
    }
}
