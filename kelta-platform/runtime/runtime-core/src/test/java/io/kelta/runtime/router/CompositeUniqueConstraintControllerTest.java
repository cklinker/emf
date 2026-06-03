package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.CompositeUniqueConstraint;
import io.kelta.runtime.storage.PhysicalTableStorageAdapter;
import io.kelta.runtime.storage.StorageException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for {@link CompositeUniqueConstraintController}. Covers
 * the JSON:API request shape, validation of required fields, and how the
 * controller surfaces service-level errors as HTTP statuses.
 */
class CompositeUniqueConstraintControllerTest {

    private CollectionRegistry registry;
    private PhysicalTableStorageAdapter storageAdapter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry = mock(CollectionRegistry.class);
        storageAdapter = mock(PhysicalTableStorageAdapter.class);
        CompositeUniqueConstraintController controller =
                new CompositeUniqueConstraintController(registry, storageAdapter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private CollectionDefinition collectionWithFields(String name, String... fieldNames) {
        CollectionDefinitionBuilder b = new CollectionDefinitionBuilder()
                .name(name)
                .displayName(name);
        for (String f : fieldNames) {
            b.addField(FieldDefinition.requiredString(f));
        }
        return b.build();
    }

    @Test
    @DisplayName("POST creates a composite unique constraint and returns 201")
    void postCreatesConstraint() throws Exception {
        CollectionDefinition def = collectionWithFields(
                "availability", "title", "provider", "region");
        when(registry.get("availability")).thenReturn(def);
        when(storageAdapter.createCompositeUniqueConstraint(
                eq(def), eq(List.of("title", "provider", "region"))))
                .thenReturn("cuq_availability_8864be4f");

        String body = "{"
                + "\"data\":{"
                + "\"type\":\"compositeUniqueConstraints\","
                + "\"attributes\":{"
                + "\"collectionName\":\"availability\","
                + "\"fieldNames\":[\"title\",\"provider\",\"region\"]"
                + "}}}";

        mockMvc.perform(post("/api/_composite-unique-constraints")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("compositeUniqueConstraints"))
                .andExpect(jsonPath("$.data.id").value("cuq_availability_8864be4f"))
                .andExpect(jsonPath("$.data.attributes.collectionName").value("availability"))
                .andExpect(jsonPath("$.data.attributes.fieldNames[0]").value("title"))
                .andExpect(jsonPath("$.data.attributes.fieldNames[2]").value("region"));
    }

    @Test
    @DisplayName("POST returns 404 when the collection is unknown")
    void postReturns404ForUnknownCollection() throws Exception {
        when(registry.get("ghost")).thenReturn(null);
        String body = "{\"data\":{\"type\":\"compositeUniqueConstraints\",\"attributes\":{"
                + "\"collectionName\":\"ghost\",\"fieldNames\":[\"a\",\"b\"]}}}";
        mockMvc.perform(post("/api/_composite-unique-constraints")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
        verify(storageAdapter, never()).createCompositeUniqueConstraint(any(), any());
    }

    @Test
    @DisplayName("POST returns 400 when fieldNames has fewer than 2 entries")
    void postRejectsSingleFieldList() throws Exception {
        String body = "{\"data\":{\"type\":\"compositeUniqueConstraints\",\"attributes\":{"
                + "\"collectionName\":\"availability\",\"fieldNames\":[\"title\"]}}}";
        mockMvc.perform(post("/api/_composite-unique-constraints")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail").value(
                        org.hamcrest.Matchers.containsString("at least 2")));
        verify(registry, never()).get(any());
    }

    @Test
    @DisplayName("POST returns 400 when collectionName is missing")
    void postRejectsMissingCollectionName() throws Exception {
        String body = "{\"data\":{\"type\":\"compositeUniqueConstraints\",\"attributes\":{"
                + "\"fieldNames\":[\"a\",\"b\"]}}}";
        mockMvc.perform(post("/api/_composite-unique-constraints")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST surfaces an unknown-field error from the adapter as 400")
    void postSurfacesUnknownFieldAsBadRequest() throws Exception {
        CollectionDefinition def = collectionWithFields("availability", "title", "provider");
        when(registry.get("availability")).thenReturn(def);
        when(storageAdapter.createCompositeUniqueConstraint(any(), any()))
                .thenThrow(new IllegalArgumentException("Field 'region' does not exist"));

        String body = "{\"data\":{\"type\":\"compositeUniqueConstraints\",\"attributes\":{"
                + "\"collectionName\":\"availability\","
                + "\"fieldNames\":[\"title\",\"region\"]}}}";

        mockMvc.perform(post("/api/_composite-unique-constraints")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail").value(
                        org.hamcrest.Matchers.containsString("region")));
    }

    @Test
    @DisplayName("POST surfaces a storage failure as 500")
    void postSurfacesStorageExceptionAs500() throws Exception {
        CollectionDefinition def = collectionWithFields("availability", "title", "provider");
        when(registry.get("availability")).thenReturn(def);
        when(storageAdapter.createCompositeUniqueConstraint(any(), any()))
                .thenThrow(new StorageException("DDL blew up"));

        String body = "{\"data\":{\"type\":\"compositeUniqueConstraints\",\"attributes\":{"
                + "\"collectionName\":\"availability\","
                + "\"fieldNames\":[\"title\",\"provider\"]}}}";

        mockMvc.perform(post("/api/_composite-unique-constraints")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET lists composite constraints for a collection")
    void getListsConstraints() throws Exception {
        CollectionDefinition def = collectionWithFields(
                "availability", "title", "provider", "region");
        when(registry.get("availability")).thenReturn(def);
        when(storageAdapter.listCompositeUniqueConstraints(def)).thenReturn(List.of(
                new CompositeUniqueConstraint("cuq_availability_8864be4f",
                        List.of("title", "provider", "region")),
                new CompositeUniqueConstraint("cuq_availability_aabbccdd",
                        List.of("provider", "region"))));

        mockMvc.perform(get("/api/_composite-unique-constraints")
                        .param("collectionName", "availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("cuq_availability_8864be4f"))
                .andExpect(jsonPath("$.data[0].attributes.fieldNames", hasSize(3)))
                .andExpect(jsonPath("$.data[1].attributes.fieldNames[0]").value("provider"));
    }

    @Test
    @DisplayName("DELETE removes a constraint and returns 204")
    void deleteRemovesConstraint() throws Exception {
        CollectionDefinition def = collectionWithFields("availability", "title", "provider");
        when(registry.get("availability")).thenReturn(def);

        mockMvc.perform(delete("/api/_composite-unique-constraints/cuq_availability_bb5fc63d")
                        .param("collectionName", "availability"))
                .andExpect(status().isNoContent());

        verify(storageAdapter).dropCompositeUniqueConstraint(def, "cuq_availability_bb5fc63d");
    }

    @Test
    @DisplayName("DELETE returns 404 when the collection is unknown")
    void deleteReturns404ForUnknownCollection() throws Exception {
        when(registry.get("ghost")).thenReturn(null);
        mockMvc.perform(delete("/api/_composite-unique-constraints/cuq_x")
                        .param("collectionName", "ghost"))
                .andExpect(status().isNotFound());
        verify(storageAdapter, never()).dropCompositeUniqueConstraint(any(), any());
    }

    private static org.hamcrest.Matcher<java.util.Collection<?>> hasSize(int n) {
        return org.hamcrest.Matchers.hasSize(n);
    }
}
