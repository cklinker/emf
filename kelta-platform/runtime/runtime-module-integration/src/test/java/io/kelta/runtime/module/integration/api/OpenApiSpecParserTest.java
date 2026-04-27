package io.kelta.runtime.module.integration.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OpenApiSpecParser")
class OpenApiSpecParserTest {

    private OpenApiSpecParser parser;

    @BeforeEach
    void setUp() {
        parser = new OpenApiSpecParser(new ObjectMapper());
    }

    private static final String PETSTORE_YAML = """
        openapi: 3.0.3
        info:
          title: Swagger Petstore
          version: 1.0.6
          description: A sample API for unit tests
        servers:
          - url: https://petstore.swagger.io/v2
        paths:
          /pet:
            post:
              tags: [pet]
              summary: Add a new pet to the store
              operationId: addPet
              requestBody:
                required: true
                content:
                  application/json:
                    schema:
                      $ref: '#/components/schemas/Pet'
              responses:
                '200':
                  description: ok
            put:
              tags: [pet]
              summary: Update an existing pet
              operationId: updatePet
              responses:
                '200':
                  description: ok
          /pet/{petId}:
            get:
              tags: [pet]
              summary: Find pet by ID
              operationId: getPetById
              parameters:
                - name: petId
                  in: path
                  required: true
                  schema:
                    type: integer
                    format: int64
              responses:
                '200':
                  description: ok
            delete:
              tags: [pet]
              summary: Delete a pet
              # operationId intentionally omitted to test synthetic id
              parameters:
                - name: petId
                  in: path
                  required: true
                  schema:
                    type: integer
              responses:
                '204':
                  description: deleted
        components:
          schemas:
            Pet:
              type: object
              required: [name]
              properties:
                id:
                  type: integer
                name:
                  type: string
                status:
                  type: string
                  enum: [available, pending, sold]
        """;

    @Test
    @DisplayName("Parses a valid OpenAPI 3.0 spec into normalized fields")
    void parsesValidSpec() {
        ParsedSpec parsed = parser.parse(PETSTORE_YAML);

        assertEquals("3.0.3", parsed.specVersion());
        assertEquals("Swagger Petstore", parsed.apiTitle());
        assertEquals("1.0.6", parsed.apiVersion());
        assertEquals("https://petstore.swagger.io/v2", parsed.baseUrl());
        assertEquals(4, parsed.operations().size(),
            "should extract 4 operations across /pet and /pet/{petId}");
    }

    @Test
    @DisplayName("Synthesizes an operationId when the spec omits one")
    void synthesizesMissingOperationId() {
        ParsedSpec parsed = parser.parse(PETSTORE_YAML);
        ParsedSpec.ParsedOperation deletePet = parsed.operations().stream()
            .filter(op -> "DELETE".equals(op.httpMethod())
                && op.pathTemplate().equals("/pet/{petId}"))
            .findFirst()
            .orElseThrow();

        assertNull(deletePet.operationId(), "spec did not provide operationId");
        assertNotNull(deletePet.syntheticOpId());
        assertTrue(deletePet.syntheticOpId().startsWith("DELETE"),
            "synthetic id should start with the HTTP method");
    }

    @Test
    @DisplayName("Resolves $ref pointers so handlers don't need to chase them")
    void resolvesRefs() {
        ParsedSpec parsed = parser.parse(PETSTORE_YAML);
        ParsedSpec.ParsedOperation addPet = parsed.operations().stream()
            .filter(op -> "addPet".equals(op.operationId()))
            .findFirst()
            .orElseThrow();

        // The Pet schema's actual properties should be inlined into the body
        // even if swagger-parser leaves stub $refs for cycle protection.
        assertNotNull(addPet.requestBodySchema(), "request body should be present");
        String json = addPet.requestBodySchema().toString();
        assertTrue(json.contains("photoUrls") || json.contains("status")
                || json.contains("\"name\""),
            "Pet schema properties should be inlined into the operation body");
    }

    @Test
    @DisplayName("Builds a search_text that includes method, path, summary, and tags")
    void buildsSearchText() {
        ParsedSpec parsed = parser.parse(PETSTORE_YAML);
        ParsedSpec.ParsedOperation addPet = parsed.operations().stream()
            .filter(op -> "addPet".equals(op.operationId()))
            .findFirst()
            .orElseThrow();

        String text = addPet.searchText();
        assertTrue(text.contains("POST"));
        assertTrue(text.contains("/pet"));
        assertTrue(text.contains("Add a new pet"));
        assertTrue(text.contains("pet"));   // tag
    }

    @Test
    @DisplayName("hash() returns a stable hex digest")
    void hashIsStable() {
        String first = parser.hash(PETSTORE_YAML);
        String second = parser.hash(PETSTORE_YAML);
        assertEquals(first, second);
        assertEquals(64, first.length(), "sha-256 hex digest is 64 chars");
    }

    @Test
    @DisplayName("Throws OpenApiParseException for an unparseable document")
    void throwsForGarbage() {
        // Empty string and known-bad YAML both surface as parse errors via
        // swagger-parser's null-OpenAPI return; the parser turns that into
        // OpenApiParseException.
        assertThrows(OpenApiParseException.class, () -> parser.parse(""));
    }

    @Test
    @DisplayName("Accepts JSON and YAML interchangeably")
    void acceptsJson() {
        String json = """
            { "openapi": "3.0.3",
              "info": {"title": "t", "version": "1"},
              "paths": {"/x": {"get": {"responses": {"200": {"description": "ok"}}}}} }
            """;
        ParsedSpec parsed = parser.parse(json);
        assertEquals(1, parsed.operations().size());
        assertEquals("GET", parsed.operations().get(0).httpMethod());
    }

    @Test
    @DisplayName("Operations preserve tag info as JSON arrays")
    void preservesTags() {
        ParsedSpec parsed = parser.parse(PETSTORE_YAML);
        List<ParsedSpec.ParsedOperation> petOps = parsed.operations().stream()
            .filter(op -> op.tags() != null && op.tags().toString().contains("pet"))
            .toList();
        assertEquals(4, petOps.size(), "all 4 operations are tagged 'pet'");
    }

    @Test
    @DisplayName("Emits clean OpenAPI-style JSON, not swagger-model field dumps")
    void emitsCleanJson() {
        ParsedSpec parsed = parser.parse(PETSTORE_YAML);
        ParsedSpec.ParsedOperation getPet = parsed.operations().stream()
            .filter(op -> "getPetById".equals(op.operationId()))
            .findFirst()
            .orElseThrow();

        String paramsJson = getPet.parametersSchema().toString();
        // Bare ObjectMapper would dump swagger internals like exampleSetFlag,
        // jsonSchema, jsonSchemaImpl, types, booleanSchemaValue. Json.mapper()
        // omits these — assert they're gone.
        assertFalse(paramsJson.contains("exampleSetFlag"),
            "swagger internal field 'exampleSetFlag' should not leak into output: " + paramsJson);
        assertFalse(paramsJson.contains("jsonSchemaImpl"),
            "swagger internal field 'jsonSchemaImpl' should not leak into output: " + paramsJson);
        assertFalse(paramsJson.contains("_unparsable"),
            "convert() should not fall through to _unparsable on Petstore: " + paramsJson);
        assertTrue(paramsJson.contains("\"name\":\"petId\""),
            "should include the actual parameter name: " + paramsJson);
    }

    @Test
    @DisplayName("convert() does not fall through to _unparsable for any operation field")
    void noUnparsableFallback() {
        ParsedSpec parsed = parser.parse(PETSTORE_YAML);
        for (ParsedSpec.ParsedOperation op : parsed.operations()) {
            for (var node : List.of(
                op.parametersSchema() == null ? "" : op.parametersSchema().toString(),
                op.requestBodySchema() == null ? "" : op.requestBodySchema().toString(),
                op.responseSchemas() == null ? "" : op.responseSchemas().toString())) {
                assertFalse(node.contains("_unparsable"),
                    "Found _unparsable fallback in operation "
                        + op.httpMethod() + " " + op.pathTemplate() + ": " + node);
            }
        }
    }
}
