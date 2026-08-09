package io.kelta.runtime.query;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.storage.StorageAdapter;
import io.kelta.runtime.validation.FieldError;
import io.kelta.runtime.validation.ValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for read-only collection enforcement and immutable field handling
 * in DefaultQueryEngine.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Read-only collections reject create, update, and delete operations</li>
 *   <li>Read-only collections still allow query (read) operations</li>
 *   <li>An update that would change an immutable field is rejected, not silently dropped</li>
 *   <li>An unchanged immutable field is accepted and left out of the persisted patch</li>
 *   <li>Non-immutable fields pass through normally</li>
 * </ul>
 */
class ReadOnlyCollectionTest {

    private StorageAdapter storageAdapter;
    private DefaultQueryEngine queryEngine;

    @BeforeEach
    void setUp() {
        storageAdapter = mock(StorageAdapter.class);
        queryEngine = new DefaultQueryEngine(storageAdapter);
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a read-only system collection definition (e.g., audit logs).
     */
    private CollectionDefinition buildReadOnlyCollection() {
        return new CollectionDefinitionBuilder()
                .name("audit-logs")
                .displayName("Audit Logs")
                .addField(FieldDefinition.string("action"))
                .addField(FieldDefinition.string("details"))
                .systemCollection(true)
                .readOnly(true)
                .build();
    }

    /**
     * Creates a writable collection with immutable fields.
     */
    private CollectionDefinition buildCollectionWithImmutableFields() {
        return new CollectionDefinitionBuilder()
                .name("users")
                .displayName("Users")
                .addField(FieldDefinition.requiredString("email"))
                .addField(FieldDefinition.string("name"))
                .addField(FieldDefinition.string("role"))
                .systemCollection(true)
                .addImmutableField("tenantId")
                .addImmutableField("email")
                .build();
    }

    /**
     * Creates a writable collection with no immutable fields.
     */
    private CollectionDefinition buildWritableCollection() {
        return new CollectionDefinitionBuilder()
                .name("products")
                .displayName("Products")
                .addField(FieldDefinition.requiredString("name"))
                .addField(FieldDefinition.doubleField("price"))
                .build();
    }

    // ==================== Read-Only Enforcement Tests ====================

    @Nested
    @DisplayName("Read-Only Collection Enforcement")
    class ReadOnlyEnforcementTests {

        @Test
        @DisplayName("Should throw ReadOnlyCollectionException on create for read-only collection")
        void create_throwsReadOnlyCollectionException_forReadOnlyCollection() {
            CollectionDefinition readOnlyDef = buildReadOnlyCollection();

            Map<String, Object> data = new HashMap<>();
            data.put("action", "LOGIN");
            data.put("details", "User logged in");

            ReadOnlyCollectionException ex = assertThrows(
                    ReadOnlyCollectionException.class,
                    () -> queryEngine.create(readOnlyDef, data));

            assertEquals("audit-logs", ex.getCollectionName());
            assertTrue(ex.getMessage().contains("read-only"));

            // Verify the storage adapter was never called
            verify(storageAdapter, never()).create(any(), any());
        }

        @Test
        @DisplayName("Should throw ReadOnlyCollectionException on update for read-only collection")
        void update_throwsReadOnlyCollectionException_forReadOnlyCollection() {
            CollectionDefinition readOnlyDef = buildReadOnlyCollection();

            Map<String, Object> data = new HashMap<>();
            data.put("details", "Modified details");

            ReadOnlyCollectionException ex = assertThrows(
                    ReadOnlyCollectionException.class,
                    () -> queryEngine.update(readOnlyDef, "log-1", data));

            assertEquals("audit-logs", ex.getCollectionName());

            // Verify the storage adapter was never called
            verify(storageAdapter, never()).update(any(), any(), any());
            verify(storageAdapter, never()).getById(any(), any());
        }

        @Test
        @DisplayName("Should throw ReadOnlyCollectionException on delete for read-only collection")
        void delete_throwsReadOnlyCollectionException_forReadOnlyCollection() {
            CollectionDefinition readOnlyDef = buildReadOnlyCollection();

            ReadOnlyCollectionException ex = assertThrows(
                    ReadOnlyCollectionException.class,
                    () -> queryEngine.delete(readOnlyDef, "log-1"));

            assertEquals("audit-logs", ex.getCollectionName());

            // Verify the storage adapter was never called
            verify(storageAdapter, never()).delete(any(), any());
        }

        @Test
        @DisplayName("Should allow executeQuery on read-only collection (reads still work)")
        void executeQuery_succeeds_forReadOnlyCollection() {
            CollectionDefinition readOnlyDef = buildReadOnlyCollection();
            QueryRequest request = QueryRequest.defaults();
            QueryResult expectedResult = QueryResult.empty(request.pagination());

            when(storageAdapter.query(readOnlyDef, request)).thenReturn(expectedResult);

            QueryResult result = queryEngine.executeQuery(readOnlyDef, request);

            assertNotNull(result);
            assertEquals(expectedResult, result);
            verify(storageAdapter).query(readOnlyDef, request);
        }
    }

    // ==================== Immutable Field Tests ====================

    @Nested
    @DisplayName("Immutable Field Enforcement")
    class ImmutableFieldTests {

        @Test
        @DisplayName("Should reject an update that changes immutable fields, naming each one")
        void update_rejectsChangedImmutableFields() {
            CollectionDefinition usersDef = buildCollectionWithImmutableFields();
            String id = "user-1";

            Map<String, Object> existingRecord = new HashMap<>();
            existingRecord.put("id", id);
            existingRecord.put("email", "john@example.com");
            existingRecord.put("name", "John");
            existingRecord.put("tenantId", "tenant-1");

            // Attempt to update email (immutable), tenantId (immutable), and name (mutable)
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("email", "jane@example.com");      // immutable - should be rejected
            updateData.put("tenantId", "tenant-2");            // immutable - should be rejected
            updateData.put("name", "Jane");                     // mutable

            when(storageAdapter.getById(usersDef, id)).thenReturn(Optional.of(existingRecord));

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> queryEngine.update(usersDef, id, updateData));

            List<String> failedFields = exception.getValidationResult().errors().stream()
                    .map(FieldError::fieldName)
                    .toList();
            assertTrue(failedFields.containsAll(List.of("email", "tenantId")),
                    "Both immutable fields should be named in the error, was " + failedFields);
            assertTrue(exception.getValidationResult().errors().stream()
                            .allMatch(error -> "immutable".equals(error.constraint())),
                    "Errors should carry the immutable constraint");

            verify(storageAdapter, never()).update(eq(usersDef), eq(id), any());
        }

        @Test
        @DisplayName("Should accept an unchanged immutable field and leave it out of the patch")
        void update_acceptsUnchangedImmutableField() {
            CollectionDefinition usersDef = buildCollectionWithImmutableFields();
            String id = "user-1";

            Map<String, Object> existingRecord = new HashMap<>();
            existingRecord.put("id", id);
            existingRecord.put("email", "john@example.com");
            existingRecord.put("name", "John");
            existingRecord.put("tenantId", "tenant-1");

            // A full-record round-trip: immutable fields resent with the values they already hold
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("email", "john@example.com");
            updateData.put("tenantId", "tenant-1");
            updateData.put("name", "Jane");

            when(storageAdapter.getById(usersDef, id)).thenReturn(Optional.of(existingRecord));
            when(storageAdapter.update(eq(usersDef), eq(id), any()))
                    .thenAnswer(invocation -> Optional.of(invocation.getArgument(2)));

            queryEngine.update(usersDef, id, updateData);

            ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(storageAdapter).update(eq(usersDef), eq(id), dataCaptor.capture());

            Map<String, Object> persistedData = dataCaptor.getValue();
            assertFalse(persistedData.containsKey("email"),
                    "Unchanged immutable field should not be rewritten");
            assertFalse(persistedData.containsKey("tenantId"),
                    "Unchanged immutable field should not be rewritten");
            assertEquals("Jane", persistedData.get("name"));
        }

        @Test
        @DisplayName("Should treat a stored value that reads back as another type as unchanged")
        void update_acceptsImmutableField_whenStoredTypeDiffers() {
            CollectionDefinition usersDef = buildCollectionWithImmutableFields();
            String id = "user-1";
            UUID tenantId = UUID.fromString("11111111-2222-3333-4444-555555555555");

            Map<String, Object> existingRecord = new HashMap<>();
            existingRecord.put("id", id);
            existingRecord.put("email", "john@example.com");
            existingRecord.put("tenantId", tenantId);          // reads back as a UUID

            Map<String, Object> updateData = new HashMap<>();
            updateData.put("tenantId", tenantId.toString());   // resent as a String
            updateData.put("name", "Jane");

            when(storageAdapter.getById(usersDef, id)).thenReturn(Optional.of(existingRecord));
            when(storageAdapter.update(eq(usersDef), eq(id), any()))
                    .thenAnswer(invocation -> Optional.of(invocation.getArgument(2)));

            assertDoesNotThrow(() -> queryEngine.update(usersDef, id, updateData));
        }

        @Test
        @DisplayName("Should ignore a blank submission for an immutable field")
        void update_ignoresBlankImmutableField() {
            CollectionDefinition usersDef = buildCollectionWithImmutableFields();
            String id = "user-1";

            Map<String, Object> existingRecord = new HashMap<>();
            existingRecord.put("id", id);
            existingRecord.put("email", "john@example.com");
            existingRecord.put("tenantId", "tenant-1");

            // A form round-trip that coerced untouched inputs to null / ""
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("email", null);
            updateData.put("tenantId", "");
            updateData.put("name", "Jane");

            when(storageAdapter.getById(usersDef, id)).thenReturn(Optional.of(existingRecord));
            when(storageAdapter.update(eq(usersDef), eq(id), any()))
                    .thenAnswer(invocation -> Optional.of(invocation.getArgument(2)));

            assertDoesNotThrow(() -> queryEngine.update(usersDef, id, updateData));

            ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(storageAdapter).update(eq(usersDef), eq(id), dataCaptor.capture());

            Map<String, Object> persistedData = dataCaptor.getValue();
            assertFalse(persistedData.containsKey("email"),
                    "A blank submission must not clear an immutable field");
            assertFalse(persistedData.containsKey("tenantId"),
                    "A blank submission must not clear an immutable field");
            assertEquals("Jane", persistedData.get("name"));
        }

        @Test
        @DisplayName("Should reject setting an immutable field that is currently null")
        void update_rejectsImmutableField_whenStoredValueIsNull() {
            CollectionDefinition usersDef = buildCollectionWithImmutableFields();
            String id = "user-1";

            Map<String, Object> existingRecord = new HashMap<>();
            existingRecord.put("id", id);
            existingRecord.put("name", "John");

            Map<String, Object> updateData = new HashMap<>();
            updateData.put("email", "jane@example.com");

            when(storageAdapter.getById(usersDef, id)).thenReturn(Optional.of(existingRecord));

            assertThrows(ValidationException.class, () -> queryEngine.update(usersDef, id, updateData));
            verify(storageAdapter, never()).update(eq(usersDef), eq(id), any());
        }

        @Test
        @DisplayName("Should allow non-immutable fields in update data")
        void update_allowsNonImmutableFields_inUpdateData() {
            CollectionDefinition usersDef = buildCollectionWithImmutableFields();
            String id = "user-1";

            Map<String, Object> existingRecord = new HashMap<>();
            existingRecord.put("id", id);
            existingRecord.put("email", "john@example.com");
            existingRecord.put("name", "John");
            existingRecord.put("role", "user");

            // Update only mutable fields
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("name", "Jonathan");
            updateData.put("role", "admin");

            when(storageAdapter.getById(usersDef, id)).thenReturn(Optional.of(existingRecord));
            when(storageAdapter.update(eq(usersDef), eq(id), any()))
                    .thenAnswer(invocation -> Optional.of(invocation.getArgument(2)));

            Optional<Map<String, Object>> result = queryEngine.update(usersDef, id, updateData);

            assertTrue(result.isPresent());

            ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(storageAdapter).update(eq(usersDef), eq(id), dataCaptor.capture());

            Map<String, Object> persistedData = dataCaptor.getValue();
            assertTrue(persistedData.containsKey("name"),
                    "Mutable field 'name' should be present");
            assertEquals("Jonathan", persistedData.get("name"));
            assertTrue(persistedData.containsKey("role"),
                    "Mutable field 'role' should be present");
            assertEquals("admin", persistedData.get("role"));
        }

        @Test
        @DisplayName("Should be no-op when no immutable fields defined on collection")
        void update_noOp_whenNoImmutableFieldsDefined() {
            CollectionDefinition writableDef = buildWritableCollection();
            String id = "prod-1";

            Map<String, Object> existingRecord = new HashMap<>();
            existingRecord.put("id", id);
            existingRecord.put("name", "Widget");
            existingRecord.put("price", 9.99);

            Map<String, Object> updateData = new HashMap<>();
            updateData.put("name", "Super Widget");
            updateData.put("price", 19.99);

            when(storageAdapter.getById(writableDef, id)).thenReturn(Optional.of(existingRecord));
            when(storageAdapter.update(eq(writableDef), eq(id), any()))
                    .thenAnswer(invocation -> Optional.of(invocation.getArgument(2)));

            queryEngine.update(writableDef, id, updateData);

            ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(storageAdapter).update(eq(writableDef), eq(id), dataCaptor.capture());

            Map<String, Object> persistedData = dataCaptor.getValue();
            assertTrue(persistedData.containsKey("name"),
                    "All fields should pass through when no immutable fields defined");
            assertTrue(persistedData.containsKey("price"),
                    "All fields should pass through when no immutable fields defined");
        }
    }
}
