package com.emf.runtime.router;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.CollectionDefinitionBuilder;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.query.FilterCondition;
import com.emf.runtime.query.FilterOperator;
import com.emf.runtime.query.Pagination;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.query.QueryRequest;
import com.emf.runtime.query.QueryResult;
import com.emf.runtime.query.PaginationMetadata;
import com.emf.runtime.registry.CollectionRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for display field lookup in DynamicCollectionRouter.
 *
 * <p>Verifies that GET /{collectionName}/{id} can resolve records by:
 * <ul>
 *   <li>Standard UUID (existing behavior)</li>
 *   <li>Display field value when the field is unique and required</li>
 * </ul>
 */
@DisplayName("DynamicCollectionRouter Display Field Lookup Tests")
class DynamicCollectionRouterDisplayFieldTest {

    private CollectionRegistry registry;
    private QueryEngine queryEngine;
    private DynamicCollectionRouter router;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        registry = mock(CollectionRegistry.class);
        queryEngine = mock(QueryEngine.class);
        router = new DynamicCollectionRouter(registry, queryEngine);
        mockMvc = MockMvcBuilders.standaloneSetup(router).build();
        objectMapper = new ObjectMapper();
    }

    private CollectionDefinition buildCollectionWithDisplayField(
            String name, String displayFieldName, boolean unique, boolean required) {
        FieldDefinition displayField = new FieldDefinition(
                displayFieldName,
                com.emf.runtime.model.FieldType.STRING,
                !required,    // nullable = !required
                false,        // immutable
                unique,       // unique
                null, null, null, null, null);

        return new CollectionDefinitionBuilder()
                .name(name)
                .displayName(name)
                .addField(FieldDefinition.requiredString("name"))
                .addField(displayField)
                .displayFieldName(displayFieldName)
                .build();
    }

    @Nested
    @DisplayName("UUID Lookup")
    class UuidLookup {

        @Test
        @DisplayName("Should resolve record by UUID even when display field is configured")
        void shouldResolveByUuid() throws Exception {
            String uuid = "550e8400-e29b-41d4-a716-446655440000";
            CollectionDefinition def = buildCollectionWithDisplayField("tenants", "slug", true, true);
            when(registry.get("tenants")).thenReturn(def);

            Map<String, Object> record = Map.of("id", uuid, "name", "Default Tenant", "slug", "default");
            when(queryEngine.getById(def, uuid)).thenReturn(Optional.of(record));

            MvcResult result = mockMvc.perform(get("/api/collections/tenants/" + uuid))
                    .andExpect(status().isOk())
                    .andReturn();

            // Should call getById, NOT executeQuery
            verify(queryEngine).getById(def, uuid);
            verify(queryEngine, never()).executeQuery(any(), any());

            String body = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(body, Map.class);
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            assertEquals(uuid, data.get("id"));
            assertEquals("tenants", data.get("type"));
        }
    }

    @Nested
    @DisplayName("Display Field Lookup")
    class DisplayFieldLookup {

        @Test
        @DisplayName("Should resolve record by display field value when field is unique and required")
        void shouldResolveByDisplayFieldValue() throws Exception {
            CollectionDefinition def = buildCollectionWithDisplayField("tenants", "slug", true, true);
            when(registry.get("tenants")).thenReturn(def);

            // getById returns empty for non-UUID "default"
            when(queryEngine.getById(def, "default")).thenReturn(Optional.empty());

            String recordId = "550e8400-e29b-41d4-a716-446655440000";
            Map<String, Object> record = Map.of("id", recordId, "name", "Default Tenant", "slug", "default");

            QueryResult queryResult = new QueryResult(
                    List.of(record),
                    new PaginationMetadata(1, 1, 1, 1));
            when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(queryResult);

            MvcResult result = mockMvc.perform(get("/api/collections/tenants/default"))
                    .andExpect(status().isOk())
                    .andReturn();

            // getById is called first, then falls back to display field query
            verify(queryEngine).getById(def, "default");

            // Verify the display field query filter
            ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(queryEngine).executeQuery(eq(def), captor.capture());

            QueryRequest captured = captor.getValue();
            assertEquals(1, captured.filters().size());
            FilterCondition filter = captured.filters().get(0);
            assertEquals("slug", filter.fieldName());
            assertEquals(FilterOperator.EQ, filter.operator());
            assertEquals("default", filter.value());

            // Verify response
            String body = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(body, Map.class);
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            assertEquals(recordId, data.get("id"));
        }

        @Test
        @DisplayName("Should return 404 when display field value does not match any record")
        void shouldReturn404WhenDisplayFieldValueNotFound() throws Exception {
            CollectionDefinition def = buildCollectionWithDisplayField("tenants", "slug", true, true);
            when(registry.get("tenants")).thenReturn(def);

            when(queryEngine.getById(def, "nonexistent")).thenReturn(Optional.empty());

            QueryResult emptyResult = new QueryResult(
                    List.of(),
                    new PaginationMetadata(0, 1, 1, 0));
            when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(emptyResult);

            mockMvc.perform(get("/api/collections/tenants/nonexistent"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 when display field is not unique")
        void shouldReturn404WhenDisplayFieldNotUnique() throws Exception {
            // Display field is required but NOT unique
            CollectionDefinition def = buildCollectionWithDisplayField("tenants", "slug", false, true);
            when(registry.get("tenants")).thenReturn(def);
            when(queryEngine.getById(def, "default")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/collections/tenants/default"))
                    .andExpect(status().isNotFound());

            // getById is always called first; display field query should NOT happen
            verify(queryEngine).getById(def, "default");
            verify(queryEngine, never()).executeQuery(any(), any());
        }

        @Test
        @DisplayName("Should return 404 when display field is not required")
        void shouldReturn404WhenDisplayFieldNotRequired() throws Exception {
            // Display field is unique but NOT required (nullable)
            CollectionDefinition def = buildCollectionWithDisplayField("tenants", "slug", true, false);
            when(registry.get("tenants")).thenReturn(def);
            when(queryEngine.getById(def, "default")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/collections/tenants/default"))
                    .andExpect(status().isNotFound());

            verify(queryEngine).getById(def, "default");
            verify(queryEngine, never()).executeQuery(any(), any());
        }

        @Test
        @DisplayName("Should return 404 when no display field is configured")
        void shouldReturn404WhenNoDisplayField() throws Exception {
            CollectionDefinition def = new CollectionDefinitionBuilder()
                    .name("tenants")
                    .displayName("Tenants")
                    .addField(FieldDefinition.requiredString("name"))
                    .addField(FieldDefinition.requiredString("slug").withUnique(true))
                    // No displayFieldName set
                    .build();
            when(registry.get("tenants")).thenReturn(def);
            when(queryEngine.getById(def, "default")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/collections/tenants/default"))
                    .andExpect(status().isNotFound());

            verify(queryEngine).getById(def, "default");
            verify(queryEngine, never()).executeQuery(any(), any());
        }
    }

    @Nested
    @DisplayName("Include with Display Field Lookup")
    class IncludeWithDisplayField {

        @Test
        @DisplayName("Should support ?include= parameter with display field lookup")
        void shouldSupportIncludeWithDisplayFieldLookup() throws Exception {
            // Parent collection with display field
            FieldDefinition slugField = new FieldDefinition(
                    "slug",
                    com.emf.runtime.model.FieldType.STRING,
                    false, false, true,
                    null, null, null, null, null);

            CollectionDefinition tenantDef = new CollectionDefinitionBuilder()
                    .name("tenants")
                    .displayName("Tenants")
                    .addField(FieldDefinition.requiredString("name"))
                    .addField(slugField)
                    .displayFieldName("slug")
                    .build();

            when(registry.get("tenants")).thenReturn(tenantDef);

            // getById returns empty for non-UUID "default"
            when(queryEngine.getById(tenantDef, "default")).thenReturn(Optional.empty());

            String recordId = "550e8400-e29b-41d4-a716-446655440000";
            Map<String, Object> record = Map.of("id", recordId, "name", "Default", "slug", "default");

            QueryResult queryResult = new QueryResult(
                    List.of(record),
                    new PaginationMetadata(1, 1, 1, 1));
            when(queryEngine.executeQuery(eq(tenantDef), any(QueryRequest.class))).thenReturn(queryResult);

            MvcResult result = mockMvc.perform(get("/api/collections/tenants/default?include=users"))
                    .andExpect(status().isOk())
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(body, Map.class);
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            assertEquals(recordId, data.get("id"));
        }
    }
}
