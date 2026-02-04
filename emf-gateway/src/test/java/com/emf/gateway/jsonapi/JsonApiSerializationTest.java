package com.emf.gateway.jsonapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JSON serialization and deserialization of JSON:API models.
 */
class JsonApiSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeAndDeserializeResourceIdentifier() throws Exception {
        ResourceIdentifier original = new ResourceIdentifier("users", "123");
        
        String json = objectMapper.writeValueAsString(original);
        ResourceIdentifier deserialized = objectMapper.readValue(json, ResourceIdentifier.class);
        
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getId(), deserialized.getId());
    }

    @Test
    void shouldSerializeAndDeserializeRelationshipWithSingleResource() throws Exception {
        Relationship original = new Relationship(new ResourceIdentifier("users", "123"));
        
        String json = objectMapper.writeValueAsString(original);
        Relationship deserialized = objectMapper.readValue(json, Relationship.class);
        
        assertNotNull(deserialized.getData());
    }

    @Test
    void shouldSerializeAndDeserializeResourceObject() throws Exception {
        ResourceObject original = new ResourceObject("users", "123");
        original.addAttribute("name", "John Doe");
        original.addAttribute("email", "john@example.com");
        original.addRelationship("posts", new Relationship(
            new ResourceIdentifier("posts", "1")
        ));
        
        String json = objectMapper.writeValueAsString(original);
        ResourceObject deserialized = objectMapper.readValue(json, ResourceObject.class);
        
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getId(), deserialized.getId());
        assertEquals("John Doe", deserialized.getAttributes().get("name"));
        assertEquals("john@example.com", deserialized.getAttributes().get("email"));
        assertNotNull(deserialized.getRelationships().get("posts"));
    }

    @Test
    void shouldSerializeAndDeserializeJsonApiDocument() throws Exception {
        JsonApiDocument original = new JsonApiDocument();
        
        ResourceObject user = new ResourceObject("users", "123");
        user.addAttribute("name", "John Doe");
        original.addData(user);
        
        ResourceObject post = new ResourceObject("posts", "1");
        post.addAttribute("title", "My Post");
        original.addIncluded(post);
        
        original.addMeta("total", 1);
        
        String json = objectMapper.writeValueAsString(original);
        JsonApiDocument deserialized = objectMapper.readValue(json, JsonApiDocument.class);
        
        assertTrue(deserialized.hasData());
        assertTrue(deserialized.hasIncluded());
        assertEquals(1, deserialized.getData().size());
        assertEquals(1, deserialized.getIncluded().size());
        assertEquals("users", deserialized.getData().get(0).getType());
        assertEquals("123", deserialized.getData().get(0).getId());
        assertEquals(1, deserialized.getMeta().get("total"));
    }

    @Test
    void shouldSerializeDocumentWithErrors() throws Exception {
        JsonApiDocument original = new JsonApiDocument();
        original.addError(new JsonApiError("400", "BAD_REQUEST", "Bad Request", "Invalid input"));
        
        String json = objectMapper.writeValueAsString(original);
        JsonApiDocument deserialized = objectMapper.readValue(json, JsonApiDocument.class);
        
        assertTrue(deserialized.hasErrors());
        assertEquals(1, deserialized.getErrors().size());
        assertEquals("400", deserialized.getErrors().get(0).getStatus());
    }

    @Test
    void shouldOmitNullFieldsInSerialization() throws Exception {
        ResourceObject resource = new ResourceObject("users", "123");
        // Don't add any attributes or relationships
        
        String json = objectMapper.writeValueAsString(resource);
        
        assertTrue(json.contains("\"type\":\"users\""));
        assertTrue(json.contains("\"id\":\"123\""));
        // Should not contain attributes or relationships if they're empty
        // (they may be omitted entirely or shown as empty objects depending on @JsonInclude)
    }

    @Test
    void shouldHandleComplexJsonApiDocument() throws Exception {
        // Create a realistic JSON:API document
        JsonApiDocument document = new JsonApiDocument();
        
        // Primary data - a post with author relationship
        ResourceObject post = new ResourceObject("posts", "1");
        post.addAttribute("title", "JSON:API paints my bikeshed!");
        post.addAttribute("body", "The shortest article. Ever.");
        post.addRelationship("author", new Relationship(
            new ResourceIdentifier("users", "9")
        ));
        post.addRelationship("comments", new Relationship(
            Arrays.asList(
                new ResourceIdentifier("comments", "5"),
                new ResourceIdentifier("comments", "12")
            )
        ));
        document.addData(post);
        
        // Included resources
        ResourceObject author = new ResourceObject("users", "9");
        author.addAttribute("firstName", "Dan");
        author.addAttribute("lastName", "Gebhardt");
        document.addIncluded(author);
        
        ResourceObject comment1 = new ResourceObject("comments", "5");
        comment1.addAttribute("body", "First!");
        document.addIncluded(comment1);
        
        ResourceObject comment2 = new ResourceObject("comments", "12");
        comment2.addAttribute("body", "I like XML better");
        document.addIncluded(comment2);
        
        // Serialize and deserialize
        String json = objectMapper.writeValueAsString(document);
        JsonApiDocument deserialized = objectMapper.readValue(json, JsonApiDocument.class);
        
        // Verify structure
        assertEquals(1, deserialized.getData().size());
        assertEquals(3, deserialized.getIncluded().size());
        
        ResourceObject deserializedPost = deserialized.getData().get(0);
        assertEquals("posts", deserializedPost.getType());
        assertEquals("1", deserializedPost.getId());
        assertEquals("JSON:API paints my bikeshed!", deserializedPost.getAttributes().get("title"));
        
        // Verify relationships exist
        assertNotNull(deserializedPost.getRelationships().get("author"));
        assertNotNull(deserializedPost.getRelationships().get("comments"));
    }
}
