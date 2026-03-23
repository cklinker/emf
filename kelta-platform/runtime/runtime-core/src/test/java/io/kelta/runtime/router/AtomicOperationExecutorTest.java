package io.kelta.runtime.router;

import io.kelta.jsonapi.AtomicOperation;
import io.kelta.jsonapi.AtomicResult;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AtomicOperationExecutor Tests")
class AtomicOperationExecutorTest {

    @Mock private QueryEngine queryEngine;
    @Mock private CollectionRegistry collectionRegistry;
    @Mock private CollectionDefinition contactsDef;

    private AtomicOperationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AtomicOperationExecutor(queryEngine, collectionRegistry);
        lenient().when(collectionRegistry.get("contacts")).thenReturn(contactsDef);
    }

    @Nested
    @DisplayName("Add Operations")
    class AddOperations {
        @Test
        void shouldCreateRecordAndReturnResult() {
            var op = new AtomicOperation("add", null,
                    new AtomicOperation.ResourceData("contacts", null, null,
                            Map.of("name", "John", "email", "john@example.com"), null));

            when(queryEngine.create(eq(contactsDef), anyMap()))
                    .thenReturn(Map.of("id", "server-1", "name", "John", "email", "john@example.com"));

            List<AtomicResult> results = executor.execute(List.of(op));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).data()).isNotNull();
            assertThat(results.get(0).data().type()).isEqualTo("contacts");
            assertThat(results.get(0).data().id()).isEqualTo("server-1");
        }

        @Test
        void shouldTrackLidToIdMapping() {
            var addOp = new AtomicOperation("add", null,
                    new AtomicOperation.ResourceData("contacts", null, "temp-1",
                            Map.of("name", "John"), null));

            when(queryEngine.create(eq(contactsDef), anyMap()))
                    .thenReturn(Map.of("id", "server-1", "name", "John"));

            var updateOp = new AtomicOperation("update",
                    new AtomicOperation.ResourceRef("contacts", null, "temp-1"),
                    new AtomicOperation.ResourceData("contacts", null, null,
                            Map.of("name", "Updated John"), null));

            when(queryEngine.update(eq(contactsDef), eq("server-1"), anyMap()))
                    .thenReturn(Optional.of(Map.of("id", "server-1", "name", "Updated John")));

            List<AtomicResult> results = executor.execute(List.of(addOp, updateOp));

            assertThat(results).hasSize(2);
            assertThat(results.get(0).data().lid()).isEqualTo("temp-1");
            assertThat(results.get(1).data().id()).isEqualTo("server-1");
        }
    }

    @Nested
    @DisplayName("Update Operations")
    class UpdateOperations {
        @Test
        void shouldUpdateAndReturnResult() {
            var op = new AtomicOperation("update",
                    new AtomicOperation.ResourceRef("contacts", "123", null),
                    new AtomicOperation.ResourceData("contacts", null, null,
                            Map.of("name", "Updated"), null));

            when(queryEngine.update(eq(contactsDef), eq("123"), anyMap()))
                    .thenReturn(Optional.of(Map.of("id", "123", "name", "Updated")));

            List<AtomicResult> results = executor.execute(List.of(op));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).data().id()).isEqualTo("123");
        }

        @Test
        void shouldThrowWhenRecordNotFound() {
            var op = new AtomicOperation("update",
                    new AtomicOperation.ResourceRef("contacts", "missing", null),
                    new AtomicOperation.ResourceData("contacts", null, null,
                            Map.of("name", "Updated"), null));

            when(queryEngine.update(eq(contactsDef), eq("missing"), anyMap()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> executor.execute(List.of(op)))
                    .isInstanceOf(AtomicOperationExecutor.AtomicOperationException.class)
                    .hasMessageContaining("Record not found");
        }
    }

    @Nested
    @DisplayName("Remove Operations")
    class RemoveOperations {
        @Test
        void shouldDeleteAndReturnEmptyResult() {
            var op = new AtomicOperation("remove",
                    new AtomicOperation.ResourceRef("contacts", "123", null), null);

            when(queryEngine.delete(eq(contactsDef), eq("123"))).thenReturn(true);

            List<AtomicResult> results = executor.execute(List.of(op));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).data()).isNull();
        }

        @Test
        void shouldThrowWhenDeleteFails() {
            var op = new AtomicOperation("remove",
                    new AtomicOperation.ResourceRef("contacts", "missing", null), null);

            when(queryEngine.delete(eq(contactsDef), eq("missing"))).thenReturn(false);

            assertThatThrownBy(() -> executor.execute(List.of(op)))
                    .isInstanceOf(AtomicOperationExecutor.AtomicOperationException.class)
                    .hasMessageContaining("Record not found");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {
        @Test
        void shouldRejectUnknownOperationType() {
            var op = new AtomicOperation("merge", null,
                    new AtomicOperation.ResourceData("contacts", null, null, Map.of(), null));

            assertThatThrownBy(() -> executor.execute(List.of(op)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown op 'merge'");
        }

        @Test
        void shouldRejectMissingRefOnUpdate() {
            var op = new AtomicOperation("update", null,
                    new AtomicOperation.ResourceData("contacts", null, null, Map.of(), null));

            assertThatThrownBy(() -> executor.execute(List.of(op)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'update' requires 'ref'");
        }

        @Test
        void shouldRejectMissingDataOnAdd() {
            var op = new AtomicOperation("add", null, null);

            assertThatThrownBy(() -> executor.execute(List.of(op)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'add' requires 'data'");
        }

        @Test
        void shouldRejectUnknownCollectionType() {
            var op = new AtomicOperation("add", null,
                    new AtomicOperation.ResourceData("nonexistent", null, null, Map.of(), null));

            when(collectionRegistry.get("nonexistent")).thenReturn(null);

            assertThatThrownBy(() -> executor.execute(List.of(op)))
                    .isInstanceOf(AtomicOperationExecutor.AtomicOperationException.class)
                    .hasMessageContaining("Unknown collection type");
        }

        @Test
        void shouldRejectUnresolvedLid() {
            var op = new AtomicOperation("update",
                    new AtomicOperation.ResourceRef("contacts", null, "nonexistent-lid"),
                    new AtomicOperation.ResourceData("contacts", null, null, Map.of(), null));

            assertThatThrownBy(() -> executor.execute(List.of(op)))
                    .isInstanceOf(AtomicOperationExecutor.AtomicOperationException.class)
                    .hasMessageContaining("Local ID 'nonexistent-lid' not found");
        }
    }

    @Nested
    @DisplayName("Error Propagation")
    class ErrorPropagation {
        @Test
        void shouldIncludeOperationIndexInException() {
            var op1 = new AtomicOperation("add", null,
                    new AtomicOperation.ResourceData("contacts", null, null, Map.of("name", "A"), null));
            var op2 = new AtomicOperation("add", null,
                    new AtomicOperation.ResourceData("contacts", null, null, Map.of("name", "B"), null));

            when(queryEngine.create(eq(contactsDef), anyMap()))
                    .thenReturn(Map.of("id", "1", "name", "A"))
                    .thenThrow(new RuntimeException("Validation failed"));

            assertThatThrownBy(() -> executor.execute(List.of(op1, op2)))
                    .isInstanceOf(AtomicOperationExecutor.AtomicOperationException.class)
                    .satisfies(ex -> {
                        var aoe = (AtomicOperationExecutor.AtomicOperationException) ex;
                        assertThat(aoe.getOperationIndex()).isEqualTo(1);
                    });
        }
    }
}
