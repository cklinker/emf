package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.query.PaginationMetadata;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests that the JSON:API response projection filters out orphan columns —
 * physical PostgreSQL columns that no longer have a matching
 * {@link FieldDefinition} because their field was deleted via
 * {@code DELETE /api/fields/{id}}.
 *
 * <p>Background (TASK-2026-06-02-0005): {@code SchemaMigrationEngine} only
 * marks dropped fields as deprecated, it does not issue {@code ALTER TABLE
 * ... DROP COLUMN}. {@code PhysicalTableStorageAdapter.query} therefore still
 * returns those columns via {@code SELECT *}, and the router used to copy
 * every key into {@code data.attributes}. The fix gates the attribute copy
 * behind the live field set + a small allow-list of framework metadata keys.
 */
@DisplayName("DynamicCollectionRouter Orphan Column Filtering")
class DynamicCollectionRouterOrphanColumnTest {

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

    private CollectionDefinition providersWithLiveFields() {
        return new CollectionDefinitionBuilder()
                .name("providers")
                .displayName("Providers")
                .addField(FieldDefinition.requiredString("name"))
                .addField(FieldDefinition.string("npi"))
                .build();
    }

    @Test
    @DisplayName("GET single record drops orphan column keys from data.attributes")
    void getById_filtersOrphanColumns() throws Exception {
        CollectionDefinition def = providersWithLiveFields();
        when(registry.get("providers")).thenReturn(def);

        String id = "550e8400-e29b-41d4-a716-446655440000";
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("name", "Dr. Smith");
        record.put("npi", "1234567890");
        // Orphan columns left behind by deleted fields
        record.put("probe_temperature", null);
        record.put("legacy_specialty", "cardiology");
        when(queryEngine.getById(def, id)).thenReturn(Optional.of(record));

        MvcResult result = mockMvc.perform(get("/api/providers/" + id))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        Map<?, ?> data = (Map<?, ?>) response.get("data");
        Map<?, ?> attributes = (Map<?, ?>) data.get("attributes");

        assertEquals("Dr. Smith", attributes.get("name"));
        assertEquals("1234567890", attributes.get("npi"));
        assertFalse(attributes.containsKey("probe_temperature"),
                "Orphan column 'probe_temperature' must not appear in attributes");
        assertFalse(attributes.containsKey("legacy_specialty"),
                "Orphan column 'legacy_specialty' must not appear in attributes");
    }

    @Test
    @DisplayName("LIST drops orphan column keys from every resource in data array")
    void list_filtersOrphanColumns() throws Exception {
        CollectionDefinition def = providersWithLiveFields();
        when(registry.get("providers")).thenReturn(def);

        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("id", "11111111-1111-1111-1111-111111111111");
        r1.put("name", "Dr. A");
        r1.put("npi", "1111111111");
        r1.put("probe_temperature", null);
        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("id", "22222222-2222-2222-2222-222222222222");
        r2.put("name", "Dr. B");
        r2.put("npi", "2222222222");
        r2.put("legacy_specialty", "ortho");

        QueryResult qr = new QueryResult(
                List.of(r1, r2),
                new PaginationMetadata(2, 1, 20, 1));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(qr);

        MvcResult result = mockMvc.perform(get("/api/providers"))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        List<?> data = (List<?>) response.get("data");
        assertEquals(2, data.size());
        for (Object item : data) {
            Map<?, ?> attributes = (Map<?, ?>) ((Map<?, ?>) item).get("attributes");
            assertFalse(attributes.containsKey("probe_temperature"));
            assertFalse(attributes.containsKey("legacy_specialty"));
        }
    }

    @Test
    @DisplayName("POST response drops orphan column keys returned by the storage layer")
    void create_filtersOrphanColumnsInResponse() throws Exception {
        CollectionDefinition def = providersWithLiveFields();
        when(registry.get("providers")).thenReturn(def);

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "33333333-3333-3333-3333-333333333333");
        created.put("name", "Dr. New");
        created.put("npi", "3333333333");
        // The orphan column comes back from the post-insert SELECT
        created.put("probe_temperature", null);
        when(queryEngine.create(eq(def), any())).thenReturn(created);

        String body = "{\"data\":{\"type\":\"providers\",\"attributes\":"
                + "{\"name\":\"Dr. New\",\"npi\":\"3333333333\"}}}";

        MvcResult result = mockMvc.perform(post("/api/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        Map<?, ?> data = (Map<?, ?>) response.get("data");
        Map<?, ?> attributes = (Map<?, ?>) data.get("attributes");
        assertEquals("Dr. New", attributes.get("name"));
        assertFalse(attributes.containsKey("probe_temperature"),
                "Orphan column must not leak back through the create response");
    }

    @Test
    @DisplayName("Framework metadata keys (createdAt, tenantId, etc.) are preserved")
    void systemMetadataKeysAreRetainedAsAttributes() throws Exception {
        CollectionDefinition def = providersWithLiveFields();
        when(registry.get("providers")).thenReturn(def);

        String id = "44444444-4444-4444-4444-444444444444";
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("name", "Dr. Smith");
        record.put("createdAt", "2026-06-02T00:00:00Z");
        record.put("updatedAt", "2026-06-02T00:00:00Z");
        record.put("createdBy", "user-1");
        record.put("updatedBy", "user-1");
        record.put("tenantId", "tenant-1");
        record.put("recordTypeId", "rt-1");
        when(queryEngine.getById(def, id)).thenReturn(Optional.of(record));

        MvcResult result = mockMvc.perform(get("/api/providers/" + id))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        Map<?, ?> attributes = (Map<?, ?>) ((Map<?, ?>) response.get("data")).get("attributes");
        assertEquals("2026-06-02T00:00:00Z", attributes.get("createdAt"));
        assertEquals("2026-06-02T00:00:00Z", attributes.get("updatedAt"));
        assertEquals("user-1", attributes.get("createdBy"));
        assertEquals("user-1", attributes.get("updatedBy"));
        assertEquals("tenant-1", attributes.get("tenantId"));
        assertEquals("rt-1", attributes.get("recordTypeId"));
    }

    @Test
    @DisplayName("CURRENCY/GEOLOCATION companion column keys are preserved when the primary field still exists")
    void companionColumnsAreRetained_whenPrimaryFieldExists() throws Exception {
        FieldDefinition price = new FieldDefinition(
                "price", FieldType.CURRENCY,
                true, false, false, null, null, null, null, null);
        FieldDefinition location = new FieldDefinition(
                "location", FieldType.GEOLOCATION,
                true, false, false, null, null, null, null, null);
        CollectionDefinition def = new CollectionDefinitionBuilder()
                .name("products")
                .displayName("Products")
                .addField(FieldDefinition.requiredString("name"))
                .addField(price)
                .addField(location)
                .build();
        when(registry.get("products")).thenReturn(def);

        String id = "55555555-5555-5555-5555-555555555555";
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("name", "Widget");
        record.put("price", 9.99);
        record.put("price_currency_code", "USD");
        record.put("location", Map.of("latitude", 40.7, "longitude", -74.0));
        // Orphan column to confirm filtering still applies alongside companions
        record.put("legacy_sku", "abc-123");
        when(queryEngine.getById(def, id)).thenReturn(Optional.of(record));

        MvcResult result = mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        Map<?, ?> attributes = (Map<?, ?>) ((Map<?, ?>) response.get("data")).get("attributes");
        assertEquals("USD", attributes.get("price_currency_code"));
        assertTrue(attributes.containsKey("location"));
        assertFalse(attributes.containsKey("legacy_sku"),
                "Orphan column must not appear even with companion columns present");
    }
}
