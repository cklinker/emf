package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveHookRegistry;
import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.service.delegated.DelegatedWriteContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdentityCollectionGuardHook Tests")
class IdentityCollectionGuardHookTest {

    private static final String PROFILE_HEADER = "X-User-Profile-Id";

    @Mock private BootstrapRepository bootstrapRepository;

    private IdentityCollectionGuardHook hook;

    @BeforeEach
    void setUp() {
        hook = new IdentityCollectionGuardHook(bootstrapRepository);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequestWithProfile(String profileId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (profileId != null) {
            request.addHeader(PROFILE_HEADER, profileId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @SafeVarargs
    private final void stubPermissions(Map<String, Object>... permissions) {
        when(bootstrapRepository.findProfileSystemPermissions("profile-1"))
                .thenReturn(List.of(permissions));
    }

    @Nested
    @DisplayName("Admitted paths")
    class AdmittedPaths {

        @Test
        @DisplayName("non-guarded collection passes even with an unprivileged identity")
        void nonGuardedCollectionIsAdmitted() {
            bindRequestWithProfile("profile-1");

            BeforeSaveResult result = hook.beforeCreate("accounts", Map.of(), "tenant-1");

            assertThat(result.isSuccess()).isTrue();
            verifyNoInteractions(bootstrapRepository);
        }

        @Test
        @DisplayName("no request context bound (flows, schedulers, NATS) passes on users")
        void noRequestContextIsAdmitted() {
            BeforeSaveResult result = hook.beforeCreate("users", Map.of(), "tenant-1");

            assertThat(result.isSuccess()).isTrue();
            verifyNoInteractions(bootstrapRepository);
        }

        @Test
        @DisplayName("request context without X-User-Profile-Id (SCIM/internal) passes")
        void requestWithoutIdentityHeaderIsAdmitted() {
            bindRequestWithProfile(null);

            BeforeSaveResult result = hook.beforeCreate("users", Map.of(), "tenant-1");

            assertThat(result.isSuccess()).isTrue();
            verifyNoInteractions(bootstrapRepository);
        }

        @Test
        @DisplayName("MANAGE_USERS admits writes to users")
        void manageUsersIsAdmitted() {
            bindRequestWithProfile("profile-1");
            stubPermissions(Map.of("permission_name", "MANAGE_USERS", "granted", true));

            BeforeSaveResult result = hook.beforeCreate("users", Map.of(), "tenant-1");

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("MODIFY_ALL_DATA admits writes to user-permission-sets")
        void modifyAllDataIsAdmitted() {
            bindRequestWithProfile("profile-1");
            stubPermissions(Map.of("permission_name", "MODIFY_ALL_DATA", "granted", true));

            BeforeSaveResult result = hook.beforeUpdate(
                    "user-permission-sets", "ups-1", Map.of(), Map.of(), "tenant-1");

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("DelegatedWriteContext.runAuthorized admits an otherwise-blocked write")
        void delegatedWriteContextIsAdmitted() {
            bindRequestWithProfile("profile-1");
            BeforeSaveResult[] result = new BeforeSaveResult[1];

            DelegatedWriteContext.runAuthorized(
                    () -> result[0] = hook.beforeCreate("users", Map.of(), "tenant-1"));

            assertThat(result[0].isSuccess()).isTrue();
            verifyNoInteractions(bootstrapRepository);
        }
    }

    @Nested
    @DisplayName("Blocked paths")
    class BlockedPaths {

        @Test
        @DisplayName("identity without MANAGE_USERS or MODIFY_ALL_DATA is blocked on all "
                + "identity collections for create, update, and delete")
        void unprivilegedIdentityIsBlocked() {
            bindRequestWithProfile("profile-1");
            stubPermissions(
                    Map.of("permission_name", "API_ACCESS", "granted", true),
                    Map.of("permission_name", "MANAGE_USERS", "granted", false));

            for (String collection : List.of("users", "user-permission-sets", "group-memberships")) {
                assertBlocked(hook.beforeCreate(collection, Map.of(), "tenant-1"), collection);
                assertBlocked(hook.beforeUpdate(collection, "rec-1", Map.of(), Map.of(), "tenant-1"),
                        collection);
                assertBlocked(hook.beforeDelete(collection, "rec-1", "tenant-1"), collection);
            }
        }

        @Test
        @DisplayName("delegated-admin-scopes requires MANAGE_DELEGATED_ADMINS — MANAGE_USERS "
                + "alone is blocked")
        void manageUsersAloneIsBlockedOnDelegatedAdminScopes() {
            bindRequestWithProfile("profile-1");
            stubPermissions(Map.of("permission_name", "MANAGE_USERS", "granted", true));

            assertBlocked(hook.beforeCreate("delegated-admin-scopes", Map.of(), "tenant-1"),
                    "delegated-admin-scopes");
        }

        @Test
        @DisplayName("MANAGE_DELEGATED_ADMINS admits writes to delegated-admin-scopes")
        void manageDelegatedAdminsIsAdmittedOnDelegatedAdminScopes() {
            bindRequestWithProfile("profile-1");
            stubPermissions(Map.of("permission_name", "MANAGE_DELEGATED_ADMINS", "granted", true));

            BeforeSaveResult result =
                    hook.beforeCreate("delegated-admin-scopes", Map.of(), "tenant-1");

            assertThat(result.isSuccess()).isTrue();
        }

        private void assertBlocked(BeforeSaveResult result, String collection) {
            assertThat(result.isSuccess()).as("write to %s should be blocked", collection).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).message())
                    .contains("Insufficient permissions to modify " + collection);
        }
    }

    @Nested
    @DisplayName("Registration contract")
    class RegistrationContract {

        @Test
        @DisplayName("runs before all other hooks (negative order)")
        void runsBeforeOtherHooks() {
            assertThat(hook.getOrder()).isLessThan(0);
        }

        @Test
        @DisplayName("registers as a wildcard hook")
        void registersAsWildcard() {
            assertThat(hook.getCollectionName()).isEqualTo(BeforeSaveHookRegistry.WILDCARD);
        }
    }
}
