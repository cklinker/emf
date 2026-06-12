package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.model.ReferenceConfig;
import io.kelta.runtime.query.FilterOperator;
import io.kelta.runtime.query.PaginationMetadata;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@code ?include=<field>} resolution where the include name names a
 * lookup/reference FIELD on the primary collection (rather than a collection
 * name). The field stores a raw UUID FK in {@code attributes}; with the include,
 * the response should also surface {@code relationships.<field>.data} and
 * hydrate the referenced row into {@code included[]}.
 */
@DisplayName("DynamicCollectionRouter field-name include")
class DynamicCollectionRouterFieldIncludeTest {

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

    /**
     * Titles collection (the lookup target).
     */
    private CollectionDefinition buildTitlesCollection() {
        return new CollectionDefinitionBuilder()
                .name("titles")
                .displayName("Titles")
                .addField(FieldDefinition.requiredString("name", 200))
                .build();
    }

    /**
     * Providers collection (a second lookup target).
     */
    private CollectionDefinition buildProvidersCollection() {
        return new CollectionDefinitionBuilder()
                .name("providers")
                .displayName("Providers")
                .addField(FieldDefinition.requiredString("name", 200))
                .build();
    }

    /**
     * Availabilities collection with two lookup fields stored as STRING+refConfig
     * (the legacy "raw UUID in attributes" form referenced in the task brief).
     */
    private CollectionDefinition buildAvailabilitiesCollection() {
        FieldDefinition titleField = new FieldDefinition(
                "title", FieldType.STRING, true, false, false, null, null, null,
                ReferenceConfig.toCollection("titles"), null);
        FieldDefinition providerField = new FieldDefinition(
                "provider", FieldType.STRING, true, false, false, null, null, null,
                ReferenceConfig.toCollection("providers"), null);
        return new CollectionDefinitionBuilder()
                .name("availabilities")
                .displayName("Availabilities")
                .addField(titleField)
                .addField(providerField)
                .addField(FieldDefinition.string("slot", 50))
                .build();
    }

    /**
     * Events collection where {@code venue} is a LOOKUP-typed field. Used to
     * verify the field-name include path also hydrates included rows for
     * properly typed relationship fields (where {@code relationships} is already
     * emitted by the default serializer).
     */
    private CollectionDefinition buildEventsCollection() {
        return new CollectionDefinitionBuilder()
                .name("events")
                .displayName("Events")
                .addField(FieldDefinition.requiredString("name", 100))
                .addField(new FieldDefinition("venue", FieldType.LOOKUP, true, false, false,
                        null, null, null,
                        ReferenceConfig.lookup("venues", "Venue"), null))
                .build();
    }

    private CollectionDefinition buildVenuesCollection() {
        return new CollectionDefinitionBuilder()
                .name("venues")
                .displayName("Venues")
                .addField(FieldDefinition.requiredString("name", 100))
                .build();
    }

    private Map<String, Object> availabilityRecord(String id, String slot,
                                                    String titleId, String providerId) {
        Map<String, Object> record = new HashMap<>();
        record.put("id", id);
        record.put("slot", slot);
        record.put("title", titleId);
        record.put("provider", providerId);
        return record;
    }

    private Map<String, Object> titleRecord(String id, String name) {
        Map<String, Object> record = new HashMap<>();
        record.put("id", id);
        record.put("name", name);
        return record;
    }

    private Map<String, Object> providerRecord(String id, String name) {
        Map<String, Object> record = new HashMap<>();
        record.put("id", id);
        record.put("name", name);
        return record;
    }

    // ---------- Single resource ----------

    @Nested
    @DisplayName("Get single with ?include=<field>")
    class GetSingleWithFieldInclude {

        @Test
        @DisplayName("Hydrates lookup field stored as STRING+refConfig: relationships + included")
        @SuppressWarnings("unchecked")
        void singleInclude_stringWithRefConfig() throws Exception {
            CollectionDefinition availDef = buildAvailabilitiesCollection();
            CollectionDefinition titlesDef = buildTitlesCollection();

            when(registry.get("availabilities")).thenReturn(availDef);
            when(registry.get("titles")).thenReturn(titlesDef);
            // No collection named "title" — falls through to field-include path.
            when(registry.get("title")).thenReturn(null);

            Map<String, Object> avail = availabilityRecord("avail-1", "morning",
                    "title-100", "prov-1");
            when(queryEngine.getById(availDef, "avail-1")).thenReturn(Optional.of(avail));

            Map<String, Object> title = titleRecord("title-100", "Doctor");
            QueryResult titleResult = new QueryResult(List.of(title),
                    new PaginationMetadata(1, 1, 1000, 1));
            when(queryEngine.executeQuery(eq(titlesDef), any(QueryRequest.class)))
                    .thenReturn(titleResult);

            MvcResult result = mockMvc.perform(get("/api/availabilities/avail-1")
                            .param("include", "title"))
                    .andExpect(status().isOk())
                    .andReturn();

            Map<String, Object> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), Map.class);

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            assertEquals("avail-1", data.get("id"));

            // Back-compat: raw UUID still in attributes
            Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
            assertEquals("title-100", attributes.get("title"),
                    "Lookup FK UUID must still be on attributes for back-compat");

            // New: relationships.title.data = { type, id }
            Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
            assertNotNull(relationships, "relationships block must be present after field include");
            Map<String, Object> titleRel = (Map<String, Object>) relationships.get("title");
            assertNotNull(titleRel);
            Map<String, Object> titleRelData = (Map<String, Object>) titleRel.get("data");
            assertEquals("titles", titleRelData.get("type"));
            assertEquals("title-100", titleRelData.get("id"));

            // New: included[] contains the referenced title
            List<Map<String, Object>> included =
                    (List<Map<String, Object>>) response.get("included");
            assertNotNull(included, "included[] must be present");
            assertEquals(1, included.size());
            assertEquals("titles", included.get(0).get("type"));
            assertEquals("title-100", included.get(0).get("id"));
        }

        @Test
        @DisplayName("Hydrates LOOKUP-typed field by its field name (not collection name)")
        @SuppressWarnings("unchecked")
        void singleInclude_lookupTypedField() throws Exception {
            CollectionDefinition eventsDef = buildEventsCollection();
            CollectionDefinition venuesDef = buildVenuesCollection();

            when(registry.get("events")).thenReturn(eventsDef);
            when(registry.get("venues")).thenReturn(venuesDef);
            // No collection named "venue" — must resolve via field-name path.
            when(registry.get("venue")).thenReturn(null);

            Map<String, Object> evt = new HashMap<>();
            evt.put("id", "evt-1");
            evt.put("name", "Spring Concert");
            evt.put("venue", "venue-42");
            when(queryEngine.getById(eventsDef, "evt-1")).thenReturn(Optional.of(evt));

            Map<String, Object> venue = new HashMap<>();
            venue.put("id", "venue-42");
            venue.put("name", "Main Hall");
            QueryResult venueResult = new QueryResult(List.of(venue),
                    new PaginationMetadata(1, 1, 1000, 1));
            when(queryEngine.executeQuery(eq(venuesDef), any(QueryRequest.class)))
                    .thenReturn(venueResult);

            MvcResult result = mockMvc.perform(get("/api/events/evt-1")
                            .param("include", "venue"))
                    .andExpect(status().isOk())
                    .andReturn();

            Map<String, Object> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), Map.class);

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
            assertNotNull(relationships);
            Map<String, Object> venueRel = (Map<String, Object>) relationships.get("venue");
            Map<String, Object> venueRelData = (Map<String, Object>) venueRel.get("data");
            assertEquals("venues", venueRelData.get("type"));
            assertEquals("venue-42", venueRelData.get("id"));

            List<Map<String, Object>> included =
                    (List<Map<String, Object>>) response.get("included");
            assertNotNull(included);
            assertEquals(1, included.size());
            assertEquals("venues", included.get(0).get("type"));
        }

        @Test
        @DisplayName("Multiple field includes: include=title,provider")
        @SuppressWarnings("unchecked")
        void multipleFieldIncludes() throws Exception {
            CollectionDefinition availDef = buildAvailabilitiesCollection();
            CollectionDefinition titlesDef = buildTitlesCollection();
            CollectionDefinition providersDef = buildProvidersCollection();

            when(registry.get("availabilities")).thenReturn(availDef);
            when(registry.get("titles")).thenReturn(titlesDef);
            when(registry.get("providers")).thenReturn(providersDef);
            when(registry.get("title")).thenReturn(null);
            when(registry.get("provider")).thenReturn(null);

            Map<String, Object> avail = availabilityRecord("avail-1", "morning",
                    "title-100", "prov-1");
            when(queryEngine.getById(availDef, "avail-1")).thenReturn(Optional.of(avail));

            Map<String, Object> title = titleRecord("title-100", "Doctor");
            QueryResult titleResult = new QueryResult(List.of(title),
                    new PaginationMetadata(1, 1, 1000, 1));
            when(queryEngine.executeQuery(eq(titlesDef), any(QueryRequest.class)))
                    .thenReturn(titleResult);

            Map<String, Object> provider = providerRecord("prov-1", "Acme");
            QueryResult providerResult = new QueryResult(List.of(provider),
                    new PaginationMetadata(1, 1, 1000, 1));
            when(queryEngine.executeQuery(eq(providersDef), any(QueryRequest.class)))
                    .thenReturn(providerResult);

            MvcResult result = mockMvc.perform(get("/api/availabilities/avail-1")
                            .param("include", "title,provider"))
                    .andExpect(status().isOk())
                    .andReturn();

            Map<String, Object> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), Map.class);

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
            assertNotNull(relationships);
            assertTrue(relationships.containsKey("title"));
            assertTrue(relationships.containsKey("provider"));

            Map<String, Object> titleRelData =
                    (Map<String, Object>) ((Map<String, Object>) relationships.get("title")).get("data");
            assertEquals("titles", titleRelData.get("type"));
            assertEquals("title-100", titleRelData.get("id"));

            Map<String, Object> providerRelData =
                    (Map<String, Object>) ((Map<String, Object>) relationships.get("provider")).get("data");
            assertEquals("providers", providerRelData.get("type"));
            assertEquals("prov-1", providerRelData.get("id"));

            List<Map<String, Object>> included =
                    (List<Map<String, Object>>) response.get("included");
            assertNotNull(included);
            assertEquals(2, included.size());
            long titleCount = included.stream()
                    .filter(r -> "titles".equals(r.get("type"))).count();
            long providerCount = included.stream()
                    .filter(r -> "providers".equals(r.get("type"))).count();
            assertEquals(1, titleCount);
            assertEquals(1, providerCount);
        }

        @Test
        @DisplayName("Null FK on lookup field yields data:null and no included row")
        @SuppressWarnings("unchecked")
        void singleInclude_nullFk() throws Exception {
            CollectionDefinition availDef = buildAvailabilitiesCollection();
            CollectionDefinition titlesDef = buildTitlesCollection();

            when(registry.get("availabilities")).thenReturn(availDef);
            when(registry.get("titles")).thenReturn(titlesDef);
            when(registry.get("title")).thenReturn(null);

            Map<String, Object> avail = availabilityRecord("avail-1", "morning",
                    null, "prov-1");
            when(queryEngine.getById(availDef, "avail-1")).thenReturn(Optional.of(avail));

            MvcResult result = mockMvc.perform(get("/api/availabilities/avail-1")
                            .param("include", "title"))
                    .andExpect(status().isOk())
                    .andReturn();

            Map<String, Object> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), Map.class);

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
            assertNotNull(relationships);
            assertTrue(relationships.containsKey("title"));
            Map<String, Object> titleRel = (Map<String, Object>) relationships.get("title");
            assertNull(titleRel.get("data"), "Null FK must yield data:null in relationships");

            // No target query should have run for a null FK
            verify(queryEngine, never()).executeQuery(eq(titlesDef), any(QueryRequest.class));

            assertNull(response.get("included"),
                    "included[] should be absent when there are no resolvable targets");
        }

        @Test
        @DisplayName("Missing target row: relationships present, included empty")
        @SuppressWarnings("unchecked")
        void singleInclude_missingTargetRow() throws Exception {
            CollectionDefinition availDef = buildAvailabilitiesCollection();
            CollectionDefinition titlesDef = buildTitlesCollection();

            when(registry.get("availabilities")).thenReturn(availDef);
            when(registry.get("titles")).thenReturn(titlesDef);
            when(registry.get("title")).thenReturn(null);

            Map<String, Object> avail = availabilityRecord("avail-1", "morning",
                    "title-orphan", "prov-1");
            when(queryEngine.getById(availDef, "avail-1")).thenReturn(Optional.of(avail));

            // Target collection query returns no rows (dangling FK)
            QueryResult emptyResult = new QueryResult(List.of(),
                    new PaginationMetadata(0, 1, 1000, 0));
            when(queryEngine.executeQuery(eq(titlesDef), any(QueryRequest.class)))
                    .thenReturn(emptyResult);

            MvcResult result = mockMvc.perform(get("/api/availabilities/avail-1")
                            .param("include", "title"))
                    .andExpect(status().isOk())
                    .andReturn();

            Map<String, Object> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), Map.class);

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
            assertNotNull(relationships, "relationships block must still be emitted with dangling FK");
            Map<String, Object> titleRelData =
                    (Map<String, Object>) ((Map<String, Object>) relationships.get("title")).get("data");
            assertEquals("titles", titleRelData.get("type"));
            assertEquals("title-orphan", titleRelData.get("id"));

            // included[] omitted entirely when no targets resolved
            assertNull(response.get("included"));
        }

        @Test
        @DisplayName("Unknown target collection: no included rows, no crash")
        @SuppressWarnings("unchecked")
        void singleInclude_unknownTargetCollection() throws Exception {
            CollectionDefinition availDef = buildAvailabilitiesCollection();
            when(registry.get("availabilities")).thenReturn(availDef);
            // titles collection is NOT registered — target lookup fails gracefully.
            when(registry.get("titles")).thenReturn(null);
            when(registry.get("title")).thenReturn(null);

            Map<String, Object> avail = availabilityRecord("avail-1", "morning",
                    "title-100", "prov-1");
            when(queryEngine.getById(availDef, "avail-1")).thenReturn(Optional.of(avail));

            MvcResult result = mockMvc.perform(get("/api/availabilities/avail-1")
                            .param("include", "title"))
                    .andExpect(status().isOk())
                    .andReturn();

            Map<String, Object> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), Map.class);

            // Raw UUID still on attributes
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
            assertEquals("title-100", attributes.get("title"));

            // No included rows — no target collection available
            assertNull(response.get("included"));
        }

        @Test
        @DisplayName("Include name that is neither a collection nor a field is skipped")
        @SuppressWarnings("unchecked")
        void singleInclude_unknownIncludeName() throws Exception {
            CollectionDefinition availDef = buildAvailabilitiesCollection();
            when(registry.get("availabilities")).thenReturn(availDef);
            when(registry.get("not-a-thing")).thenReturn(null);

            Map<String, Object> avail = availabilityRecord("avail-1", "morning",
                    "title-100", "prov-1");
            when(queryEngine.getById(availDef, "avail-1")).thenReturn(Optional.of(avail));

            MvcResult result = mockMvc.perform(get("/api/availabilities/avail-1")
                            .param("include", "not-a-thing"))
                    .andExpect(status().isOk())
                    .andReturn();

            Map<String, Object> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), Map.class);

            assertNull(response.get("included"));
            // No relationships block injected for a non-existent field name
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            assertNull(data.get("relationships"));
        }
    }

    // ---------- List endpoint ----------

    @Nested
    @DisplayName("List with ?include=<field>")
    class ListWithFieldInclude {

        @Test
        @DisplayName("List hydrates the same FK across rows, deduplicates target query")
        @SuppressWarnings("unchecked")
        void listInclude_dedupesFkValuesForTargetQuery() throws Exception {
            CollectionDefinition availDef = buildAvailabilitiesCollection();
            CollectionDefinition titlesDef = buildTitlesCollection();

            when(registry.get("availabilities")).thenReturn(availDef);
            when(registry.get("titles")).thenReturn(titlesDef);
            when(registry.get("title")).thenReturn(null);

            // Two availabilities point at the same title, one points at another.
            Map<String, Object> a1 = availabilityRecord("avail-1", "morning",
                    "title-100", "prov-1");
            Map<String, Object> a2 = availabilityRecord("avail-2", "afternoon",
                    "title-100", "prov-2");
            Map<String, Object> a3 = availabilityRecord("avail-3", "evening",
                    "title-200", "prov-1");
            QueryResult primaryResult = new QueryResult(List.of(a1, a2, a3),
                    new PaginationMetadata(3, 1, 20, 1));
            when(queryEngine.executeQuery(eq(availDef), any(QueryRequest.class)))
                    .thenReturn(primaryResult);

            Map<String, Object> t100 = titleRecord("title-100", "Doctor");
            Map<String, Object> t200 = titleRecord("title-200", "Nurse");
            QueryResult titleResult = new QueryResult(List.of(t100, t200),
                    new PaginationMetadata(2, 1, 1000, 1));
            when(queryEngine.executeQuery(eq(titlesDef), any(QueryRequest.class)))
                    .thenReturn(titleResult);

            MvcResult result = mockMvc.perform(get("/api/availabilities")
                            .param("include", "title"))
                    .andExpect(status().isOk())
                    .andReturn();

            // Capture the query against titles and verify it used the deduped FK set
            ArgumentCaptor<QueryRequest> q = ArgumentCaptor.forClass(QueryRequest.class);
            verify(queryEngine).executeQuery(eq(titlesDef), q.capture());
            QueryRequest titleQuery = q.getValue();
            assertFalse(titleQuery.filters().isEmpty());
            assertEquals("id", titleQuery.filters().get(0).fieldName());
            assertEquals(FilterOperator.IN, titleQuery.filters().get(0).operator());
            List<Object> ids = (List<Object>) titleQuery.filters().get(0).value();
            assertEquals(2, ids.size(), "Duplicate FK values must be deduped before querying targets");
            assertTrue(ids.contains("title-100"));
            assertTrue(ids.contains("title-200"));

            Map<String, Object> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), Map.class);

            // Every primary row has its relationships.title set
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            assertEquals(3, data.size());
            for (Map<String, Object> row : data) {
                Map<String, Object> rels = (Map<String, Object>) row.get("relationships");
                assertNotNull(rels);
                assertTrue(rels.containsKey("title"));
            }

            // 2 unique titles in included[]
            List<Map<String, Object>> included =
                    (List<Map<String, Object>>) response.get("included");
            assertNotNull(included);
            assertEquals(2, included.size());
        }
    }
}
