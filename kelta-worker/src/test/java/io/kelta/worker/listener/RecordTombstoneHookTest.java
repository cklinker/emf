package io.kelta.worker.listener;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.repository.RecordTombstoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordTombstoneHook")
class RecordTombstoneHookTest {

    @Mock
    private RecordTombstoneRepository tombstoneRepository;

    @Mock
    private CollectionRegistry collectionRegistry;

    private RecordTombstoneHook hook;

    @BeforeEach
    void setUp() {
        hook = new RecordTombstoneHook(tombstoneRepository, collectionRegistry);
    }

    @Test
    @DisplayName("records a tombstone when a user-collection record is deleted")
    void recordsForUserCollection() {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(def.systemCollection()).thenReturn(false);
        when(collectionRegistry.get("orders")).thenReturn(def);

        hook.afterDelete("orders", "rec-1", "tenant-1");

        verify(tombstoneRepository).record("tenant-1", "orders", "rec-1");
    }

    @Test
    @DisplayName("skips system collections (not part of offline data)")
    void skipsSystemCollection() {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(def.systemCollection()).thenReturn(true);
        when(collectionRegistry.get("flows")).thenReturn(def);

        hook.afterDelete("flows", "f1", "tenant-1");

        verify(tombstoneRepository, never()).record(any(), any(), any());
    }

    @Test
    @DisplayName("is a wildcard hook running after attachment cleanup")
    void wildcardAndOrder() {
        assertThat(hook.getCollectionName()).isEqualTo("*");
        assertThat(hook.getOrder()).isEqualTo(250);
    }

    @Test
    @DisplayName("ignores incomplete delete callbacks")
    void nullArgsNoOp() {
        hook.afterDelete(null, "x", "tenant-1");
        hook.afterDelete("orders", null, "tenant-1");
        hook.afterDelete("orders", "x", null);
        verifyNoInteractions(tombstoneRepository, collectionRegistry);
    }
}
