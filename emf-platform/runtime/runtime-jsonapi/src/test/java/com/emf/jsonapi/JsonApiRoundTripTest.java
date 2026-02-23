package com.emf.jsonapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that verifies round-trip serialization and parsing of JSON:API documents.
 * This ensures that JsonApiParser can correctly parse documents that were serialized
 * using Jackson's ObjectMapper.
 */
class JsonApiRoundTripTest {

    private ObjectMapper objectMapper;
    private JsonApiParser parser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parser = new JsonApiParser(objectMapper);
    }

    @Test
    void shouldRoundTripSingleResource() throws Exception {
        // Create a document
        JsonApiDocument original = new JsonApiDocument();
        ResourceObject user = new ResourceObject("users", "123");
        user.addAttribute("name", "John Doe");
        user.addAttribute("email", "john@example.com");
        original.addData(user);

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Parse back
        JsonApiDocument parsed = parser.parse(json);

        // Verify
        assertEquals(1, parsed.getData().size());
        assertEquals("users", parsed.getData().get(0).getType());
        assertEquals("123", parsed.getData().get(0).getId());
        assertEquals("John Doe", parsed.getData().get(0).getAttributes().get("name"));
        assertEquals("john@example.com", parsed.getData().get(0).getAttributes().get("email"));
    }

    @Test
    void shouldRoundTripCollection() throws Exception {
        // Create a document with multiple resources
        JsonApiDocument original = new JsonApiDocument();

        ResourceObject user1 = new ResourceObject("users", "1");
        user1.addAttribute("name", "Alice");
        original.addData(user1);

        ResourceObject user2 = new ResourceObject("users", "2");
        user2.addAttribute("name", "Bob");
        original.addData(user2);

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Parse back
        JsonApiDocument parsed = parser.parse(json);

        // Verify
        assertEquals(2, parsed.getData().size());
        assertEquals("Alice", parsed.getData().get(0).getAttributes().get("name"));
        assertEquals("Bob", parsed.getData().get(1).getAttributes().get("name"));
    }

    @Test
    void shouldRoundTripWithRelationships() throws Exception {
        // Create a post with author relationship
        JsonApiDocument original = new JsonApiDocument();

        ResourceObject post = new ResourceObject("posts", "1");
        post.addAttribute("title", "My Post");
        post.addRelationship("author", new Relationship(
            new ResourceIdentifier("users", "123")
        ));
        post.addRelationship("comments", new Relationship(
            Arrays.asList(
                new ResourceIdentifier("comments", "5"),
                new ResourceIdentifier("comments", "12")
            )
        ));
        original.addData(post);

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Parse back
        JsonApiDocument parsed = parser.parse(json);

        // Verify
        ResourceObject parsedPost = parsed.getData().get(0);
        assertEquals("posts", parsedPost.getType());
        assertEquals("1", parsedPost.getId());
        assertEquals("My Post", parsedPost.getAttributes().get("title"));

        // Verify single relationship
        Relationship authorRel = parsedPost.getRelationships().get("author");
        assertNotNull(authorRel);
        assertTrue(authorRel.isSingleResource());
        assertEquals("users", authorRel.getDataAsSingle().getType());
        assertEquals("123", authorRel.getDataAsSingle().getId());

        // Verify collection relationship
        Relationship commentsRel = parsedPost.getRelationships().get("comments");
        assertNotNull(commentsRel);
        assertTrue(commentsRel.isResourceCollection());
        assertEquals(2, commentsRel.getDataAsCollection().size());
    }

    @Test
    void shouldRoundTripWithIncludedResources() throws Exception {
        // Create a document with included resources
        JsonApiDocument original = new JsonApiDocument();

        ResourceObject post = new ResourceObject("posts", "1");
        post.addAttribute("title", "My Post");
        post.addRelationship("author", new Relationship(
            new ResourceIdentifier("users", "9")
        ));
        original.addData(post);

        ResourceObject author = new ResourceObject("users", "9");
        author.addAttribute("firstName", "Dan");
        author.addAttribute("lastName", "Gebhardt");
        original.addIncluded(author);

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Parse back
        JsonApiDocument parsed = parser.parse(json);

        // Verify
        assertTrue(parsed.hasData());
        assertTrue(parsed.hasIncluded());
        assertEquals(1, parsed.getIncluded().size());

        ResourceObject parsedAuthor = parsed.getIncluded().get(0);
        assertEquals("users", parsedAuthor.getType());
        assertEquals("9", parsedAuthor.getId());
        assertEquals("Dan", parsedAuthor.getAttributes().get("firstName"));
        assertEquals("Gebhardt", parsedAuthor.getAttributes().get("lastName"));
    }

    @Test
    void shouldRoundTripWithMeta() throws Exception {
        // Create a document with meta
        JsonApiDocument original = new JsonApiDocument();
        original.setData(Arrays.asList());
        original.addMeta("total", 100);
        original.addMeta("page", 1);

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Parse back
        JsonApiDocument parsed = parser.parse(json);

        // Verify
        assertNotNull(parsed.getMeta());
        assertEquals(100, parsed.getMeta().get("total"));
        assertEquals(1, parsed.getMeta().get("page"));
    }

    @Test
    void shouldRoundTripErrors() throws Exception {
        // Create a document with errors
        JsonApiDocument original = new JsonApiDocument();
        original.addError(new JsonApiError("400", "BAD_REQUEST", "Bad Request", "Invalid input"));
        original.addError(new JsonApiError("422", "VALIDATION_ERROR", "Validation Failed", "Name is required"));

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Parse back
        JsonApiDocument parsed = parser.parse(json);

        // Verify
        assertTrue(parsed.hasErrors());
        assertEquals(2, parsed.getErrors().size());
        assertEquals("400", parsed.getErrors().get(0).getStatus());
        assertEquals("BAD_REQUEST", parsed.getErrors().get(0).getCode());
        assertEquals("422", parsed.getErrors().get(1).getStatus());
        assertEquals("VALIDATION_ERROR", parsed.getErrors().get(1).getCode());
    }

    @Test
    void shouldRoundTripComplexDocument() throws Exception {
        // Create a complex document similar to JSON:API spec example
        JsonApiDocument original = new JsonApiDocument();

        // Primary data - a post with relationships
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
        original.addData(post);

        // Included resources
        ResourceObject author = new ResourceObject("users", "9");
        author.addAttribute("firstName", "Dan");
        author.addAttribute("lastName", "Gebhardt");
        original.addIncluded(author);

        ResourceObject comment1 = new ResourceObject("comments", "5");
        comment1.addAttribute("body", "First!");
        original.addIncluded(comment1);

        ResourceObject comment2 = new ResourceObject("comments", "12");
        comment2.addAttribute("body", "I like XML better");
        original.addIncluded(comment2);

        // Meta
        original.addMeta("copyright", "Copyright 2024");

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Parse back
        JsonApiDocument parsed = parser.parse(json);

        // Verify structure
        assertEquals(1, parsed.getData().size());
        assertEquals(3, parsed.getIncluded().size());

        // Verify primary data
        ResourceObject parsedPost = parsed.getData().get(0);
        assertEquals("posts", parsedPost.getType());
        assertEquals("1", parsedPost.getId());
        assertEquals("JSON:API paints my bikeshed!", parsedPost.getAttributes().get("title"));

        // Verify relationships
        assertNotNull(parsedPost.getRelationships().get("author"));
        assertNotNull(parsedPost.getRelationships().get("comments"));

        // Verify included resources
        assertEquals("users", parsed.getIncluded().get(0).getType());
        assertEquals("comments", parsed.getIncluded().get(1).getType());
        assertEquals("comments", parsed.getIncluded().get(2).getType());

        // Verify meta
        assertEquals("Copyright 2024", parsed.getMeta().get("copyright"));
    }
}
