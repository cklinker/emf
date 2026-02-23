package com.emf.runtime.query;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.CollectionDefinitionBuilder;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import com.emf.runtime.storage.StorageAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for read-only collection enforcement and immutable field stripping
 * in DefaultQueryEngine.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Read-only collections reject create, update, and delete operations</li>
 *   <li>Read-only collections still allow query (read) operations</li>
 *   <li>Immutable fields are stripped from update data before persisting</li>
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

    // ==================== Immutable Field Stripping Tests ====================

    @Nested
    @DisplayName("Immutable Field Stripping")
    class ImmutableFieldTests {

        @Test
        @DisplayName("Should strip immutable fields from update data")
        void update_stripsImmutableFields_fromUpdateData() {
            CollectionDefinition usersDef = buildCollectionWithImmutableFields();
            String id = "user-1";

            Map<String, Object> existingRecord = new HashMap<>();
            existingRecord.put("id", id);
            existingRecord.put("email", "john@example.com");
            existingRecord.put("name", "John");
            existingRecord.put("tenantId", "tenant-1");

            // Attempt to update email (immutable), tenantId (immutable), and name (mutable)
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("email", "jane@example.com");      // immutable - should be stripped
            updateData.put("tenantId", "tenant-2");            // immutable - should be stripped
            updateData.put("name", "Jane");                     // mutable - should be kept

            when(storageAdapter.getById(usersDef, id)).thenReturn(Optional.of(existingRecord));
            when(storageAdapter.update(eq(usersDef), eq(id), any()))
                    .thenAnswer(invocation -> Optional.of(invocation.getArgument(2)));

            queryEngine.update(usersDef, id, updateData);

            // Capture the data passed to storageAdapter.update()
            ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(storageAdapter).update(eq(usersDef), eq(id), dataCaptor.capture());

            Map<String, Object> persistedData = dataCaptor.getValue();

            assertFalse(persistedData.containsKey("email"),
                    "Immutable field 'email' should be stripped from update data");
            assertFalse(persistedData.containsKey("tenantId"),
                    "Immutable field 'tenantId' should be stripped from update data");
            assertTrue(persistedData.containsKey("name"),
                    "Mutable field 'name' should be present in update data");
            assertEquals("Jane", persistedData.get("name"));
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
