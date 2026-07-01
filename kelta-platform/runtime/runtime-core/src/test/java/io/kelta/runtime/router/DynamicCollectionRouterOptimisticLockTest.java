package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Optimistic-locking behavior on the dynamic collection router (unified record experience, slice 5):
 * GET emits an ETag; a write with a stale If-Match returns 409; a matching or absent If-Match
 * proceeds.
 */
@DisplayName("DynamicCollectionRouter Optimistic Locking Tests")
class DynamicCollectionRouterOptimisticLockTest {

    private static final String ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String VERSION = "2026-07-01T12:00:00Z";

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

    private CollectionDefinition def() {
        CollectionDefinition def = new CollectionDefinitionBuilder()
                .name("things")
                .displayName("Things")
                .addField(FieldDefinition.requiredString("name"))
                .build();
        when(registry.get("things")).thenReturn(def);
        return def;
    }

    private Map<String, Object> record() {
        return Map.of("id", ID, "name", "Acme", "updatedAt", VERSION);
    }

    private String expectedEtag() {
        return "\"" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(VERSION.getBytes(StandardCharsets.UTF_8)) + "\"";
    }

    private String patchBody() {
        return "{\"data\":{\"type\":\"things\",\"id\":\"" + ID + "\",\"attributes\":{\"name\":\"New\"}}}";
    }

    @Test
    @DisplayName("GET single record emits an ETag derived from updatedAt")
    void getEmitsEtag() throws Exception {
        CollectionDefinition def = def();
        when(queryEngine.getById(def, ID)).thenReturn(Optional.of(record()));

        mockMvc.perform(get("/api/things/" + ID))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", expectedEtag()));
    }

    @Test
    @DisplayName("PATCH with a matching If-Match proceeds")
    void patchMatchingIfMatchProceeds() throws Exception {
        CollectionDefinition def = def();
        when(queryEngine.getById(def, ID)).thenReturn(Optional.of(record()));
        when(queryEngine.update(eq(def), eq(ID), any())).thenReturn(Optional.of(record()));

        mockMvc.perform(patch("/api/things/" + ID)
                        .header("If-Match", expectedEtag())
                        .contentType("application/json")
                        .content(patchBody()))
                .andExpect(status().isOk());

        verify(queryEngine).update(eq(def), eq(ID), any());
    }

    @Test
    @DisplayName("PATCH with a stale If-Match returns 409 and does not write")
    void patchStaleIfMatchConflicts() throws Exception {
        CollectionDefinition def = def();
        when(queryEngine.getById(def, ID)).thenReturn(Optional.of(record()));

        mockMvc.perform(patch("/api/things/" + ID)
                        .header("If-Match", "\"stale-token\"")
                        .contentType("application/json")
                        .content(patchBody()))
                .andExpect(status().isConflict());

        verify(queryEngine, never()).update(any(), any(), any());
    }

    @Test
    @DisplayName("PATCH without If-Match proceeds (back-compat)")
    void patchNoIfMatchProceeds() throws Exception {
        CollectionDefinition def = def();
        when(queryEngine.update(eq(def), eq(ID), any())).thenReturn(Optional.of(record()));

        mockMvc.perform(patch("/api/things/" + ID)
                        .contentType("application/json")
                        .content(patchBody()))
                .andExpect(status().isOk());

        verify(queryEngine).update(eq(def), eq(ID), any());
        // No If-Match ⇒ no pre-read for version check.
        verify(queryEngine, never()).getById(any(), any());
    }

    @Test
    @DisplayName("DELETE with a stale If-Match returns 409 and does not delete")
    void deleteStaleIfMatchConflicts() throws Exception {
        CollectionDefinition def = def();
        when(queryEngine.getById(def, ID)).thenReturn(Optional.of(record()));

        mockMvc.perform(delete("/api/things/" + ID).header("If-Match", "\"stale-token\""))
                .andExpect(status().isConflict());

        verify(queryEngine, never()).delete(any(), any());
    }
}
