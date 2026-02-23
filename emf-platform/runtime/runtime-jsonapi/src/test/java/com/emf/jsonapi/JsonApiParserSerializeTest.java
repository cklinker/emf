package com.emf.jsonapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JsonApiParser#serialize(JsonApiDocument)}.
 *
 * Verifies correct JSON:API serialization including the single-resource vs
 * collection distinction, included resources, meta, and errors.
 */
class JsonApiParserSerializeTest {

    private JsonApiParser parser;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parser = new JsonApiParser(objectMapper);
    }

    // ── null / error cases ──────────────────────────────────────────────

    @Test
    void shouldThrowExceptionForNullDocument() {
        assertThrows(JsonApiParser.JsonApiParseException.class, () -> parser.serialize(null));
    }

    // ── single resource ─────────────────────────────────────────────────

    @Test
    void shouldSerializeSingleResourceAsObject() throws Exception {
        ResourceObject resource = new ResourceObject("users", "1");
        resource.addAttribute("name", "Alice");

        JsonApiDocument doc = new JsonApiDocument(List.of(resource));
        doc.setSingleResource(true);

        String json = parser.serialize(doc);

        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("data"));
        assertFalse(root.get("data").isArray(), "Single resource data must be an object, not array");
        assertEquals("users", root.get("data").get("type").asText());
        assertEquals("1", root.get("data").get("id").asText());
        assertEquals("Alice", root.get("data").get("attributes").get("name").asText());
    }

    @Test
    void shouldSerializeSingleResourceWithEmptyDataAsNull() throws Exception {
        JsonApiDocument doc = new JsonApiDocument(new ArrayList<>());
        doc.setSingleResource(true);

        String json = parser.serialize(doc);

        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("data"));
        assertTrue(root.get("data").isNull(), "Single resource with empty list should serialize data as null");
    }

    // ── collection ──────────────────────────────────────────────────────

    @Test
    void shouldSerializeCollectionAsArray() throws Exception {
        ResourceObject r1 = new ResourceObject("users", "1");
        r1.addAttribute("name", "Alice");
        ResourceObject r2 = new ResourceObject("users", "2");
        r2.addAttribute("name", "Bob");

        JsonApiDocument doc = new JsonApiDocument(List.of(r1, r2));
        doc.setSingleResource(false);

        String json = parser.serialize(doc);

        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("data"));
        assertTrue(root.get("data").isArray(), "Collection data must be an array");
        assertEquals(2, root.get("data").size());
        assertEquals("Alice", root.get("data").get(0).get("attributes").get("name").asText());
        assertEquals("Bob", root.get("data").get(1).get("attributes").get("name").asText());
    }

    @Test
    void shouldSerializeEmptyCollectionAsEmptyArray() throws Exception {
        JsonApiDocument doc = new JsonApiDocument(new ArrayList<>());
        doc.setSingleResource(false);

        String json = parser.serialize(doc);

        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("data"));
        assertTrue(root.get("data").isArray());
        assertEquals(0, root.get("data").size());
    }

    // ── included resources ──────────────────────────────────────────────

    @Test
    void shouldSerializeIncludedResources() throws Exception {
        ResourceObject primary = new ResourceObject("articles", "1");
        primary.addAttribute("title", "Hello");

        ResourceObject included = new ResourceObject("people", "9");
        included.addAttribute("name", "Dan");

        JsonApiDocument doc = new JsonApiDocument(List.of(primary));
        doc.setSingleResource(true);
        doc.setIncluded(List.of(included));

        String json = parser.serialize(doc);

        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("included"));
        assertTrue(root.get("included").isArray());
        assertEquals(1, root.get("included").size());
        assertEquals("people", root.get("included").get(0).get("type").asText());
        assertEquals("Dan", root.get("included").get(0).get("attributes").get("name").asText());
    }

    @Test
    void shouldOmitIncludedWhenEmpty() throws Exception {
        ResourceObject primary = new ResourceObject("articles", "1");
        JsonApiDocument doc = new JsonApiDocument(List.of(primary));
        doc.setSingleResource(true);
        // No included set

        String json = parser.serialize(doc);

        JsonNode root = objectMapper.readTree(json);
        assertFalse(root.has("included"), "Should not include empty included array");
    }

    // ── meta ────────────────────────────────────────────────────────────

    @Test
    void shouldSerializeMeta() throws Exception {
        JsonApiDocument doc = new JsonApiDocument(List.of(new ResourceObject("users", "1")));
        doc.setSingleResource(true);
        doc.addMeta("totalRecords", 42);
        doc.addMeta("page", 1);

        String json = parser.serialize(doc);

        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("meta"));
        assertEquals(42, root.get("meta").get("totalRecords").asInt());
        assertEquals(1, root.get("meta").get("page").asInt());
    }

    @Test
    void shouldOmitMetaWhenNull() throws Exception {
        JsonApiDocument doc = new JsonApiDocument(List.of(new ResourceObject("users", "1")));
        doc.setSingleResource(true);

        String json = parser.serialize(doc);

        JsonNode root = objectMapper.readTree(json);
        assertFalse(root.has("meta"));
    }

    // ── errors ──────────────────────────────────────────────────────────

    @Test
    void shouldSerializeErrors() throws Exception {
        JsonApiDocument doc = new JsonApiDocument();
        doc.addError(new JsonApiError("422", "VALIDATION", "Validation Error", "Name is required"));

        String json = parser.serialize(doc);

        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("errors"));
        assertEquals(1, root.get("errors").size());
        assertEquals("422", root.get("errors").get(0).get("status").asText());
        assertEquals("VALIDATION", root.get("errors").get(0).get("code").asText());
        assertEquals("Validation Error", root.get("errors").get(0).get("title").asText());
        assertEquals("Name is required", root.get("errors").get(0).get("detail").asText());
    }

    @Test
    void shouldOmitErrorsWhenNone() throws Exception {
        JsonApiDocument doc = new JsonApiDocument(List.of(new ResourceObject("users", "1")));
        doc.setSingleResource(true);

        String json = parser.serialize(doc);

        JsonNode root = objectMapper.readTree(json);
        assertFalse(root.has("errors"));
    }

    // ── relationships ───────────────────────────────────────────────────

    @Test
    void shouldSerializeResourceWithRelationships() throws Exception {
        ResourceObject article = new ResourceObject("articles", "1");
        article.addAttribute("title", "JSON:API");

        ResourceIdentifier authorId = new ResourceIdentifier("people", "9");
        Relationship authorRel = new Relationship(authorId);
        article.addRelationship("author", authorRel);

        JsonApiDocument doc = new JsonApiDocument(List.of(article));
        doc.setSingleResource(true);

        String json = parser.serialize(doc);

        JsonNode root = objectMapper.readTree(json);
        JsonNode relationships = root.get("data").get("relationships");
        assertNotNull(relationships);
        assertTrue(relationships.has("author"));
        assertEquals("people", relationships.get("author").get("data").get("type").asText());
        assertEquals("9", relationships.get("author").get("data").get("id").asText());
    }

    // ── round-trip ──────────────────────────────────────────────────────

    @Test
    void shouldRoundTripSingleResource() {
        ResourceObject resource = new ResourceObject("users", "42");
        resource.addAttribute("name", "Test User");
        resource.addAttribute("email", "test@example.com");

        JsonApiDocument original = new JsonApiDocument(List.of(resource));
        original.setSingleResource(true);
        original.addMeta("version", 1);

        String json = parser.serialize(original);
        JsonApiDocument parsed = parser.parse(json);

        assertTrue(parsed.isSingleResource());
        assertEquals(1, parsed.getData().size());
        assertEquals("users", parsed.getData().get(0).getType());
        assertEquals("42", parsed.getData().get(0).getId());
        assertEquals("Test User", parsed.getData().get(0).getAttributes().get("name"));
        assertEquals("test@example.com", parsed.getData().get(0).getAttributes().get("email"));
        assertEquals(1, parsed.getMeta().get("version"));
    }

    @Test
    void shouldRoundTripCollection() {
        ResourceObject r1 = new ResourceObject("products", "1");
        r1.addAttribute("name", "Widget");
        ResourceObject r2 = new ResourceObject("products", "2");
        r2.addAttribute("name", "Gadget");

        JsonApiDocument original = new JsonApiDocument(List.of(r1, r2));
        original.setSingleResource(false);

        String json = parser.serialize(original);
        JsonApiDocument parsed = parser.parse(json);

        assertFalse(parsed.isSingleResource());
        assertEquals(2, parsed.getData().size());
    }

    @Test
    void shouldRoundTripWithIncludedResources() {
        ResourceObject article = new ResourceObject("articles", "1");
        article.addAttribute("title", "Hello World");

        ResourceIdentifier authorId = new ResourceIdentifier("people", "9");
        article.addRelationship("author", new Relationship(authorId));

        ResourceObject author = new ResourceObject("people", "9");
        author.addAttribute("name", "Dan");

        JsonApiDocument original = new JsonApiDocument(List.of(article));
        original.setSingleResource(true);
        original.setIncluded(List.of(author));

        String json = parser.serialize(original);
        JsonApiDocument parsed = parser.parse(json);

        assertTrue(parsed.isSingleResource());
        assertTrue(parsed.hasIncluded());
        assertEquals(1, parsed.getIncluded().size());
        assertEquals("people", parsed.getIncluded().get(0).getType());
        assertEquals("Dan", parsed.getIncluded().get(0).getAttributes().get("name"));
    }

    // ── complete document ───────────────────────────────────────────────

    @Test
    void shouldSerializeCompleteDocument() throws Exception {
        // Primary data
        ResourceObject article = new ResourceObject("articles", "1");
        article.addAttribute("title", "JSON:API primer");

        // Relationship
        ResourceIdentifier authorId = new ResourceIdentifier("people", "9");
        article.addRelationship("author", new Relationship(authorId));

        // Included
        ResourceObject author = new ResourceObject("people", "9");
        author.addAttribute("firstName", "Dan");

        // Document
        JsonApiDocument doc = new JsonApiDocument(List.of(article));
        doc.setSingleResource(true);
        doc.setIncluded(List.of(author));
        doc.addMeta("copyright", "2026 EMF");

        String json = parser.serialize(doc);
        JsonNode root = objectMapper.readTree(json);

        // Verify structure
        assertTrue(root.has("data"));
        assertFalse(root.get("data").isArray());
        assertTrue(root.has("included"));
        assertTrue(root.has("meta"));
        assertFalse(root.has("errors"));

        // Verify content
        assertEquals("articles", root.get("data").get("type").asText());
        assertEquals("JSON:API primer", root.get("data").get("attributes").get("title").asText());
        assertEquals("people", root.get("included").get(0).get("type").asText());
        assertEquals("2026 EMF", root.get("meta").get("copyright").asText());
    }

    @Test
    void shouldNotIncludeDataFieldWhenNull() throws Exception {
        JsonApiDocument doc = new JsonApiDocument();
        doc.addError(new JsonApiError("500", "INTERNAL", "Server Error", "Something went wrong"));

        String json = parser.serialize(doc);
        JsonNode root = objectMapper.readTree(json);

        assertFalse(root.has("data"), "Data field should not be present when null");
        assertTrue(root.has("errors"));
    }
}
