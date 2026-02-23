package com.emf.controlplane.controller;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.service.CollectionService;
import com.emf.runtime.datapath.CollectionDefinitionProvider;
import com.emf.runtime.datapath.DataPath;
import com.emf.runtime.datapath.DataPathValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataPathControllerTest {

    private CollectionService collectionService;
    private FieldRepository fieldRepository;
    private DataPathController controller;

    @BeforeEach
    void setUp() {
        collectionService = mock(CollectionService.class);
        fieldRepository = mock(FieldRepository.class);
        controller = new DataPathController(collectionService, fieldRepository, null);
    }

    @Test
    @DisplayName("Should return field tree for collection")
    void shouldReturnFieldTree() {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("orders");
        when(collectionService.getCollection("col-1")).thenReturn(collection);

        Field nameField = createField("name", "Name", "string", false, null);
        Field statusField = createField("status", "Status", "string", false, null);
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-1"))
            .thenReturn(List.of(nameField, statusField));

        List<Map<String, Object>> result = controller.getDataPaths("col-1", 2);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("name", result.get(0).get("name"));
        assertEquals("Name", result.get(0).get("displayName"));
        assertEquals("string", result.get(0).get("type"));
        assertEquals(false, result.get(0).get("isRelationship"));
    }

    @Test
    @DisplayName("Should expand relationship fields within depth")
    void shouldExpandRelationshipFields() {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("order_lines");
        when(collectionService.getCollection("col-1")).thenReturn(collection);

        Field orderRef = createField("order_id", "Order", "reference", true, "col-2");
        Field amountField = createField("amount", "Amount", "number", false, null);
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-1"))
            .thenReturn(List.of(orderRef, amountField));

        // Child fields for the referenced collection
        Field orderName = createField("name", "Order Name", "string", false, null);
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-2"))
            .thenReturn(List.of(orderName));

        List<Map<String, Object>> result = controller.getDataPaths("col-1", 2);

        assertNotNull(result);
        assertEquals(2, result.size());

        // The relationship field should have children
        Map<String, Object> orderNode = result.get(0);
        assertEquals("order_id", orderNode.get("name"));
        assertEquals(true, orderNode.get("isRelationship"));
        assertEquals("col-2", orderNode.get("targetCollectionId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) orderNode.get("children");
        assertNotNull(children);
        assertEquals(1, children.size());
        assertEquals("name", children.get(0).get("name"));
    }

    @Test
    @DisplayName("Should clamp depth to max 5")
    void shouldClampDepthToMax() {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("orders");
        when(collectionService.getCollection("col-1")).thenReturn(collection);
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-1"))
            .thenReturn(List.of());

        // Requesting depth 10 should be clamped to 5
        List<Map<String, Object>> result = controller.getDataPaths("col-1", 10);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should clamp depth to min 1")
    void shouldClampDepthToMin() {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("orders");
        when(collectionService.getCollection("col-1")).thenReturn(collection);
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-1"))
            .thenReturn(List.of());

        // Requesting depth 0 should be clamped to 1
        List<Map<String, Object>> result = controller.getDataPaths("col-1", 0);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle empty field list")
    void shouldHandleEmptyFieldList() {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("orders");
        when(collectionService.getCollection("col-1")).thenReturn(collection);
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-1"))
            .thenReturn(List.of());

        List<Map<String, Object>> result = controller.getDataPaths("col-1", 2);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should detect lookup relationship type")
    void shouldDetectLookupRelationshipType() {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("orders");
        when(collectionService.getCollection("col-1")).thenReturn(collection);

        Field lookupField = createField("customer_id", "Customer", "lookup", true, "col-2");
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-1"))
            .thenReturn(List.of(lookupField));
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-2"))
            .thenReturn(List.of());

        List<Map<String, Object>> result = controller.getDataPaths("col-1", 2);

        assertEquals(1, result.size());
        assertEquals(true, result.get(0).get("isRelationship"));
    }

    @Test
    @DisplayName("Should detect master_detail relationship type")
    void shouldDetectMasterDetailRelationshipType() {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("orders");
        when(collectionService.getCollection("col-1")).thenReturn(collection);

        Field mdField = createField("parent_id", "Parent", "master_detail", true, "col-3");
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-1"))
            .thenReturn(List.of(mdField));
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-3"))
            .thenReturn(List.of());

        List<Map<String, Object>> result = controller.getDataPaths("col-1", 2);

        assertEquals(1, result.size());
        assertEquals(true, result.get(0).get("isRelationship"));
    }

    @Test
    @DisplayName("Should prevent circular reference expansion")
    void shouldPreventCircularReferences() {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("orders");
        when(collectionService.getCollection("col-1")).thenReturn(collection);

        // col-1 has reference to col-2, col-2 has reference back to col-1
        Field ref1 = createField("ref", "Ref", "reference", true, "col-2");
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-1"))
            .thenReturn(List.of(ref1));

        Field ref2 = createField("back_ref", "Back Ref", "reference", true, "col-1");
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-2"))
            .thenReturn(List.of(ref2));

        // Should not loop infinitely
        List<Map<String, Object>> result = controller.getDataPaths("col-1", 5);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should use displayName from field when available")
    void shouldUseDisplayName() {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("orders");
        when(collectionService.getCollection("col-1")).thenReturn(collection);

        Field field = createField("email", "Email Address", "string", false, null);
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-1"))
            .thenReturn(List.of(field));

        List<Map<String, Object>> result = controller.getDataPaths("col-1", 2);

        assertEquals("Email Address", result.get(0).get("displayName"));
    }

    @Test
    @DisplayName("Should fall back to name when displayName is null")
    void shouldFallBackToNameWhenDisplayNameNull() {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("orders");
        when(collectionService.getCollection("col-1")).thenReturn(collection);

        Field field = createField("email", null, "string", false, null);
        when(fieldRepository.findByCollectionIdAndActiveTrue("col-1"))
            .thenReturn(List.of(field));

        List<Map<String, Object>> result = controller.getDataPaths("col-1", 2);

        assertEquals("email", result.get(0).get("displayName"));
    }

    @Test
    @DisplayName("Should return validation error when no provider configured")
    void shouldReturnValidationErrorWhenNoProvider() {
        // Controller created with null collectionDefinitionProvider
        DataPathController.DataPathValidateRequest request = new DataPathController.DataPathValidateRequest();
        request.setRootCollectionId("col-1");
        request.setExpression("name");

        Map<String, Object> result = controller.validateDataPath(request);

        assertFalse((Boolean) result.get("valid"));
        assertNotNull(result.get("errorMessage"));
    }

    private Field createField(String name, String displayName, String type,
                               boolean isRelationship, String referenceCollectionId) {
        Field field = new Field();
        field.setName(name);
        field.setDisplayName(displayName);
        field.setType(type);
        if (referenceCollectionId != null) {
            field.setReferenceCollectionId(referenceCollectionId);
        }
        return field;
    }
}
