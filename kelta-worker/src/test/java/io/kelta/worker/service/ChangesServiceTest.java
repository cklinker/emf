package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.repository.RecordTombstoneRepository;
import io.kelta.worker.repository.RecordTombstoneRepository.Tombstone;
import io.kelta.worker.service.ChangesService.ChangesException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangesService")
class ChangesServiceTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private CollectionRegistry collectionRegistry;

    @Mock
    private RecordTombstoneRepository tombstoneRepository;

    private ChangesService service;

    @BeforeEach
    void setUp() {
        service = new ChangesService(collectionRegistry, tombstoneRepository);
    }

    @Test
    @DisplayName("throws for an unknown collection")
    void unknownCollection() {
        when(collectionRegistry.get("nope")).thenReturn(null);
        assertThatThrownBy(() -> service.changes(TENANT, "nope", Instant.now()))
                .isInstanceOf(ChangesException.class);
    }

    @Test
    @DisplayName("initial sync (no cursor) reports no deletions and never queries the log")
    void initialSyncNoDeletions() {
        when(collectionRegistry.get("orders")).thenReturn(mock(CollectionDefinition.class));

        Map<String, Object> result = service.changes(TENANT, "orders", null);

        assertThat(result.get("deletions")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
        assertThat(result.get("cursor")).isNotNull();
        verify(tombstoneRepository, never()).findSince(any(), any(), any());
    }

    @Test
    @DisplayName("incremental sync returns the deleted record ids since the cursor")
    void incrementalDeletions() {
        when(collectionRegistry.get("orders")).thenReturn(mock(CollectionDefinition.class));
        Instant since = Instant.parse("2026-06-01T00:00:00Z");
        when(tombstoneRepository.findSince(eq(TENANT), eq("orders"), eq(since)))
                .thenReturn(List.of(new Tombstone("r1", Instant.now()), new Tombstone("r2", Instant.now())));

        Map<String, Object> result = service.changes(TENANT, "orders", since);

        assertThat(result.get("deletions")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .containsExactly("r1", "r2");
        assertThat(result.get("deletionCount")).isEqualTo(2);
        assertThat(result.get("cursor")).isNotNull();
    }
}
