package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for TASK-2026-06-02-0005.
 *
 * <p>{@code DELETE /api/fields/{id}} only deprecates the underlying PostgreSQL
 * column ({@link io.kelta.runtime.storage.SchemaMigrationEngine} comments it
 * out instead of {@code ALTER TABLE ... DROP COLUMN}), so {@code SELECT *} in
 * the storage adapter keeps returning the stale column. Before this fix,
 * {@link DynamicCollectionRouter#toJsonApiResourceObject} echoed every key
 * from the row map into {@code data.attributes}, exposing deleted field names
 * as {@code null} on every record read/write. The router now drops keys that
 * have neither a live {@link FieldDefinition}, a framework-metadata role
 * (createdAt/updatedAt/createdBy/updatedBy/tenantId/recordTypeId), nor a
 * companion-column relationship to a still-live CURRENCY/GEOLOCATION field.
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

    @Test
    @DisplayName("GET by id: orphan columns left by a deleted field are filtered out of attributes")
    void getById_dropsOrphanColumns() throws Exception {
        CollectionDefinition def = buildProvidersWithOnlyName();
        when(registry.get("providers")).thenReturn(def);

        Map<String, Object> row = new HashMap<>();
        row.put("id", "rec-1");
        row.put("name", "Acme");
        // Simulates the seven probe fields that were deleted but whose physical
        // columns SELECT * still returns with null values.
        row.put("probe_field_1", null);
        row.put("probe_field_2", null);
        row.put("probe_field_3", "lingering");

        when(queryEngine.getById(eq(def), eq("rec-1"))).thenReturn(Optional.of(row));

        mockMvc.perform(get("/api/providers/rec-1").header("X-Tenant-ID", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("rec-1"))
                .andExpect(jsonPath("$.data.attributes.name").value("Acme"))
                .andExpect(jsonPath("$.data.attributes.probe_field_1").doesNotExist())
                .andExpect(jsonPath("$.data.attributes.probe_field_2").doesNotExist())
                .andExpect(jsonPath("$.data.attributes.probe_field_3").doesNotExist());
    }

    @Test
    @DisplayName("LIST: orphan columns are filtered from every record in data[]")
    void list_dropsOrphanColumns() throws Exception {
        CollectionDefinition def = buildProvidersWithOnlyName();
        when(registry.get("providers")).thenReturn(def);

        Map<String, Object> row = new HashMap<>();
        row.put("id", "rec-1");
        row.put("name", "Acme");
        row.put("probe_field_1", null);

        QueryResult result = QueryResult.of(List.of(row), 1, Pagination.defaults());
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(result);

        mockMvc.perform(get("/api/providers").header("X-Tenant-ID", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].attributes.name").value("Acme"))
                .andExpect(jsonPath("$.data[0].attributes.probe_field_1").doesNotExist());
    }

    @Test
    @DisplayName("POST: the create response does not echo orphan columns the read-back picked up")
    void create_dropsOrphanColumnsInResponse() throws Exception {
        CollectionDefinition def = buildProvidersWithOnlyName();
        when(registry.get("providers")).thenReturn(def);

        Map<String, Object> created = new HashMap<>();
        created.put("id", "rec-1");
        created.put("name", "Acme");
        // PhysicalTableStorageAdapter#create merges getById back into the
        // returned map, so an orphan column reappears here just like in
        // production — the response must still drop it.
        created.put("probe_field_1", null);
        when(queryEngine.create(eq(def), any())).thenReturn(created);

        String body = "{\"data\":{\"type\":\"providers\",\"attributes\":{\"name\":\"Acme\"}}}";

        mockMvc.perform(post("/api/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Tenant-ID", "tenant-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attributes.name").value("Acme"))
                .andExpect(jsonPath("$.data.attributes.probe_field_1").doesNotExist());
    }

    @Test
    @DisplayName("Framework metadata keys (createdAt/updatedAt/...) are preserved even though they have no FieldDefinition")
    void getById_keepsFrameworkMetadata() throws Exception {
        CollectionDefinition def = buildProvidersWithOnlyName();
        when(registry.get("providers")).thenReturn(def);

        Map<String, Object> row = new HashMap<>();
        row.put("id", "rec-1");
        row.put("name", "Acme");
        row.put("createdAt", Instant.parse("2026-06-02T00:00:00Z").toString());
        row.put("updatedAt", Instant.parse("2026-06-02T00:00:00Z").toString());
        row.put("createdBy", "user-1");
        row.put("updatedBy", "user-1");
        row.put("recordTypeId", "rt-1");

        when(queryEngine.getById(eq(def), eq("rec-1"))).thenReturn(Optional.of(row));

        mockMvc.perform(get("/api/providers/rec-1").header("X-Tenant-ID", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.createdAt").exists())
                .andExpect(jsonPath("$.data.attributes.updatedAt").exists())
                .andExpect(jsonPath("$.data.attributes.createdBy").value("user-1"))
                .andExpect(jsonPath("$.data.attributes.updatedBy").value("user-1"))
                .andExpect(jsonPath("$.data.attributes.recordTypeId").value("rt-1"));
    }

    @Test
    @DisplayName("Companion columns of a live CURRENCY/GEOLOCATION field are kept; companions of a deleted field are dropped")
    void getById_keepsLiveCompanionsAndDropsOrphanedOnes() throws Exception {
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
        row.put("id", "rec-1");
        row.put("name", "Widget");
        row.put("price", 9.99);
        row.put("price_currency_code", "USD"); // companion of live field — keep
        // Companions of a deleted GEOLOCATION field — drop. There's no
        // "location" FieldDefinition in this collection.
        row.put("location_longitude", -122.0);
        row.put("location_latitude", 37.0);

        when(queryEngine.getById(eq(def), eq("rec-1"))).thenReturn(Optional.of(row));

        mockMvc.perform(get("/api/products/rec-1").header("X-Tenant-ID", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.name").value("Widget"))
                .andExpect(jsonPath("$.data.attributes.price").value(9.99))
                .andExpect(jsonPath("$.data.attributes.price_currency_code").value("USD"))
                .andExpect(jsonPath("$.data.attributes.location_longitude").doesNotExist())
                .andExpect(jsonPath("$.data.attributes.location_latitude").doesNotExist());
    }
}
