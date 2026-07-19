package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request-origin geo stamping on the dynamic collection router: collections with
 * {@code captureGeo} get createdGeo/updatedGeo stamped from the gateway's X-Geo-*
 * headers; collections without the flag are untouched; absent headers stamp null
 * (the value always describes the origin of the LAST write).
 */
@DisplayName("DynamicCollectionRouter Geo Stamp Tests")
class DynamicCollectionRouterGeoStampTest {

    private static final String ID = "550e8400-e29b-41d4-a716-446655440000";

    private CollectionRegistry registry;
    private QueryEngine queryEngine;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry = mock(CollectionRegistry.class);
        queryEngine = mock(QueryEngine.class);
        DynamicCollectionRouter router = new DynamicCollectionRouter(registry, queryEngine);
        mockMvc = MockMvcBuilders.standaloneSetup(router)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CollectionDefinition def(boolean captureGeo) {
        CollectionDefinition def = new CollectionDefinitionBuilder()
                .name("things")
                .displayName("Things")
                .captureGeo(captureGeo)
                .addField(FieldDefinition.requiredString("name"))
                .build();
        when(registry.get("things")).thenReturn(def);
        return def;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedCreateData(CollectionDefinition def) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(queryEngine).create(eq(def), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("POST stamps createdGeo + updatedGeo from X-Geo-* when captureGeo is on")
    void createStampsGeo() throws Exception {
        CollectionDefinition def = def(true);
        when(queryEngine.create(eq(def), any())).thenAnswer(inv -> inv.getArgument(1));

        mockMvc.perform(post("/api/things")
                        .contentType("application/json")
                        .content("{\"data\":{\"type\":\"things\",\"attributes\":{\"name\":\"Acme\"}}}")
                        .header("X-Geo-Country", "PT")
                        .header("X-Geo-Region", "Lisbon")
                        .header("X-Geo-City", "M%C3%BCnchen")
                        .header("X-Geo-Lat", "38.6979")
                        .header("X-Geo-Lon", "-9.4207"))
                .andExpect(status().isCreated());

        Map<String, Object> data = capturedCreateData(def);
        Map<String, Object> createdGeo = (Map<String, Object>) data.get("createdGeo");
        assertThat(createdGeo)
                .containsEntry("country", "PT")
                .containsEntry("region", "Lisbon")
                .containsEntry("city", "München")
                .containsEntry("lat", 38.6979)
                .containsEntry("lon", -9.4207);
        assertThat(data.get("updatedGeo")).isEqualTo(createdGeo);
    }

    @Test
    @DisplayName("POST without geo headers stamps explicit nulls when captureGeo is on")
    void createWithoutHeadersStampsNull() throws Exception {
        CollectionDefinition def = def(true);
        when(queryEngine.create(eq(def), any())).thenAnswer(inv -> inv.getArgument(1));

        mockMvc.perform(post("/api/things")
                        .contentType("application/json")
                        .content("{\"data\":{\"type\":\"things\",\"attributes\":{\"name\":\"Acme\"}}}"))
                .andExpect(status().isCreated());

        Map<String, Object> data = capturedCreateData(def);
        assertThat(data).containsKey("createdGeo").containsKey("updatedGeo");
        assertThat(data.get("createdGeo")).isNull();
        assertThat(data.get("updatedGeo")).isNull();
    }

    @Test
    @DisplayName("POST leaves geo keys absent when captureGeo is off")
    void createWithoutFlagStampsNothing() throws Exception {
        CollectionDefinition def = def(false);
        when(queryEngine.create(eq(def), any())).thenAnswer(inv -> inv.getArgument(1));

        mockMvc.perform(post("/api/things")
                        .contentType("application/json")
                        .content("{\"data\":{\"type\":\"things\",\"attributes\":{\"name\":\"Acme\"}}}")
                        .header("X-Geo-Country", "PT"))
                .andExpect(status().isCreated());

        Map<String, Object> data = capturedCreateData(def);
        assertThat(data).doesNotContainKeys("createdGeo", "updatedGeo");
    }

    @Test
    @DisplayName("PATCH stamps updatedGeo only")
    @SuppressWarnings("unchecked")
    void updateStampsUpdatedGeoOnly() throws Exception {
        CollectionDefinition def = def(true);
        when(queryEngine.getById(def, ID)).thenReturn(Optional.of(
                Map.of("id", ID, "name", "Acme", "updatedAt", "2026-07-01T12:00:00Z")));
        when(queryEngine.update(eq(def), eq(ID), any())).thenAnswer(inv ->
                Optional.of(inv.getArgument(2)));

        mockMvc.perform(patch("/api/things/" + ID)
                        .contentType("application/json")
                        .content("{\"data\":{\"type\":\"things\",\"id\":\"" + ID
                                + "\",\"attributes\":{\"name\":\"New\"}}}")
                        .header("X-Geo-Country", "DE"))
                .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(queryEngine).update(eq(def), eq(ID), captor.capture());
        Map<String, Object> data = captor.getValue();
        assertThat(data).doesNotContainKey("createdGeo");
        assertThat((Map<String, Object>) data.get("updatedGeo")).containsEntry("country", "DE");
    }
}
