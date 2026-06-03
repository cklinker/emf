package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.model.ReferenceConfig;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests that POST/PATCH responses include a {@code relationships} block that
 * echoes the caller's relationships, regardless of whether the underlying
 * field is REFERENCE-typed. This makes it straightforward to follow up with
 * a related-record fetch after a create/update.
 */
@DisplayName("DynamicCollectionRouter relationship echo")
class DynamicCollectionRouterRelationshipEchoTest {

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

    private CollectionDefinition eventsCollection() {
        return new CollectionDefinitionBuilder()
                .name("events")
                .displayName("Events")
                .addField(FieldDefinition.requiredString("name"))
                .addField(new FieldDefinition("venue", FieldType.LOOKUP, true, false, false,
                        null, null, null,
                        ReferenceConfig.lookup("venues", "Venue"), null))
                .build();
    }

    @Test
    @DisplayName("create response includes relationships block for REFERENCE-typed field")
    void createEchoesRelationshipsForReferenceField() throws Exception {
        CollectionDefinition def = eventsCollection();
        when(registry.get("events")).thenReturn(def);

        Map<String, Object> stored = new HashMap<>();
        stored.put("id", "evt-1");
        stored.put("name", "Spring Concert");
        stored.put("venue", "venue-42");
        when(queryEngine.create(eq(def), any())).thenReturn(stored);

        String body = "{\"data\":{\"type\":\"events\","
                + "\"attributes\":{\"name\":\"Spring Concert\"},"
                + "\"relationships\":{\"venue\":{\"data\":{\"type\":\"venues\",\"id\":\"venue-42\"}}}}}";

        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertEquals("evt-1", data.get("id"));
        assertEquals("events", data.get("type"));

        Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
        assertNotNull(relationships, "relationships block must be present on create response");
        Map<String, Object> venue = (Map<String, Object>) relationships.get("venue");
        assertNotNull(venue);
        Map<String, Object> venueData = (Map<String, Object>) venue.get("data");
        assertEquals("venues", venueData.get("type"));
        assertEquals("venue-42", venueData.get("id"));
    }

    @Test
    @DisplayName("create response echoes caller-supplied relationships even when field is not REFERENCE-typed")
    void createEchoesCallerRelationshipsWhenFieldNotDeclaredAsReference() throws Exception {
        CollectionDefinition def = new CollectionDefinitionBuilder()
                .name("notes")
                .displayName("Notes")
                .addField(FieldDefinition.requiredString("body"))
                .build();
        when(registry.get("notes")).thenReturn(def);

        Map<String, Object> stored = new HashMap<>();
        stored.put("id", "note-1");
        stored.put("body", "draft");
        when(queryEngine.create(eq(def), any())).thenReturn(stored);

        String body = "{\"data\":{\"type\":\"notes\","
                + "\"attributes\":{\"body\":\"draft\"},"
                + "\"relationships\":{\"author\":{\"data\":{\"type\":\"users\",\"id\":\"u-9\"}}}}}";

        MvcResult result = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
        assertNotNull(relationships, "caller-supplied relationships must be echoed back");
        Map<String, Object> author = (Map<String, Object>) relationships.get("author");
        Map<String, Object> authorData = (Map<String, Object>) author.get("data");
        assertEquals("users", authorData.get("type"));
        assertEquals("u-9", authorData.get("id"));
    }

    @Test
    @DisplayName("update response includes relationships block")
    void updateEchoesRelationships() throws Exception {
        CollectionDefinition def = eventsCollection();
        when(registry.get("events")).thenReturn(def);

        Map<String, Object> updated = new HashMap<>();
        updated.put("id", "evt-1");
        updated.put("name", "Spring Concert");
        updated.put("venue", "venue-99");
        when(queryEngine.update(eq(def), eq("evt-1"), any())).thenReturn(Optional.of(updated));

        String body = "{\"data\":{\"type\":\"events\",\"id\":\"evt-1\","
                + "\"attributes\":{\"name\":\"Spring Concert\"},"
                + "\"relationships\":{\"venue\":{\"data\":{\"type\":\"venues\",\"id\":\"venue-99\"}}}}}";

        MvcResult result = mockMvc.perform(patch("/api/events/evt-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
        assertNotNull(relationships);
        Map<String, Object> venue = (Map<String, Object>) relationships.get("venue");
        Map<String, Object> venueData = (Map<String, Object>) venue.get("data");
        assertEquals("venue-99", venueData.get("id"));
    }
}
