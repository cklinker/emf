package com.emf.runtime.query;

import com.emf.runtime.model.*;
import com.emf.runtime.storage.StorageAdapter;
import com.emf.runtime.validation.DefaultValidationEngine;
import com.emf.runtime.validation.ValidationEngine;
import com.emf.runtime.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DefaultQueryEngine.
 */
class DefaultQueryEngineTest {
    
    private StorageAdapter storageAdapter;
    private ValidationEngine validationEngine;
    private DefaultQueryEngine queryEngine;
    private CollectionDefinition testCollection;
    
    @BeforeEach
    void setUp() {
        storageAdapter = mock(StorageAdapter.class);
        validationEngine = mock(ValidationEngine.class);
        queryEngine = new DefaultQueryEngine(storageAdapter, validationEngine);
        
        testCollection = new CollectionDefinitionBuilder()
            .name("products")
            .displayName("Products")
            .addField(new FieldDefinitionBuilder()
                .name("name")
                .type(FieldType.STRING)
                .nullable(false)
                .build())
            .addField(new FieldDefinitionBuilder()
                .name("price")
                .type(FieldType.DOUBLE)
                .nullable(false)
                .build())
            .addField(new FieldDefinitionBuilder()
                .name("category")
                .type(FieldType.STRING)
                .nullable(true)
                .build())
            .build();
    }
    
    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {
        
        @Test
        @DisplayName("Should create engine with storage adapter only")
        void shouldCreateWithStorageAdapterOnly() {
            DefaultQueryEngine engine = new DefaultQueryEngine(storageAdapter);
            assertNotNull(engine);
        }
        
        @Test
        @DisplayName("Should throw on null storage adapter")
        void shouldThrowOnNullStorageAdapter() {
            assertThrows(NullPointerException.class, () -> 
                new DefaultQueryEngine(null));
        }
    }
    
    @Nested
    @DisplayName("executeQuery Tests")
    class ExecuteQueryTests {
        
        @Test
        @DisplayName("Should execute query with valid parameters")
        void shouldExecuteQueryWithValidParameters() {
            QueryRequest request = QueryRequest.defaults();
            QueryResult expectedResult = QueryResult.empty(request.pagination());
            
            when(storageAdapter.query(testCollection, request)).thenReturn(expectedResult);
            
            QueryResult result = queryEngine.executeQuery(testCollection, request);
            
            assertEquals(expectedResult, result);
            verify(storageAdapter).query(testCollection, request);
        }
        
        @Test
        @DisplayName("Should validate sort fields")
        void shouldValidateSortFields() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(new SortField("nonexistent", SortDirection.ASC)),
                List.of(),
                List.of()
            );
            
            InvalidQueryException ex = assertThrows(InvalidQueryException.class, () ->
                queryEngine.executeQuery(testCollection, request));
            
            assertEquals("nonexistent", ex.getFieldName());
        }
        
        @Test
        @DisplayName("Should allow sorting by system fields")
        void shouldAllowSortingBySystemFields() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(new SortField("createdAt", SortDirection.DESC)),
                List.of(),
                List.of()
            );
            QueryResult expectedResult = QueryResult.empty(request.pagination());
            
            when(storageAdapter.query(testCollection, request)).thenReturn(expectedResult);
            
            QueryResult result = queryEngine.executeQuery(testCollection, request);
            
            assertEquals(expectedResult, result);
        }

        @Test
        @DisplayName("Should validate filter fields")
        void shouldValidateFilterFields() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(new FilterCondition("nonexistent", FilterOperator.EQ, "value"))
            );
            
            InvalidQueryException ex = assertThrows(InvalidQueryException.class, () ->
                queryEngine.executeQuery(testCollection, request));
            
            assertEquals("nonexistent", ex.getFieldName());
        }
        
        @Test
        @DisplayName("Should validate requested fields")
        void shouldValidateRequestedFields() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of("nonexistent"),
                List.of()
            );
            
            InvalidQueryException ex = assertThrows(InvalidQueryException.class, () ->
                queryEngine.executeQuery(testCollection, request));
            
            assertEquals("nonexistent", ex.getFieldName());
        }
        
        @Test
        @DisplayName("Should allow requesting system fields")
        void shouldAllowRequestingSystemFields() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of("id", "createdAt", "updatedAt"),
                List.of()
            );
            QueryResult expectedResult = QueryResult.empty(request.pagination());
            
            when(storageAdapter.query(testCollection, request)).thenReturn(expectedResult);
            
            QueryResult result = queryEngine.executeQuery(testCollection, request);
            
            assertEquals(expectedResult, result);
        }
        
        @Test
        @DisplayName("Should throw on null definition")
        void shouldThrowOnNullDefinition() {
            assertThrows(NullPointerException.class, () ->
                queryEngine.executeQuery(null, QueryRequest.defaults()));
        }
        
        @Test
        @DisplayName("Should throw on null request")
        void shouldThrowOnNullRequest() {
            assertThrows(NullPointerException.class, () ->
                queryEngine.executeQuery(testCollection, null));
        }
    }
    
    @Nested
    @DisplayName("getById Tests")
    class GetByIdTests {
        
        @Test
        @DisplayName("Should return record when found")
        void shouldReturnRecordWhenFound() {
            String id = "test-id";
            Map<String, Object> record = Map.of("id", id, "name", "Test Product");
            
            when(storageAdapter.getById(testCollection, id)).thenReturn(Optional.of(record));
            
            Optional<Map<String, Object>> result = queryEngine.getById(testCollection, id);
            
            assertTrue(result.isPresent());
            assertEquals(record, result.get());
        }
        
        @Test
        @DisplayName("Should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            String id = "nonexistent";
            
            when(storageAdapter.getById(testCollection, id)).thenReturn(Optional.empty());
            
            Optional<Map<String, Object>> result = queryEngine.getById(testCollection, id);
            
            assertTrue(result.isEmpty());
        }
    }
    
    @Nested
    @DisplayName("create Tests")
    class CreateTests {
        
        @Test
        @DisplayName("Should add system fields on create")
        void shouldAddSystemFieldsOnCreate() {
            Map<String, Object> inputData = new HashMap<>();
            inputData.put("name", "Test Product");
            inputData.put("price", 99.99);
            
            when(validationEngine.validate(eq(testCollection), any(), any()))
                .thenReturn(com.emf.runtime.validation.ValidationResult.success());
            when(storageAdapter.create(eq(testCollection), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
            
            Map<String, Object> result = queryEngine.create(testCollection, inputData);
            
            assertNotNull(result.get("id"));
            assertNotNull(result.get("createdAt"));
            assertNotNull(result.get("updatedAt"));
            assertEquals("Test Product", result.get("name"));
        }
        
        @Test
        @DisplayName("Should validate data before create")
        void shouldValidateDataBeforeCreate() {
            Map<String, Object> inputData = new HashMap<>();
            inputData.put("name", "Test");
            
            com.emf.runtime.validation.ValidationResult invalidResult = 
                com.emf.runtime.validation.ValidationResult.failure(
                    com.emf.runtime.validation.FieldError.nullable("price")
                );
            
            when(validationEngine.validate(eq(testCollection), any(), any()))
                .thenReturn(invalidResult);
            
            assertThrows(ValidationException.class, () ->
                queryEngine.create(testCollection, inputData));
            
            verify(storageAdapter, never()).create(any(), any());
        }
        
        @Test
        @DisplayName("Should work without validation engine")
        void shouldWorkWithoutValidationEngine() {
            DefaultQueryEngine engineNoValidation = new DefaultQueryEngine(storageAdapter);
            
            Map<String, Object> inputData = new HashMap<>();
            inputData.put("name", "Test Product");
            
            when(storageAdapter.create(eq(testCollection), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
            
            Map<String, Object> result = engineNoValidation.create(testCollection, inputData);
            
            assertNotNull(result.get("id"));
        }
    }

    @Nested
    @DisplayName("update Tests")
    class UpdateTests {
        
        @Test
        @DisplayName("Should update existing record")
        void shouldUpdateExistingRecord() {
            String id = "test-id";
            Map<String, Object> existingRecord = new HashMap<>();
            existingRecord.put("id", id);
            existingRecord.put("name", "Old Name");
            existingRecord.put("price", 50.0);
            existingRecord.put("createdAt", Instant.now().minusSeconds(3600));
            
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("name", "New Name");
            
            when(storageAdapter.getById(testCollection, id)).thenReturn(Optional.of(existingRecord));
            when(validationEngine.validate(eq(testCollection), any(), any(), eq(id)))
                .thenReturn(com.emf.runtime.validation.ValidationResult.success());
            when(storageAdapter.update(eq(testCollection), eq(id), any()))
                .thenAnswer(invocation -> {
                    Map<String, Object> data = invocation.getArgument(2);
                    Map<String, Object> result = new HashMap<>(existingRecord);
                    result.putAll(data);
                    return Optional.of(result);
                });
            
            Optional<Map<String, Object>> result = queryEngine.update(testCollection, id, updateData);
            
            assertTrue(result.isPresent());
            assertEquals("New Name", result.get().get("name"));
            assertNotNull(result.get().get("updatedAt"));
        }
        
        @Test
        @DisplayName("Should return empty when record not found")
        void shouldReturnEmptyWhenRecordNotFound() {
            String id = "nonexistent";
            Map<String, Object> updateData = Map.of("name", "New Name");
            
            when(storageAdapter.getById(testCollection, id)).thenReturn(Optional.empty());
            
            Optional<Map<String, Object>> result = queryEngine.update(testCollection, id, updateData);
            
            assertTrue(result.isEmpty());
            verify(storageAdapter, never()).update(any(), any(), any());
        }
        
        @Test
        @DisplayName("Should not allow changing id")
        void shouldNotAllowChangingId() {
            String id = "test-id";
            Map<String, Object> existingRecord = new HashMap<>();
            existingRecord.put("id", id);
            existingRecord.put("name", "Test");
            
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("id", "new-id");
            updateData.put("name", "New Name");
            
            when(storageAdapter.getById(testCollection, id)).thenReturn(Optional.of(existingRecord));
            when(validationEngine.validate(eq(testCollection), any(), any(), eq(id)))
                .thenReturn(com.emf.runtime.validation.ValidationResult.success());
            when(storageAdapter.update(eq(testCollection), eq(id), any()))
                .thenAnswer(invocation -> {
                    Map<String, Object> data = invocation.getArgument(2);
                    assertFalse(data.containsKey("id"), "id should be removed from update data");
                    return Optional.of(new HashMap<>(existingRecord));
                });
            
            queryEngine.update(testCollection, id, updateData);
            
            verify(storageAdapter).update(eq(testCollection), eq(id), argThat(data -> 
                !data.containsKey("id")));
        }
        
        @Test
        @DisplayName("Should validate data before update")
        void shouldValidateDataBeforeUpdate() {
            String id = "test-id";
            Map<String, Object> existingRecord = Map.of("id", id, "name", "Test");
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("name", "");
            
            com.emf.runtime.validation.ValidationResult invalidResult = 
                com.emf.runtime.validation.ValidationResult.failure(List.of(
                    new com.emf.runtime.validation.FieldError("name", "Cannot be empty", "minLength")
                ));
            
            when(storageAdapter.getById(testCollection, id)).thenReturn(Optional.of(existingRecord));
            when(validationEngine.validate(eq(testCollection), any(), any(), eq(id)))
                .thenReturn(invalidResult);
            
            assertThrows(ValidationException.class, () ->
                queryEngine.update(testCollection, id, updateData));
            
            verify(storageAdapter, never()).update(any(), any(), any());
        }
    }
    
    @Nested
    @DisplayName("delete Tests")
    class DeleteTests {
        
        @Test
        @DisplayName("Should delete existing record")
        void shouldDeleteExistingRecord() {
            String id = "test-id";
            
            when(storageAdapter.delete(testCollection, id)).thenReturn(true);
            
            boolean result = queryEngine.delete(testCollection, id);
            
            assertTrue(result);
            verify(storageAdapter).delete(testCollection, id);
        }
        
        @Test
        @DisplayName("Should return false when record not found")
        void shouldReturnFalseWhenRecordNotFound() {
            String id = "nonexistent";
            
            when(storageAdapter.delete(testCollection, id)).thenReturn(false);
            
            boolean result = queryEngine.delete(testCollection, id);
            
            assertFalse(result);
        }
    }
}
