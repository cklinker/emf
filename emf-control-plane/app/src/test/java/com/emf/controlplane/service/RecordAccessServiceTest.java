package com.emf.controlplane.service;

import com.emf.controlplane.entity.*;
import com.emf.controlplane.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordAccessService")
class RecordAccessServiceTest {

    @Mock private PermissionResolver permissionResolver;
    @Mock private OrgWideDefaultRepository owdRepository;
    @Mock private SharingRuleRepository sharingRuleRepository;
    @Mock private RecordShareRepository recordShareRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private UserRepository userRepository;
    @Mock private UserGroupRepository userGroupRepository;
    @Mock private RoleRepository roleRepository;

    private RecordAccessService service;

    private static final String USER_ID = "user-1";
    private static final String TENANT_ID = "tenant-1";
    private static final String COLLECTION_ID = "col-1";
    private static final String RECORD_ID = "rec-1";
    private static final String OWNER_ID = "owner-1";

    @BeforeEach
    void setUp() {
        service = new RecordAccessService(
                permissionResolver, owdRepository, sharingRuleRepository,
                recordShareRepository, authorizationService, userRepository,
                userGroupRepository, roleRepository);
    }

    @Nested
    @DisplayName("canAccess")
    class CanAccessTests {

        @Test
        @DisplayName("should deny when user lacks object read permission")
        void denyWhenNoReadPermission() {
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            false, false, false, false, false, false));

            boolean result = service.canAccess(USER_ID, TENANT_ID, COLLECTION_ID,
                    RECORD_ID, OWNER_ID, RecordAccessService.AccessType.READ);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should allow when canViewAll is set for read access")
        void allowWhenCanViewAll() {
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            true, true, true, true, true, false));

            boolean result = service.canAccess(USER_ID, TENANT_ID, COLLECTION_ID,
                    RECORD_ID, OWNER_ID, RecordAccessService.AccessType.READ);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should allow when canModifyAll is set for edit access")
        void allowWhenCanModifyAll() {
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            true, true, true, true, false, true));

            boolean result = service.canAccess(USER_ID, TENANT_ID, COLLECTION_ID,
                    RECORD_ID, OWNER_ID, RecordAccessService.AccessType.EDIT);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should allow when OWD is PUBLIC_READ_WRITE")
        void allowWhenOwdPublicReadWrite() {
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            true, true, true, true, false, false));
            when(owdRepository.findByTenantIdAndCollectionId(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Optional.empty()); // default is PUBLIC_READ_WRITE

            boolean result = service.canAccess(USER_ID, TENANT_ID, COLLECTION_ID,
                    RECORD_ID, OWNER_ID, RecordAccessService.AccessType.EDIT);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should allow read when OWD is PUBLIC_READ")
        void allowReadWhenOwdPublicRead() {
            OrgWideDefault owd = new OrgWideDefault(TENANT_ID, COLLECTION_ID, "PUBLIC_READ");
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            true, true, true, true, false, false));
            when(owdRepository.findByTenantIdAndCollectionId(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Optional.of(owd));

            boolean result = service.canAccess(USER_ID, TENANT_ID, COLLECTION_ID,
                    RECORD_ID, OWNER_ID, RecordAccessService.AccessType.READ);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should deny edit when OWD is PUBLIC_READ and user is not owner")
        void denyEditWhenOwdPublicRead() {
            OrgWideDefault owd = new OrgWideDefault(TENANT_ID, COLLECTION_ID, "PUBLIC_READ");
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            true, true, true, true, false, false));
            when(owdRepository.findByTenantIdAndCollectionId(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Optional.of(owd));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
            when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(new User()));
            when(sharingRuleRepository.findByTenantIdAndCollectionIdAndActiveTrue(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Collections.emptyList());
            when(recordShareRepository.findDirectUserShares(COLLECTION_ID, RECORD_ID, USER_ID))
                    .thenReturn(Collections.emptyList());
            when(userGroupRepository.findGroupsByUserId(USER_ID)).thenReturn(Collections.emptyList());

            boolean result = service.canAccess(USER_ID, TENANT_ID, COLLECTION_ID,
                    RECORD_ID, OWNER_ID, RecordAccessService.AccessType.EDIT);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should allow when user owns the record")
        void allowWhenUserOwnsRecord() {
            OrgWideDefault owd = new OrgWideDefault(TENANT_ID, COLLECTION_ID, "PRIVATE");
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            true, true, true, true, false, false));
            when(owdRepository.findByTenantIdAndCollectionId(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Optional.of(owd));

            boolean result = service.canAccess(USER_ID, TENANT_ID, COLLECTION_ID,
                    RECORD_ID, USER_ID, RecordAccessService.AccessType.EDIT);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should allow when user is manager of record owner")
        void allowWhenUserIsManager() {
            OrgWideDefault owd = new OrgWideDefault(TENANT_ID, COLLECTION_ID, "PRIVATE");
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            true, true, true, true, false, false));
            when(owdRepository.findByTenantIdAndCollectionId(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Optional.of(owd));

            User user = new User();
            user.setId(USER_ID);
            User owner = new User();
            owner.setId(OWNER_ID);
            owner.setManagerId(USER_ID);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

            boolean result = service.canAccess(USER_ID, TENANT_ID, COLLECTION_ID,
                    RECORD_ID, OWNER_ID, RecordAccessService.AccessType.READ);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should allow when manual share exists")
        void allowWhenManualShareExists() {
            OrgWideDefault owd = new OrgWideDefault(TENANT_ID, COLLECTION_ID, "PRIVATE");
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            true, true, true, true, false, false));
            when(owdRepository.findByTenantIdAndCollectionId(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Optional.of(owd));

            User user = new User();
            user.setId(USER_ID);
            User owner = new User();
            owner.setId(OWNER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

            when(sharingRuleRepository.findByTenantIdAndCollectionIdAndActiveTrue(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Collections.emptyList());

            RecordShare share = new RecordShare();
            share.setAccessLevel("READ_WRITE");
            when(recordShareRepository.findDirectUserShares(COLLECTION_ID, RECORD_ID, USER_ID))
                    .thenReturn(List.of(share));

            boolean result = service.canAccess(USER_ID, TENANT_ID, COLLECTION_ID,
                    RECORD_ID, OWNER_ID, RecordAccessService.AccessType.READ);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("buildSharingWhereClause")
    class BuildSharingWhereClauseTests {

        @Test
        @DisplayName("should return null when canViewAll")
        void returnNullWhenCanViewAll() {
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            true, true, true, true, true, false));

            String clause = service.buildSharingWhereClause(USER_ID, TENANT_ID, COLLECTION_ID);

            assertThat(clause).isNull();
        }

        @Test
        @DisplayName("should return null when OWD is PUBLIC_READ_WRITE")
        void returnNullWhenPublicReadWrite() {
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            true, true, true, true, false, false));
            when(owdRepository.findByTenantIdAndCollectionId(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Optional.empty());

            String clause = service.buildSharingWhereClause(USER_ID, TENANT_ID, COLLECTION_ID);

            assertThat(clause).isNull();
        }

        @Test
        @DisplayName("should include owner_id clause when OWD is PRIVATE")
        void includeOwnerClauseWhenPrivate() {
            OrgWideDefault owd = new OrgWideDefault(TENANT_ID, COLLECTION_ID, "PRIVATE");
            when(permissionResolver.resolveObjectPermission(USER_ID, COLLECTION_ID))
                    .thenReturn(new PermissionResolver.EffectiveObjectPermission(
                            true, true, true, true, false, false));
            when(owdRepository.findByTenantIdAndCollectionId(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Optional.of(owd));
            when(userRepository.findByManagerId(USER_ID)).thenReturn(Collections.emptyList());
            when(recordShareRepository.findByTenantIdAndCollectionId(TENANT_ID, COLLECTION_ID))
                    .thenReturn(Collections.emptyList());
            when(userGroupRepository.findGroupsByUserId(USER_ID)).thenReturn(Collections.emptyList());

            String clause = service.buildSharingWhereClause(USER_ID, TENANT_ID, COLLECTION_ID);

            assertThat(clause).contains("owner_id = 'user-1'");
        }
    }
}
