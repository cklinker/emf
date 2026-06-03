package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.query.PaginationMetadata;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for orphan column handling on record reads/writes.
 *
 * <p>{@code DELETE /api/fields/{id}} only deprecates the underlying PostgreSQL
 * column ({@link io.kelta.runtime.storage.SchemaMigrationEngine} comments it
 * out instead of {@code ALTER TABLE ... DROP COLUMN}), so {@code SELECT *} in
 * the storage adapter keeps returning the stale column. Before the fix,
 * {@link DynamicCollectionRouter} echoed every key from the row map into
 * {@code data.attributes}, exposing deleted field names as {@code null} on
 * every record read and on the create response. The router now drops keys
 * that have neither a live {@link FieldDefinition}, a framework-metadata
 * role (createdAt/updatedAt/createdBy/updatedBy/tenantId/recordTypeId), nor
 * a companion-column relationship to a still-live CURRENCY/GEOLOCATION field.
 */
class DynamicCollectionRouterOrphanColumnTest {

    private CollectionRegistry registry;
    private QueryEngine queryEngine;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        registry = mock(CollectionRegistry.class);
        queryEngine = mock(QueryEngine.class);
        DynamicCollectionRouter router = new DynamicCollectionRouter(registry, queryEngine);
        mockMvc = MockMvcBuilders.standaloneSetup(router).build();
        objectMapper = new ObjectMapper();
    }

    private CollectionDefinition buildProvidersWithOnlyName() {
        return new CollectionDefinitionBuilder()
                .name("providers")
                .displayName("Providers")
                .addField(FieldDefinition.requiredString("name"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getAttributes(MvcResult result) throws Exception {
        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        return (Map<String, Object>) data.get("attributes");
    }

    @Test
    @DisplayName("getById drops orphan columns left behind by deleted fields")
    void getById_dropsOrphanColumns() throws Exception {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        CollectionDefinition def = buildProvidersWithOnlyName();
        when(registry.get("providers")).thenReturn(def);

        Map<String, Object> row = new HashMap<>();
        row.put("id", uuid);
        row.put("name", "Acme");
        // Seven probe fields were deleted; SELECT * still returns the columns
        // with null values until someone runs ALTER TABLE DROP COLUMN.
        row.put("probe_field_1", null);
        row.put("probe_field_2", null);
        row.put("probe_field_3", "lingering");
        when(queryEngine.getById(eq(def), eq(uuid))).thenReturn(Optional.of(row));

        MvcResult result = mockMvc.perform(get("/api/providers/" + uuid))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> attributes = getAttributes(result);
        assertEquals("Acme", attributes.get("name"));
        assertFalse(attributes.containsKey("probe_field_1"),
                "orphan column probe_field_1 should be dropped");
        assertFalse(attributes.containsKey("probe_field_2"),
                "orphan column probe_field_2 should be dropped");
        assertFalse(attributes.containsKey("probe_field_3"),
                "orphan column probe_field_3 should be dropped");
    }

    @Test
    @DisplayName("list drops orphan columns from every record in data[]")
    @SuppressWarnings("unchecked")
    void list_dropsOrphanColumns() throws Exception {
        CollectionDefinition def = buildProvidersWithOnlyName();
        when(registry.get("providers")).thenReturn(def);

        Map<String, Object> row = new HashMap<>();
        row.put("id", "rec-1");
        row.put("name", "Acme");
        row.put("probe_field_1", null);

        QueryResult result = new QueryResult(List.of(row),
                new PaginationMetadata(1, 1, 20, 1));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(result);

        MvcResult mvc = mockMvc.perform(get("/api/providers"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                mvc.getResponse().getContentAsString(), Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        assertEquals(1, data.size());
        Map<String, Object> attributes = (Map<String, Object>) data.get(0).get("attributes");
        assertEquals("Acme", attributes.get("name"));
        assertFalse(attributes.containsKey("probe_field_1"),
                "orphan column probe_field_1 should be dropped");
    }

    @Test
    @DisplayName("create response does not echo orphan columns the read-back picked up")
    void create_dropsOrphanColumnsInResponse() throws Exception {
        CollectionDefinition def = buildProvidersWithOnlyName();
        when(registry.get("providers")).thenReturn(def);

        // PhysicalTableStorageAdapter#create merges getById back into the
        // returned map, so any orphan columns reappear here just like in
        // production — the response must still filter them.
        Map<String, Object> created = new HashMap<>();
        created.put("id", "rec-1");
        created.put("name", "Acme");
        created.put("probe_field_1", null);
        when(queryEngine.create(eq(def), any())).thenReturn(created);

        String body = "{\"data\":{\"type\":\"providers\",\"attributes\":{\"name\":\"Acme\"}}}";

        MvcResult result = mockMvc.perform(post("/api/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> attributes = getAttributes(result);
        assertEquals("Acme", attributes.get("name"));
        assertFalse(attributes.containsKey("probe_field_1"),
                "orphan column probe_field_1 should not appear in create response");
    }

    @Test
    @DisplayName("framework metadata keys are preserved even though they have no FieldDefinition")
    void getById_keepsFrameworkMetadata() throws Exception {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        CollectionDefinition def = buildProvidersWithOnlyName();
        when(registry.get("providers")).thenReturn(def);

        Map<String, Object> row = new HashMap<>();
        row.put("id", uuid);
        row.put("name", "Acme");
        row.put("createdAt", "2026-06-02T00:00:00Z");
        row.put("updatedAt", "2026-06-02T00:00:00Z");
        row.put("createdBy", "user-1");
        row.put("updatedBy", "user-1");
        row.put("recordTypeId", "rt-1");
        when(queryEngine.getById(eq(def), eq(uuid))).thenReturn(Optional.of(row));

        MvcResult result = mockMvc.perform(get("/api/providers/" + uuid))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> attributes = getAttributes(result);
        assertTrue(attributes.containsKey("createdAt"));
        assertTrue(attributes.containsKey("updatedAt"));
        assertEquals("user-1", attributes.get("createdBy"));
        assertEquals("user-1", attributes.get("updatedBy"));
        assertEquals("rt-1", attributes.get("recordTypeId"));
    }

    @Test
    @DisplayName("companions of a live CURRENCY field are kept; companions of a deleted field are dropped")
    void getById_keepsLiveCompanionsAndDropsOrphanedOnes() throws Exception {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        FieldDefinition price = new FieldDefinition(
                "price", FieldType.CURRENCY, true, false, false,
                null, null, null, null, null);

        CollectionDefinition def = new CollectionDefinitionBuilder()
                .name("products")
                .displayName("Products")
                .addField(FieldDefinition.requiredString("name"))
                .addField(price)
                .build();
        when(registry.get("products")).thenReturn(def);

        Map<String, Object> row = new HashMap<>();
        row.put("id", uuid);
        row.put("name", "Widget");
        row.put("price", 9.99);
        row.put("price_currency_code", "USD"); // companion of live field — keep
        // Companions of a deleted GEOLOCATION field — drop. There's no
        // "location" FieldDefinition in this collection.
        row.put("location_longitude", -122.0);
        row.put("location_latitude", 37.0);
        when(queryEngine.getById(eq(def), eq(uuid))).thenReturn(Optional.of(row));

        MvcResult result = mockMvc.perform(get("/api/products/" + uuid))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> attributes = getAttributes(result);
        assertEquals(9.99, ((Number) attributes.get("price")).doubleValue(), 0.0001);
        assertEquals("USD", attributes.get("price_currency_code"));
        assertFalse(attributes.containsKey("location_longitude"),
                "companion of deleted field should be dropped");
        assertFalse(attributes.containsKey("location_latitude"),
                "companion of deleted field should be dropped");
    }
}
