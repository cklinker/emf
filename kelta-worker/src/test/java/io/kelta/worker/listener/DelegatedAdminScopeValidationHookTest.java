package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.repository.DelegatedAdminScopeRepository;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DelegatedAdminScopeValidationHook")
class DelegatedAdminScopeValidationHookTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private DelegatedAdminScopeRepository scopeRepository;

    private DelegatedAdminScopeValidationHook hook;

    @BeforeEach
    void setUp() {
        hook = new DelegatedAdminScopeValidationHook(scopeRepository, new ObjectMapper());
    }

    private static void assertError(BeforeSaveResult result, String field) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).field()).isEqualTo(field);
    }

    @Test
    @DisplayName("targets the delegated-admin-scopes collection")
    void collectionName() {
        assertThat(hook.getCollectionName()).isEqualTo("delegated-admin-scopes");
    }

    @Test
    @DisplayName("clean scope with all ids existing passes and normalizes arrays to List")
    void cleanScopePassesAndNormalizes() {
        Map<String, Object> record = new HashMap<>();
        record.put("delegatedUserIds", List.of("u1", "u2", "u1")); // duplicate deduped
        record.put("manageableProfileIds", List.of("p1"));
        when(scopeRepository.findExistingUserIds(Set.of("u1", "u2"), TENANT)).thenReturn(Set.of("u1", "u2"));
        when(scopeRepository.findExistingProfileIds(Set.of("p1"), TENANT)).thenReturn(Set.of("p1"));
        when(scopeRepository.findPrivilegedProfileIds(Set.of("p1"))).thenReturn(Set.of());

        BeforeSaveResult result = hook.beforeCreate(record, TENANT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(record.get("delegatedUserIds")).isInstanceOf(List.class);
        assertThat(idList(record, "delegatedUserIds")).containsExactlyInAnyOrder("u1", "u2");
        assertThat(idList(record, "manageableProfileIds")).containsExactly("p1");
    }

    @Test
    @DisplayName("privileged profile in manageableProfileIds is rejected on that field")
    void privilegedProfileRejected() {
        Map<String, Object> record = new HashMap<>();
        record.put("manageableProfileIds", List.of("p1", "p-admin"));
        when(scopeRepository.findExistingProfileIds(Set.of("p1", "p-admin"), TENANT))
                .thenReturn(Set.of("p1", "p-admin"));
        when(scopeRepository.findPrivilegedProfileIds(Set.of("p1", "p-admin")))
                .thenReturn(Set.of("p-admin"));

        BeforeSaveResult result = hook.beforeCreate(record, TENANT);

        assertError(result, "manageableProfileIds");
        assertThat(result.getErrors().get(0).message()).contains("p-admin");
    }

    @Test
    @DisplayName("unknown user id is rejected")
    void unknownUserIdRejected() {
        Map<String, Object> record = new HashMap<>();
        record.put("delegatedUserIds", List.of("u1", "u-ghost"));
        when(scopeRepository.findExistingUserIds(Set.of("u1", "u-ghost"), TENANT))
                .thenReturn(Set.of("u1"));

        BeforeSaveResult result = hook.beforeCreate(record, TENANT);

        assertError(result, "delegatedUserIds");
        assertThat(result.getErrors().get(0).message()).contains("u-ghost");
    }

    @Test
    @DisplayName("non-array value is rejected")
    void nonArrayValueRejected() {
        Map<String, Object> record = new HashMap<>();
        record.put("delegatedUserIds", 42);

        BeforeSaveResult result = hook.beforeCreate(record, TENANT);

        assertError(result, "delegatedUserIds");
        verifyNoInteractions(scopeRepository);
    }

    @Test
    @DisplayName("unparseable JSON string value is rejected")
    void unparseableJsonStringRejected() {
        Map<String, Object> record = new HashMap<>();
        record.put("manageableProfileIds", "{not-an-array");

        BeforeSaveResult result = hook.beforeCreate(record, TENANT);

        assertError(result, "manageableProfileIds");
        verifyNoInteractions(scopeRepository);
    }

    @Test
    @DisplayName("blank entry in the array is rejected")
    void blankEntryRejected() {
        Map<String, Object> record = new HashMap<>();
        record.put("delegatedUserIds", List.of("u1", "  "));

        BeforeSaveResult result = hook.beforeCreate(record, TENANT);

        assertError(result, "delegatedUserIds");
        verifyNoInteractions(scopeRepository);
    }

    @Test
    @DisplayName("oversized entry in the array is rejected")
    void oversizedEntryRejected() {
        Map<String, Object> record = new HashMap<>();
        record.put("delegatedUserIds", List.of("x".repeat(65)));

        BeforeSaveResult result = hook.beforeCreate(record, TENANT);

        assertError(result, "delegatedUserIds");
        verifyNoInteractions(scopeRepository);
    }

    @Test
    @DisplayName("non-string entry in the array is rejected")
    void nonStringEntryRejected() {
        Map<String, Object> record = new HashMap<>();
        record.put("delegatedUserIds", List.of("u1", 7));

        BeforeSaveResult result = hook.beforeCreate(record, TENANT);

        assertError(result, "delegatedUserIds");
        verifyNoInteractions(scopeRepository);
    }

    @Test
    @DisplayName("over-cap user list (501 ids) is rejected")
    void overCapRejected() {
        List<String> ids = IntStream.rangeClosed(1, DelegatedAdminScopeValidationHook.MAX_USERS + 1)
                .mapToObj(i -> "user-" + i)
                .collect(Collectors.toList());
        Map<String, Object> record = new HashMap<>();
        record.put("delegatedUserIds", ids);

        BeforeSaveResult result = hook.beforeCreate(record, TENANT);

        assertError(result, "delegatedUserIds");
        assertThat(result.getErrors().get(0).message())
                .contains(String.valueOf(DelegatedAdminScopeValidationHook.MAX_USERS));
        verifyNoInteractions(scopeRepository);
    }

    @Test
    @DisplayName("absent fields are not validated on partial update")
    void absentFieldsNotValidated() {
        Map<String, Object> record = new HashMap<>();
        record.put("name", "Renamed scope only");

        BeforeSaveResult result = hook.beforeUpdate("scope-1", record, Map.of(), TENANT);

        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(scopeRepository);
    }

    @Test
    @DisplayName("accepts JSON string form of the array and normalizes it to List")
    void jsonStringFormAccepted() {
        Map<String, Object> record = new HashMap<>();
        record.put("delegatedUserIds", "[\"a\"]");
        when(scopeRepository.findExistingUserIds(Set.of("a"), TENANT)).thenReturn(Set.of("a"));

        BeforeSaveResult result = hook.beforeCreate(record, TENANT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(record.get("delegatedUserIds")).isInstanceOf(List.class);
        assertThat(idList(record, "delegatedUserIds")).containsExactly("a");
    }

    @SuppressWarnings("unchecked")
    private static List<String> idList(Map<String, Object> record, String key) {
        return (List<String>) record.get(key);
    }
}
