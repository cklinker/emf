package io.kelta.worker.service.delegated;

import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.repository.DelegatedAdminScopeRepository;
import io.kelta.worker.service.delegated.DelegatedAdminService.EffectiveDelegatedScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DelegatedAdminService")
class DelegatedAdminServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String CALLER_EMAIL = "delegate@example.com";
    private static final String CALLER_ID = "user-1";

    @Mock
    private DelegatedAdminScopeRepository scopeRepository;

    @Mock
    private BootstrapRepository bootstrapRepository;

    private DelegatedAdminService service;

    @BeforeEach
    void setUp() {
        service = new DelegatedAdminService(scopeRepository, bootstrapRepository, new ObjectMapper());
    }

    /** Builds a scope row the way {@code DelegatedAdminScopeRepository.findActiveScopes} returns
     * it: JSONB array columns normalized to their JSON string form. */
    private static Map<String, Object> scopeRow(String delegatedUserIdsJson,
                                                String manageableProfileIdsJson,
                                                boolean canCreate,
                                                boolean canDeactivate,
                                                boolean canReset) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "scope-" + System.identityHashCode(delegatedUserIdsJson));
        row.put("name", "Scope");
        row.put("delegated_user_ids", delegatedUserIdsJson);
        row.put("manageable_profile_ids", manageableProfileIdsJson);
        row.put("can_create_users", canCreate);
        row.put("can_deactivate_users", canDeactivate);
        row.put("can_reset_passwords", canReset);
        return row;
    }

    private void stubCallerResolvesTo(String userId) {
        when(bootstrapRepository.findUserByEmailAnyStatus(CALLER_EMAIL, TENANT))
                .thenReturn(Optional.of(Map.of("id", userId)));
    }

    @Test
    @DisplayName("unknown caller email resolves to NONE")
    void unknownCallerEmailIsNone() {
        when(bootstrapRepository.findUserByEmailAnyStatus(CALLER_EMAIL, TENANT))
                .thenReturn(Optional.empty());

        EffectiveDelegatedScope scope = service.effectiveScope(CALLER_EMAIL, TENANT);

        assertThat(scope).isSameAs(EffectiveDelegatedScope.NONE);
        assertThat(scope.delegated()).isFalse();
        verify(scopeRepository, never()).findActiveScopes(TENANT);
    }

    @Test
    @DisplayName("caller not listed in any scope's delegated_user_ids resolves to NONE")
    void callerNotInAnyScopeIsNone() {
        stubCallerResolvesTo(CALLER_ID);
        when(scopeRepository.findActiveScopes(TENANT)).thenReturn(List.of(
                scopeRow("[\"someone-else\"]", "[\"p1\"]", true, true, true)));

        EffectiveDelegatedScope scope = service.effectiveScope(CALLER_EMAIL, TENANT);

        assertThat(scope).isSameAs(EffectiveDelegatedScope.NONE);
        // Not delegated → the privileged re-filter never runs.
        verify(scopeRepository, never()).findPrivilegedProfileIds(anyCollection());
    }

    @Test
    @DisplayName("union across two scopes ORs capabilities and unions profile sets")
    void unionAcrossTwoScopes() {
        stubCallerResolvesTo(CALLER_ID);
        when(scopeRepository.findActiveScopes(TENANT)).thenReturn(List.of(
                scopeRow("[\"user-1\"]", "[\"p1\"]", true, false, false),
                scopeRow("[\"user-1\",\"user-9\"]", "[\"p2\"]", false, true, false)));
        when(scopeRepository.findPrivilegedProfileIds(Set.of("p1", "p2"))).thenReturn(Set.of());

        EffectiveDelegatedScope scope = service.effectiveScope(CALLER_EMAIL, TENANT);

        assertThat(scope.delegated()).isTrue();
        assertThat(scope.canCreateUsers()).isTrue();
        assertThat(scope.canDeactivateUsers()).isTrue();
        assertThat(scope.canResetPasswords()).isFalse();
        assertThat(scope.manageableProfileIds()).containsExactlyInAnyOrder("p1", "p2");
        assertThat(scope.canManageProfile("p1")).isTrue();
    }

    @Test
    @DisplayName("runtime re-filter drops now-privileged profiles")
    void runtimeReFilterDropsPrivilegedEntries() {
        stubCallerResolvesTo(CALLER_ID);
        when(scopeRepository.findActiveScopes(TENANT)).thenReturn(List.of(
                scopeRow("[\"user-1\"]", "[\"p1\",\"p2\"]", true, false, true)));
        when(scopeRepository.findPrivilegedProfileIds(Set.of("p1", "p2"))).thenReturn(Set.of("p2"));

        EffectiveDelegatedScope scope = service.effectiveScope(CALLER_EMAIL, TENANT);

        assertThat(scope.delegated()).isTrue();
        assertThat(scope.manageableProfileIds()).containsExactly("p1");
        assertThat(scope.canManageProfile("p2")).isFalse();
    }

    @Test
    @DisplayName("parseIdArray tolerates malformed JSON without throwing")
    void parseIdArrayToleratesMalformedJson() {
        assertThatCode(() -> service.parseIdArray("{not-valid-json")).doesNotThrowAnyException();
        assertThat(service.parseIdArray("{not-valid-json")).isEmpty();
        assertThat(service.parseIdArray("\"a-string-not-an-array\"")).isEmpty();
        assertThat(service.parseIdArray(null)).isEmpty();
    }

    @Test
    @DisplayName("parseIdArray skips non-string and blank entries")
    void parseIdArraySkipsNonStringEntries() {
        Set<String> ids = service.parseIdArray("[\"a\", 42, null, true, \"\", \"  \", \"b\"]");

        assertThat(ids).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("scope rows carrying JSONB columns as JSON strings resolve membership")
    void jsonbColumnsAsJsonStrings() {
        stubCallerResolvesTo(CALLER_ID);
        Map<String, Object> row = scopeRow("[\"user-1\"]", "[\"p1\"]", false, false, true);
        assertThat(row.get("delegated_user_ids")).isInstanceOf(String.class);
        when(scopeRepository.findActiveScopes(TENANT)).thenReturn(List.of(row));
        when(scopeRepository.findPrivilegedProfileIds(Set.of("p1"))).thenReturn(Set.of());

        EffectiveDelegatedScope scope = service.effectiveScope(CALLER_EMAIL, TENANT);

        assertThat(scope.delegated()).isTrue();
        assertThat(scope.canResetPasswords()).isTrue();
        assertThat(scope.canCreateUsers()).isFalse();
        assertThat(scope.manageableProfileIds()).containsExactly("p1");
    }

    @Test
    @DisplayName("blank email or tenant resolves to NONE without hitting the repository")
    void blankInputsResolveToNone() {
        assertThat(service.effectiveScope(null, TENANT)).isSameAs(EffectiveDelegatedScope.NONE);
        assertThat(service.effectiveScope("  ", TENANT)).isSameAs(EffectiveDelegatedScope.NONE);
        assertThat(service.effectiveScope(CALLER_EMAIL, null)).isSameAs(EffectiveDelegatedScope.NONE);
        verify(scopeRepository, never()).findActiveScopes(TENANT);
    }
}
