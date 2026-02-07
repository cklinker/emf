package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a group of users for record sharing purposes.
 * Groups can be PUBLIC (standard sharing) or QUEUE (for queue-based routing).
 */
@Entity
@Table(name = "user_group")
@EntityListeners(AuditingEntityListener.class)
public class UserGroup extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "group_type", length = 20)
    private String groupType = "PUBLIC";

    @ManyToMany
    @JoinTable(name = "user_group_member",
               joinColumns = @JoinColumn(name = "group_id"),
               inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> members = new HashSet<>();

    public UserGroup() {
        super();
    }

    public UserGroup(String tenantId, String name, String description) {
        super();
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getGroupType() { return groupType; }
    public void setGroupType(String groupType) { this.groupType = groupType; }

    public Set<User> getMembers() { return members; }
    public void setMembers(Set<User> members) { this.members = members; }
}
