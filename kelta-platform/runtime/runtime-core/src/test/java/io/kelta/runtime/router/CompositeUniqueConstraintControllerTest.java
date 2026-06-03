package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.CompositeUniqueConstraint;
import io.kelta.runtime.storage.PhysicalTableStorageAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompositeUniqueConstraintControllerTest {

    private CollectionRegistry registry;
    private PhysicalTableStorageAdapter storage;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        registry = mock(CollectionRegistry.class);
        storage = mock(PhysicalTableStorageAdapter.class);
        CompositeUniqueConstraintController controller =
                new CompositeUniqueConstraintController(registry, storage);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    private static CollectionDefinition availability() {
        return new CollectionDefinitionBuilder()
                .name("availability")
                .displayName("Availability")
                .addField(FieldDefinition.requiredString("title"))
                .addField(FieldDefinition.requiredString("provider"))
                .addField(FieldDefinition.requiredString("region"))
                .build();
    }

    @Test
    void createReturns201AndDelegatesToStorage() throws Exception {
        CollectionDefinition def = availability();
        when(registry.get("availability")).thenReturn(def);
        when(storage.addCompositeUniqueConstraint(eq(def), eq(List.of("title", "provider", "region"))))
                .thenReturn("cuq_availability_deadbeef");

        String body = objectMapper.writeValueAsString(Map.of(
                "collectionName", "availability",
                "fieldNames", List.of("title", "provider", "region")));

        mockMvc.perform(post("/api/_composite-unique-constraints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.collectionName").value("availability"))
                .andExpect(jsonPath("$.data.constraintName").value("cuq_availability_deadbeef"))
                .andExpect(jsonPath("$.data.fieldNames[0]").value("title"))
                .andExpect(jsonPath("$.data.fieldNames[2]").value("region"));

        verify(storage).addCompositeUniqueConstraint(def, List.of("title", "provider", "region"));
    }

    @Test
    void createReturns400WhenFewerThanTwoFields() throws Exception {
        when(registry.get("availability")).thenReturn(availability());

        String body = objectMapper.writeValueAsString(Map.of(
                "collectionName", "availability",
                "fieldNames", List.of("title")));

        mockMvc.perform(post("/api/_composite-unique-constraints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail").value("'fieldNames' must list at least 2 fields"));

        verify(storage, never()).addCompositeUniqueConstraint(any(), any());
    }

    @Test
    void createReturns404WhenCollectionUnknown() throws Exception {
        when(registry.get("ghost")).thenReturn(null);

        String body = objectMapper.writeValueAsString(Map.of(
                "collectionName", "ghost",
                "fieldNames", List.of("a", "b")));

        mockMvc.perform(post("/api/_composite-unique-constraints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());

        verify(storage, never()).addCompositeUniqueConstraint(any(), any());
    }

    @Test
    void createSurfacesStorageValidationErrorAs400() throws Exception {
        CollectionDefinition def = availability();
        when(registry.get("availability")).thenReturn(def);
        when(storage.addCompositeUniqueConstraint(any(), any()))
                .thenThrow(new IllegalArgumentException("Field 'ghost' is not defined on collection 'availability'"));

        String body = objectMapper.writeValueAsString(Map.of(
                "collectionName", "availability",
                "fieldNames", List.of("title", "ghost")));

        mockMvc.perform(post("/api/_composite-unique-constraints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail")
                        .value("Field 'ghost' is not defined on collection 'availability'"));
    }

    @Test
    void listReturnsConstraintsFromStorage() throws Exception {
        CollectionDefinition def = availability();
        when(registry.get("availability")).thenReturn(def);
        when(storage.listCompositeUniqueConstraints(def)).thenReturn(List.of(
                new CompositeUniqueConstraint("cuq_availability_aabbccdd",
                        List.of("title", "provider", "region"))));

        mockMvc.perform(get("/api/_composite-unique-constraints").param("collection", "availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].constraintName").value("cuq_availability_aabbccdd"))
                .andExpect(jsonPath("$.data[0].fieldNames[0]").value("title"));
    }

    @Test
    void deleteReturns204WhenDropped() throws Exception {
        CollectionDefinition def = availability();
        when(registry.get("availability")).thenReturn(def);
        when(storage.dropCompositeUniqueConstraint(def, List.of("title", "provider", "region")))
                .thenReturn(true);

        mockMvc.perform(delete("/api/_composite-unique-constraints")
                        .param("collection", "availability")
                        .param("fieldNames", "title,provider,region"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenConstraintAbsent() throws Exception {
        CollectionDefinition def = availability();
        when(registry.get("availability")).thenReturn(def);
        when(storage.dropCompositeUniqueConstraint(def, List.of("title", "provider")))
                .thenReturn(false);

        mockMvc.perform(delete("/api/_composite-unique-constraints")
                        .param("collection", "availability")
                        .param("fieldNames", "title,provider"))
                .andExpect(status().isNotFound());
    }
}
