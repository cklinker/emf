package io.kelta.ai.service;

import io.kelta.ai.model.AgentDefinition;
import io.kelta.ai.repository.AgentDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentService")
class AgentServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user-1";

    @Mock
    private AgentDefinitionRepository repository;

    private AgentService service() {
        return new AgentService(repository);
    }

    private static AgentUpsertRequest valid() {
        return new AgentUpsertRequest("Support Bot", "Answers tickets", "You are helpful.",
                "claude-sonnet-4-6", 2048, List.of("search", "get_record"), 500_000L, true);
    }

    @Test
    @DisplayName("create: validates, builds, saves, and returns the agent")
    void createSaves() {
        AgentDefinition created = service().create(TENANT, USER, valid());

        ArgumentCaptor<AgentDefinition> saved = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(repository).save(saved.capture());
        AgentDefinition a = saved.getValue();
        assertThat(a).isSameAs(created);
        assertThat(a.tenantId()).isEqualTo(TENANT);
        assertThat(a.name()).isEqualTo("Support Bot");
        assertThat(a.allowedTools()).containsExactly("search", "get_record");
        assertThat(a.enabled()).isTrue();
        assertThat(a.createdBy()).isEqualTo(USER);
        assertThat(a.id()).isNotNull();
    }

    @Test
    @DisplayName("create: null enabled defaults to true")
    void createDefaultsEnabled() {
        AgentUpsertRequest req = new AgentUpsertRequest("A", null, "prompt", null, null, null, null, null);

        AgentDefinition created = service().create(TENANT, USER, req);

        assertThat(created.enabled()).isTrue();
        assertThat(created.allowedTools()).isEmpty();
        assertThat(created.model()).isNull();
    }

    @Test
    @DisplayName("create: blank name is rejected")
    void rejectsBlankName() {
        AgentUpsertRequest req = new AgentUpsertRequest("  ", null, "prompt", null, null, null, null, true);
        assertThatThrownBy(() -> service().create(TENANT, USER, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("create: blank systemPrompt is rejected")
    void rejectsBlankPrompt() {
        AgentUpsertRequest req = new AgentUpsertRequest("A", null, "  ", null, null, null, null, true);
        assertThatThrownBy(() -> service().create(TENANT, USER, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("systemPrompt");
    }

    @Test
    @DisplayName("create: non-positive maxTokens is rejected")
    void rejectsBadMaxTokens() {
        AgentUpsertRequest req = new AgentUpsertRequest("A", null, "prompt", null, 0, null, null, true);
        assertThatThrownBy(() -> service().create(TENANT, USER, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTokens");
    }

    @Test
    @DisplayName("create: tool list is de-duplicated and blanks dropped")
    void normalizesTools() {
        AgentUpsertRequest req = new AgentUpsertRequest("A", null, "prompt", null, null,
                List.of("search", " ", "search", "get_record"), null, true);

        AgentDefinition created = service().create(TENANT, USER, req);

        assertThat(created.allowedTools()).containsExactly("search", "get_record");
    }

    @Test
    @DisplayName("update: returns empty when the agent does not exist")
    void updateMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id, TENANT)).thenReturn(Optional.empty());

        Optional<AgentDefinition> result = service().update(TENANT, USER, id, valid());

        assertThat(result).isEmpty();
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("update: preserves id/createdAt/createdBy, applies new fields and updatedBy")
    void updatePreservesIdentity() {
        AgentDefinition existing = AgentDefinition.create(TENANT, "Old", "d", "old prompt",
                null, null, List.of("a"), null, true, "creator");
        when(repository.findById(existing.id(), TENANT)).thenReturn(Optional.of(existing));

        AgentUpsertRequest req = new AgentUpsertRequest("New", "nd", "new prompt", "claude-sonnet-4-6",
                4096, List.of("b"), 1000L, false);
        Optional<AgentDefinition> result = service().update(TENANT, "editor", existing.id(), req);

        assertThat(result).isPresent();
        AgentDefinition u = result.get();
        assertThat(u.id()).isEqualTo(existing.id());
        assertThat(u.createdBy()).isEqualTo("creator");
        assertThat(u.createdAt()).isEqualTo(existing.createdAt());
        assertThat(u.name()).isEqualTo("New");
        assertThat(u.enabled()).isFalse();
        assertThat(u.allowedTools()).containsExactly("b");
        assertThat(u.updatedBy()).isEqualTo("editor");
        verify(repository).save(u);
    }

    @Test
    @DisplayName("delete delegates to the repository")
    void deleteDelegates() {
        UUID id = UUID.randomUUID();
        when(repository.deleteById(id, TENANT)).thenReturn(true);
        assertThat(service().delete(TENANT, id)).isTrue();
    }

    @Test
    @DisplayName("list/get delegate to the repository")
    void listGetDelegate() {
        UUID id = UUID.randomUUID();
        when(repository.findByTenant(TENANT)).thenReturn(List.of());
        when(repository.findById(id, TENANT)).thenReturn(Optional.empty());
        assertThat(service().list(TENANT)).isEmpty();
        assertThat(service().get(TENANT, id)).isEmpty();
    }
}
