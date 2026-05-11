package io.kelta.runtime.event;

import java.util.Objects;

/**
 * Payload for {@code kelta.config.domain.changed.<domainId>} events.
 *
 * <p>Published after a tenant custom domain is registered or removed so all
 * worker and gateway pods can invalidate their domain &rarr; tenant slug
 * caches. The {@code domain} field lets listeners evict a single cache entry
 * rather than dumping the entire cache.
 */
public class DomainChangedPayload {

    private String id;
    private String domain;
    private ChangeType changeType;

    public DomainChangedPayload() {
    }

    public DomainChangedPayload(String id, String domain, ChangeType changeType) {
        this.id = id;
        this.domain = domain;
        this.changeType = changeType;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DomainChangedPayload that)) return false;
        return Objects.equals(id, that.id)
            && Objects.equals(domain, that.domain)
            && changeType == that.changeType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, domain, changeType);
    }

    @Override
    public String toString() {
        return "DomainChangedPayload{id=" + id + ", domain=" + domain
            + ", changeType=" + changeType + '}';
    }
}
