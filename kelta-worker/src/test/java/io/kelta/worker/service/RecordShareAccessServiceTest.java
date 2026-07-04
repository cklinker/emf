package io.kelta.worker.service;

import io.kelta.worker.repository.RecordShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordShareAccessService")
class RecordShareAccessServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String USER_ID = "user-1";

    @Mock
    private RecordShareRepository repository;

    private RecordShareAccessService service;

    @BeforeEach
    void setUp() {
        service = new RecordShareAccessService(repository);
        lenient().when(repository.findUserIdByEmail(EMAIL)).thenReturn(USER_ID);
        lenient().when(repository.findGroupIdsForUser(USER_ID)).thenReturn(List.of());
    }

    @Test
    @DisplayName("READ share widens read access")
    void readShareWidensRead() {
        when(repository.findSharesForPrincipal(anyString(), anyCollection(), anyString(), anyCollection()))
                .thenReturn(List.of(Map.of("recordId", "r1", "accessLevel", "READ")));

        Set<String> widened = service.widen(EMAIL, "contacts", Set.of("r1", "r2"), "read");

        assertThat(widened).containsExactly("r1");
    }

    @Test
    @DisplayName("READ share does NOT widen edit access")
    void readShareDoesNotWidenEdit() {
        when(repository.findSharesForPrincipal(anyString(), anyCollection(), anyString(), anyCollection()))
                .thenReturn(List.of(Map.of("recordId", "r1", "accessLevel", "READ")));

        assertThat(service.widen(EMAIL, "contacts", Set.of("r1"), "edit")).isEmpty();
    }

    @Test
    @DisplayName("EDIT share widens both read and edit")
    void editShareWidensReadAndEdit() {
        when(repository.findSharesForPrincipal(anyString(), anyCollection(), anyString(), anyCollection()))
                .thenReturn(List.of(Map.of("recordId", "r1", "accessLevel", "EDIT")));

        assertThat(service.widen(EMAIL, "contacts", Set.of("r1"), "read")).containsExactly("r1");
        assertThat(service.widen(EMAIL, "contacts", Set.of("r1"), "edit")).containsExactly("r1");
    }

    @Test
    @DisplayName("shares never widen create or delete")
    void neverWidensCreateOrDelete() {
        assertThat(service.widen(EMAIL, "contacts", Set.of("r1"), "create")).isEmpty();
        assertThat(service.widen(EMAIL, "contacts", Set.of("r1"), "delete")).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("unknown user email → no widening")
    void unknownUserNoWidening() {
        when(repository.findUserIdByEmail("ghost@example.com")).thenReturn(null);

        assertThat(service.widen("ghost@example.com", "contacts", Set.of("r1"), "read")).isEmpty();
    }

    @Test
    @DisplayName("empty denied set short-circuits with no lookups")
    void emptyDeniedShortCircuits() {
        assertThat(service.widen(EMAIL, "contacts", Set.of(), "read")).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("group membership is passed through to the share lookup")
    void groupShareLookupUsesGroups() {
        when(repository.findGroupIdsForUser(USER_ID)).thenReturn(List.of("g1", "g2"));
        when(repository.findSharesForPrincipal("contacts", Set.of("r1"), USER_ID, List.of("g1", "g2")))
                .thenReturn(List.of(Map.of("recordId", "r1", "accessLevel", "READ")));

        assertThat(service.widen(EMAIL, "contacts", Set.of("r1"), "read")).containsExactly("r1");
    }

    @Test
    @DisplayName("repository failure fails closed — no widening, no throw")
    void repositoryFailureFailsClosed() {
        when(repository.findUserIdByEmail(any())).thenThrow(new RuntimeException("db down"));

        assertThat(service.widen(EMAIL, "contacts", Set.of("r1"), "read")).isEmpty();
    }
}
