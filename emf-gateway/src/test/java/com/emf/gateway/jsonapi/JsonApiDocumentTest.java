package com.emf.gateway.jsonapi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonApiDocumentTest {

    @Test
    void shouldCreateDocumentWithData() {
        List<ResourceObject> data = new ArrayList<>();
        data.add(new ResourceObject("users", "123"));
        
        JsonApiDocument document = new JsonApiDocument(data);
        
        assertEquals(data, document.getData());
        assertTrue(document.hasData());
    }

    @Test
    void shouldSupportDefaultConstructor() {
        JsonApiDocument document = new JsonApiDocument();
        
        assertNull(document.getData());
        assertNull(document.getIncluded());
        assertNull(document.getMeta());
        assertNull(document.getErrors());
    }

    @Test
    void shouldSupportSetters() {
        JsonApiDocument document = new JsonApiDocument();
        
        List<ResourceObject> data = new ArrayList<>();
        data.add(new ResourceObject("users", "123"));
        document.setData(data);
        
        List<ResourceObject> included = new ArrayList<>();
        included.add(new ResourceObject("posts", "1"));
        document.setIncluded(included);
        
        Map<String, Object> meta = new HashMap<>();
        meta.put("total", 100);
        document.setMeta(meta);
        
        List<JsonApiError> errors = new ArrayList<>();
        errors.add(new JsonApiError("400", "BAD_REQUEST", "Bad Request", "Invalid input"));
        document.setErrors(errors);
        
        assertEquals(data, document.getData());
        assertEquals(included, document.getIncluded());
        assertEquals(meta, document.getMeta());
        assertEquals(errors, document.getErrors());
    }

    @Test
    void shouldAddDataResource() {
        JsonApiDocument document = new JsonApiDocument();
        ResourceObject resource = new ResourceObject("users", "123");
        
        document.addData(resource);
        
        assertNotNull(document.getData());
        assertEquals(1, document.getData().size());
        assertEquals(resource, document.getData().get(0));
        assertTrue(document.hasData());
    }

    @Test
    void shouldAddIncludedResource() {
        JsonApiDocument document = new JsonApiDocument();
        ResourceObject resource = new ResourceObject("posts", "1");
        
        document.addIncluded(resource);
        
        assertNotNull(document.getIncluded());
        assertEquals(1, document.getIncluded().size());
        assertEquals(resource, document.getIncluded().get(0));
        assertTrue(document.hasIncluded());
    }

    @Test
    void shouldAddError() {
        JsonApiDocument document = new JsonApiDocument();
        JsonApiError error = new JsonApiError("400", "BAD_REQUEST", "Bad Request", "Invalid input");
        
        document.addError(error);
        
        assertNotNull(document.getErrors());
        assertEquals(1, document.getErrors().size());
        assertEquals(error, document.getErrors().get(0));
        assertTrue(document.hasErrors());
    }

    @Test
    void shouldAddMeta() {
        JsonApiDocument document = new JsonApiDocument();
        
        document.addMeta("total", 100);
        document.addMeta("page", 1);
        
        assertNotNull(document.getMeta());
        assertEquals(2, document.getMeta().size());
        assertEquals(100, document.getMeta().get("total"));
        assertEquals(1, document.getMeta().get("page"));
    }

    @Test
    void shouldCheckHasData() {
        JsonApiDocument document = new JsonApiDocument();
        
        assertFalse(document.hasData());
        
        document.addData(new ResourceObject("users", "123"));
        
        assertTrue(document.hasData());
    }

    @Test
    void shouldCheckHasIncluded() {
        JsonApiDocument document = new JsonApiDocument();
        
        assertFalse(document.hasIncluded());
        
        document.addIncluded(new ResourceObject("posts", "1"));
        
        assertTrue(document.hasIncluded());
    }

    @Test
    void shouldCheckHasErrors() {
        JsonApiDocument document = new JsonApiDocument();
        
        assertFalse(document.hasErrors());
        
        document.addError(new JsonApiError("400", "BAD_REQUEST", "Bad Request", "Invalid input"));
        
        assertTrue(document.hasErrors());
    }

    @Test
    void shouldHandleEmptyLists() {
        JsonApiDocument document = new JsonApiDocument();
        document.setData(new ArrayList<>());
        document.setIncluded(new ArrayList<>());
        document.setErrors(new ArrayList<>());
        
        assertFalse(document.hasData());
        assertFalse(document.hasIncluded());
        assertFalse(document.hasErrors());
    }

    @Test
    void shouldSupportComplexDocument() {
        JsonApiDocument document = new JsonApiDocument();
        
        // Add primary data
        ResourceObject user = new ResourceObject("users", "123");
        user.addAttribute("name", "John Doe");
        user.addAttribute("email", "john@example.com");
        user.addRelationship("posts", new Relationship(
            new ResourceIdentifier("posts", "1")
        ));
        document.addData(user);
        
        // Add included resources
        ResourceObject post = new ResourceObject("posts", "1");
        post.addAttribute("title", "My Post");
        document.addIncluded(post);
        
        // Add meta
        document.addMeta("total", 1);
        
        assertTrue(document.hasData());
        assertTrue(document.hasIncluded());
        assertFalse(document.hasErrors());
        assertEquals(1, document.getData().size());
        assertEquals(1, document.getIncluded().size());
        assertEquals(1, document.getMeta().size());
    }

    @Test
    void shouldProvideToString() {
        JsonApiDocument document = new JsonApiDocument();
        document.addData(new ResourceObject("users", "123"));
        String str = document.toString();
        
        assertTrue(str.contains("JsonApiDocument"));
        assertTrue(str.contains("data="));
    }
}
