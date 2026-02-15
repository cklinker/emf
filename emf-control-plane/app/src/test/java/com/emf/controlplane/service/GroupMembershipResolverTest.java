package com.emf.controlplane.service;

import com.emf.controlplane.entity.GroupMembership;
import com.emf.controlplane.repository.GroupMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupMembershipResolver")
class GroupMembershipResolverTest {

    @Mock
    private GroupMembershipRepository groupMembershipRepository;

    private GroupMembershipResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new GroupMembershipResolver(groupMembershipRepository);
    }

    @Nested
    @DisplayName("getEffectiveGroupIds")
    class GetEffectiveGroupIdsTests {

        @Test
        @DisplayName("should return empty set for null userId")
        void emptyForNullUser() {
            Set<String> result = resolver.getEffectiveGroupIds(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty set when user has no group memberships")
        void emptyWhenNoGroups() {
            when(groupMembershipRepository.findGroupIdsByUserId("user-1"))
                    .thenReturn(Collections.emptyList());

            Set<String> result = resolver.getEffectiveGroupIds("user-1");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return direct group memberships when no nesting")
        void directMembershipsOnly() {
            when(groupMembershipRepository.findGroupIdsByUserId("user-1"))
                    .thenReturn(List.of("group-a", "group-b"));

            // No parent groups for either group
            when(groupMembershipRepository.findParentGroupsForGroup("group-a"))
                    .thenReturn(Collections.emptyList());
            when(groupMembershipRepository.findParentGroupsForGroup("group-b"))
                    .thenReturn(Collections.emptyList());

            Set<String> result = resolver.getEffectiveGroupIds("user-1");
            assertThat(result).containsExactlyInAnyOrder("group-a", "group-b");
        }

        @Test
        @DisplayName("should include parent groups through nesting")
        void includesParentGroups() {
            // User is in group-a
            when(groupMembershipRepository.findGroupIdsByUserId("user-1"))
                    .thenReturn(List.of("group-a"));

            // group-a is nested inside group-b
            GroupMembership parentMembership = new GroupMembership("group-b", "GROUP", "group-a");
            when(groupMembershipRepository.findParentGroupsForGroup("group-a"))
                    .thenReturn(List.of(parentMembership));

            // group-b is nested inside group-c
            GroupMembership grandParentMembership = new GroupMembership("group-c", "GROUP", "group-b");
            when(groupMembershipRepository.findParentGroupsForGroup("group-b"))
                    .thenReturn(List.of(grandParentMembership));

            // group-c has no parents
            when(groupMembershipRepository.findParentGroupsForGroup("group-c"))
                    .thenReturn(Collections.emptyList());

            Set<String> result = resolver.getEffectiveGroupIds("user-1");
            assertThat(result).containsExactlyInAnyOrder("group-a", "group-b", "group-c");
        }

        @Test
        @DisplayName("should handle diamond inheritance (group in multiple parents)")
        void handlesDiamondInheritance() {
            // User is in group-a and group-b
            when(groupMembershipRepository.findGroupIdsByUserId("user-1"))
                    .thenReturn(List.of("group-a", "group-b"));

            // Both group-a and group-b are nested inside group-c
            GroupMembership parentA = new GroupMembership("group-c", "GROUP", "group-a");
            GroupMembership parentB = new GroupMembership("group-c", "GROUP", "group-b");
            when(groupMembershipRepository.findParentGroupsForGroup("group-a"))
                    .thenReturn(List.of(parentA));
            when(groupMembershipRepository.findParentGroupsForGroup("group-b"))
                    .thenReturn(List.of(parentB));

            // group-c has no parents
            when(groupMembershipRepository.findParentGroupsForGroup("group-c"))
                    .thenReturn(Collections.emptyList());

            Set<String> result = resolver.getEffectiveGroupIds("user-1");
            assertThat(result).containsExactlyInAnyOrder("group-a", "group-b", "group-c");
        }

        @Test
        @DisplayName("should detect and handle cycles without infinite loop")
        void handlesCycles() {
            // User is in group-a
            when(groupMembershipRepository.findGroupIdsByUserId("user-1"))
                    .thenReturn(List.of("group-a"));

            // group-a is in group-b
            GroupMembership parentA = new GroupMembership("group-b", "GROUP", "group-a");
            when(groupMembershipRepository.findParentGroupsForGroup("group-a"))
                    .thenReturn(List.of(parentA));

            // group-b is in group-a (cycle!)
            GroupMembership parentB = new GroupMembership("group-a", "GROUP", "group-b");
            when(groupMembershipRepository.findParentGroupsForGroup("group-b"))
                    .thenReturn(List.of(parentB));

            // Should not infinite loop; should return both groups
            Set<String> result = resolver.getEffectiveGroupIds("user-1");
            assertThat(result).containsExactlyInAnyOrder("group-a", "group-b");
        }

        @Test
        @DisplayName("should respect maximum depth limit")
        void respectsMaxDepth() {
            // Create a chain of 12 nested groups (exceeds MAX_DEPTH of 10)
            when(groupMembershipRepository.findGroupIdsByUserId("user-1"))
                    .thenReturn(List.of("group-0"));

            // Use lenient() because stubs beyond depth 10 won't be called
            for (int i = 0; i < 12; i++) {
                String childId = "group-" + i;
                String parentId = "group-" + (i + 1);
                GroupMembership parent = new GroupMembership(parentId, "GROUP", childId);
                lenient().when(groupMembershipRepository.findParentGroupsForGroup(childId))
                        .thenReturn(List.of(parent));
            }
            lenient().when(groupMembershipRepository.findParentGroupsForGroup("group-12"))
                    .thenReturn(Collections.emptyList());

            Set<String> result = resolver.getEffectiveGroupIds("user-1");

            // Should have groups 0 through 10 (11 groups, depth limit stops at 10)
            assertThat(result).contains("group-0", "group-1", "group-10");
            // group-11 and beyond should be cut off
            assertThat(result).doesNotContain("group-11", "group-12");
        }
    }

    @Nested
    @DisplayName("getEffectiveUserIds")
    class GetEffectiveUserIdsTests {

        @Test
        @DisplayName("should return empty set for null groupId")
        void emptyForNullGroup() {
            Set<String> result = resolver.getEffectiveUserIds(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return direct user members")
        void directUserMembers() {
            when(groupMembershipRepository.findUserMemberIdsByGroupId("group-a"))
                    .thenReturn(List.of("user-1", "user-2"));
            when(groupMembershipRepository.findChildGroupIdsByGroupId("group-a"))
                    .thenReturn(Collections.emptyList());

            Set<String> result = resolver.getEffectiveUserIds("group-a");
            assertThat(result).containsExactlyInAnyOrder("user-1", "user-2");
        }

        @Test
        @DisplayName("should include users from child groups")
        void includesChildGroupUsers() {
            // group-a has user-1 directly and group-b as child
            when(groupMembershipRepository.findUserMemberIdsByGroupId("group-a"))
                    .thenReturn(List.of("user-1"));
            when(groupMembershipRepository.findChildGroupIdsByGroupId("group-a"))
                    .thenReturn(List.of("group-b"));

            // group-b has user-2 and user-3
            when(groupMembershipRepository.findUserMemberIdsByGroupId("group-b"))
                    .thenReturn(List.of("user-2", "user-3"));
            when(groupMembershipRepository.findChildGroupIdsByGroupId("group-b"))
                    .thenReturn(Collections.emptyList());

            Set<String> result = resolver.getEffectiveUserIds("group-a");
            assertThat(result).containsExactlyInAnyOrder("user-1", "user-2", "user-3");
        }

        @Test
        @DisplayName("should deduplicate users appearing in multiple child groups")
        void deduplicatesUsers() {
            // group-a has group-b and group-c as children
            when(groupMembershipRepository.findUserMemberIdsByGroupId("group-a"))
                    .thenReturn(Collections.emptyList());
            when(groupMembershipRepository.findChildGroupIdsByGroupId("group-a"))
                    .thenReturn(List.of("group-b", "group-c"));

            // Both group-b and group-c have user-1
            when(groupMembershipRepository.findUserMemberIdsByGroupId("group-b"))
                    .thenReturn(List.of("user-1", "user-2"));
            when(groupMembershipRepository.findChildGroupIdsByGroupId("group-b"))
                    .thenReturn(Collections.emptyList());

            when(groupMembershipRepository.findUserMemberIdsByGroupId("group-c"))
                    .thenReturn(List.of("user-1", "user-3"));
            when(groupMembershipRepository.findChildGroupIdsByGroupId("group-c"))
                    .thenReturn(Collections.emptyList());

            Set<String> result = resolver.getEffectiveUserIds("group-a");
            assertThat(result).containsExactlyInAnyOrder("user-1", "user-2", "user-3");
        }

        @Test
        @DisplayName("should handle cycles in child group resolution")
        void handlesCyclesInChildren() {
            // group-a contains group-b
            when(groupMembershipRepository.findUserMemberIdsByGroupId("group-a"))
                    .thenReturn(List.of("user-1"));
            when(groupMembershipRepository.findChildGroupIdsByGroupId("group-a"))
                    .thenReturn(List.of("group-b"));

            // group-b contains group-a (cycle!)
            when(groupMembershipRepository.findUserMemberIdsByGroupId("group-b"))
                    .thenReturn(List.of("user-2"));
            when(groupMembershipRepository.findChildGroupIdsByGroupId("group-b"))
                    .thenReturn(List.of("group-a"));

            Set<String> result = resolver.getEffectiveUserIds("group-a");
            assertThat(result).containsExactlyInAnyOrder("user-1", "user-2");
        }
    }
}
