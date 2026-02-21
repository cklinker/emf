package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a group of users for authorization and record sharing purposes.
 * Groups can be PUBLIC (standard sharing), QUEUE (for queue-based routing), or SYSTEM (platform-managed).
 *
 * Groups support nesting: a group can contain both users and other groups via
 * the {@link GroupMembership} entity. This enables hierarchical group structures
 * (e.g., "All Admins" containing "Data Admins" and "Security Admins").
 *
 * Groups can be sourced from OIDC providers (auto-synced from JWT group claims)
 * or created manually by administrators.
 */
@Entity
@Table(name = "user_group")
@EntityListeners(AuditingEntityListener.class)
public class UserGroup extends TenantScopedEntity {

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_OIDC = "OIDC";
    public static final String SOURCE_SYSTEM = "SYSTEM";

    public static final String TYPE_PUBLIC = "PUBLIC";
    public static final String TYPE_QUEUE = "QUEUE";
    public static final String TYPE_SYSTEM = "SYSTEM";

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "group_type", length = 20)
    private String groupType = TYPE_PUBLIC;

    @Column(name = "source", nullable = false, length = 20)
    private String source = SOURCE_MANUAL;

    @Column(name = "oidc_group_name", length = 200)
    private String oidcGroupName;

    /**
     * Legacy member relationship via user_group_member join table.
     * New code should use {@link GroupMembership} via group_membership table instead.
     */
    @ManyToMany
    @JoinTable(name = "user_group_member",
               joinColumns = @JoinColumn(name = "group_id"),
               inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> members = new HashSet<>();

    @OneToMany(mappedBy = "groupId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupMembership> memberships = new ArrayList<>();

    public UserGroup() {
        super();
    }

    public UserGroup(String tenantId, String name, String description) {
        super(tenantId);
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getGroupType() { return groupType; }
    public void setGroupType(String groupType) { this.groupType = groupType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getOidcGroupName() { return oidcGroupName; }
    public void setOidcGroupName(String oidcGroupName) { this.oidcGroupName = oidcGroupName; }

    public Set<User> getMembers() { return members; }
    public void setMembers(Set<User> members) { this.members = members; }

    public List<GroupMembership> getMemberships() { return memberships; }
    public void setMemberships(List<GroupMembership> memberships) { this.memberships = memberships; }

    public boolean isOidcSynced() { return SOURCE_OIDC.equals(source); }
    public boolean isSystemGroup() { return SOURCE_SYSTEM.equals(source); }
}
