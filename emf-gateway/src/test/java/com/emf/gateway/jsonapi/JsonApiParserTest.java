package com.emf.gateway.jsonapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonApiParser.
 * Tests parsing of various JSON:API response formats including single resources,
 * collections, included resources, errors, and edge cases.
 */
class JsonApiParserTest {

    private JsonApiParser parser;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parser = new JsonApiParser(objectMapper);
    }

    @Test
    void shouldParseSingleResourceResponse() {
        String json = """
            {
              "data": {
                "type": "users",
                "id": "123",
                "attributes": {
                  "name": "John Doe",
                  "email": "john@example.com"
                }
              }
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        assertTrue(document.hasData());
        assertEquals(1, document.getData().size());
        
        ResourceObject user = document.getData().get(0);
        assertEquals("users", user.getType());
        assertEquals("123", user.getId());
        assertEquals("John Doe", user.getAttributes().get("name"));
        assertEquals("john@example.com", user.getAttributes().get("email"));
    }

    @Test
    void shouldParseCollectionResponse() {
        String json = """
            {
              "data": [
                {
                  "type": "users",
                  "id": "1",
                  "attributes": {
                    "name": "Alice"
                  }
                },
                {
                  "type": "users",
                  "id": "2",
                  "attributes": {
                    "name": "Bob"
                  }
                }
              ]
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        assertTrue(document.hasData());
        assertEquals(2, document.getData().size());
        
        assertEquals("users", document.getData().get(0).getType());
        assertEquals("1", document.getData().get(0).getId());
        assertEquals("Alice", document.getData().get(0).getAttributes().get("name"));
        
        assertEquals("users", document.getData().get(1).getType());
        assertEquals("2", document.getData().get(1).getId());
        assertEquals("Bob", document.getData().get(1).getAttributes().get("name"));
    }

    @Test
    void shouldParseResponseWithSingleRelationship() {
        String json = """
            {
              "data": {
                "type": "posts",
                "id": "1",
                "attributes": {
                  "title": "My Post"
                },
                "relationships": {
                  "author": {
                    "data": {
                      "type": "users",
                      "id": "123"
                    }
                  }
                }
              }
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        ResourceObject post = document.getData().get(0);
        
        assertNotNull(post.getRelationships());
        assertTrue(post.getRelationships().containsKey("author"));
        
        Relationship authorRel = post.getRelationships().get("author");
        assertTrue(authorRel.isSingleResource());
        
        ResourceIdentifier author = authorRel.getDataAsSingle();
        assertEquals("users", author.getType());
        assertEquals("123", author.getId());
    }

    @Test
    void shouldParseResponseWithMultipleRelationships() {
        String json = """
            {
              "data": {
                "type": "posts",
                "id": "1",
                "attributes": {
                  "title": "My Post"
                },
                "relationships": {
                  "comments": {
                    "data": [
                      {
                        "type": "comments",
                        "id": "5"
                      },
                      {
                        "type": "comments",
                        "id": "12"
                      }
                    ]
                  }
                }
              }
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        ResourceObject post = document.getData().get(0);
        
        Relationship commentsRel = post.getRelationships().get("comments");
        assertTrue(commentsRel.isResourceCollection());
        
        List<ResourceIdentifier> comments = commentsRel.getDataAsCollection();
        assertEquals(2, comments.size());
        assertEquals("comments", comments.get(0).getType());
        assertEquals("5", comments.get(0).getId());
        assertEquals("comments", comments.get(1).getType());
        assertEquals("12", comments.get(1).getId());
    }

    @Test
    void shouldParseResponseWithIncludedResources() {
        String json = """
            {
              "data": {
                "type": "posts",
                "id": "1",
                "attributes": {
                  "title": "My Post"
                },
                "relationships": {
                  "author": {
                    "data": {
                      "type": "users",
                      "id": "9"
                    }
                  }
                }
              },
              "included": [
                {
                  "type": "users",
                  "id": "9",
                  "attributes": {
                    "firstName": "Dan",
                    "lastName": "Gebhardt"
                  }
                }
              ]
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        assertTrue(document.hasData());
        assertTrue(document.hasIncluded());
        
        assertEquals(1, document.getIncluded().size());
        ResourceObject author = document.getIncluded().get(0);
        assertEquals("users", author.getType());
        assertEquals("9", author.getId());
        assertEquals("Dan", author.getAttributes().get("firstName"));
        assertEquals("Gebhardt", author.getAttributes().get("lastName"));
    }

    @Test
    void shouldParseResponseWithMeta() {
        String json = """
            {
              "data": [],
              "meta": {
                "total": 100,
                "page": 1,
                "perPage": 10
              }
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        assertNotNull(document.getMeta());
        assertEquals(100, document.getMeta().get("total"));
        assertEquals(1, document.getMeta().get("page"));
        assertEquals(10, document.getMeta().get("perPage"));
    }

    @Test
    void shouldParseErrorResponse() {
        String json = """
            {
              "errors": [
                {
                  "status": "400",
                  "code": "BAD_REQUEST",
                  "title": "Bad Request",
                  "detail": "Invalid input provided"
                }
              ]
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        assertTrue(document.hasErrors());
        assertEquals(1, document.getErrors().size());
        
        JsonApiError error = document.getErrors().get(0);
        assertEquals("400", error.getStatus());
        assertEquals("BAD_REQUEST", error.getCode());
        assertEquals("Bad Request", error.getTitle());
        assertEquals("Invalid input provided", error.getDetail());
    }

    @Test
    void shouldParseMultipleErrors() {
        String json = """
            {
              "errors": [
                {
                  "status": "422",
                  "code": "VALIDATION_ERROR",
                  "title": "Validation Failed",
                  "detail": "Name is required",
                  "source": {
                    "pointer": "/data/attributes/name"
                  }
                },
                {
                  "status": "422",
                  "code": "VALIDATION_ERROR",
                  "title": "Validation Failed",
                  "detail": "Email is invalid",
                  "source": {
                    "pointer": "/data/attributes/email"
                  }
                }
              ]
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        assertTrue(document.hasErrors());
        assertEquals(2, document.getErrors().size());
        
        JsonApiError error1 = document.getErrors().get(0);
        assertEquals("Name is required", error1.getDetail());
        assertNotNull(error1.getSource());
        assertEquals("/data/attributes/name", error1.getSource().get("pointer"));
        
        JsonApiError error2 = document.getErrors().get(1);
        assertEquals("Email is invalid", error2.getDetail());
        assertEquals("/data/attributes/email", error2.getSource().get("pointer"));
    }

    @Test
    void shouldParseNullData() {
        String json = """
            {
              "data": null
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        assertNotNull(document.getData());
        assertTrue(document.getData().isEmpty());
    }

    @Test
    void shouldParseEmptyArray() {
        String json = """
            {
              "data": []
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        assertNotNull(document.getData());
        assertTrue(document.getData().isEmpty());
    }

    @Test
    void shouldParseResourceWithoutAttributes() {
        String json = """
            {
              "data": {
                "type": "users",
                "id": "123"
              }
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        ResourceObject user = document.getData().get(0);
        assertEquals("users", user.getType());
        assertEquals("123", user.getId());
        assertNotNull(user.getAttributes());
        assertTrue(user.getAttributes().isEmpty());
    }

    @Test
    void shouldParseResourceWithoutRelationships() {
        String json = """
            {
              "data": {
                "type": "users",
                "id": "123",
                "attributes": {
                  "name": "John"
                }
              }
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        ResourceObject user = document.getData().get(0);
        assertNotNull(user.getRelationships());
        assertTrue(user.getRelationships().isEmpty());
    }

    @Test
    void shouldParseRelationshipWithLinks() {
        String json = """
            {
              "data": {
                "type": "posts",
                "id": "1",
                "relationships": {
                  "author": {
                    "data": {
                      "type": "users",
                      "id": "123"
                    },
                    "links": {
                      "self": "/posts/1/relationships/author",
                      "related": "/posts/1/author"
                    }
                  }
                }
              }
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        ResourceObject post = document.getData().get(0);
        Relationship authorRel = post.getRelationships().get("author");
        
        assertNotNull(authorRel.getLinks());
        assertEquals("/posts/1/relationships/author", authorRel.getLinks().get("self"));
        assertEquals("/posts/1/author", authorRel.getLinks().get("related"));
    }

    @Test
    void shouldParseRelationshipWithNullData() {
        String json = """
            {
              "data": {
                "type": "posts",
                "id": "1",
                "relationships": {
                  "author": {
                    "data": null
                  }
                }
              }
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        ResourceObject post = document.getData().get(0);
        Relationship authorRel = post.getRelationships().get("author");
        
        assertNotNull(authorRel);
        assertNull(authorRel.getData());
    }

    @Test
    void shouldParseComplexNestedAttributes() {
        String json = """
            {
              "data": {
                "type": "users",
                "id": "123",
                "attributes": {
                  "name": "John Doe",
                  "age": 30,
                  "active": true,
                  "address": {
                    "street": "123 Main St",
                    "city": "Springfield",
                    "zip": "12345"
                  },
                  "tags": ["developer", "java", "spring"]
                }
              }
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        ResourceObject user = document.getData().get(0);
        
        assertEquals("John Doe", user.getAttributes().get("name"));
        assertEquals(30, user.getAttributes().get("age"));
        assertEquals(true, user.getAttributes().get("active"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) user.getAttributes().get("address");
        assertEquals("123 Main St", address.get("street"));
        assertEquals("Springfield", address.get("city"));
        
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) user.getAttributes().get("tags");
        assertEquals(3, tags.size());
        assertTrue(tags.contains("developer"));
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        assertThrows(JsonApiParser.JsonApiParseException.class, () -> {
            parser.parse(null);
        });
    }

    @Test
    void shouldThrowExceptionForEmptyInput() {
        assertThrows(JsonApiParser.JsonApiParseException.class, () -> {
            parser.parse("");
        });
    }

    @Test
    void shouldThrowExceptionForInvalidJson() {
        String invalidJson = "{ this is not valid json }";
        
        assertThrows(JsonApiParser.JsonApiParseException.class, () -> {
            parser.parse(invalidJson);
        });
    }

    @Test
    void shouldThrowExceptionForMalformedJson() {
        String malformedJson = "{ \"data\": { \"type\": \"users\" ";
        
        assertThrows(JsonApiParser.JsonApiParseException.class, () -> {
            parser.parse(malformedJson);
        });
    }

    @Test
    void shouldParseErrorWithAllFields() {
        String json = """
            {
              "errors": [
                {
                  "id": "error-123",
                  "status": "422",
                  "code": "VALIDATION_ERROR",
                  "title": "Validation Failed",
                  "detail": "Name is required",
                  "source": {
                    "pointer": "/data/attributes/name",
                    "parameter": "name"
                  },
                  "meta": {
                    "timestamp": "2024-01-15T10:30:00Z"
                  }
                }
              ]
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        assertTrue(document.hasErrors());
        
        JsonApiError error = document.getErrors().get(0);
        assertEquals("error-123", error.getId());
        assertEquals("422", error.getStatus());
        assertEquals("VALIDATION_ERROR", error.getCode());
        assertEquals("Validation Failed", error.getTitle());
        assertEquals("Name is required", error.getDetail());
        assertNotNull(error.getSource());
        assertEquals("/data/attributes/name", error.getSource().get("pointer"));
        assertNotNull(error.getMeta());
        assertEquals("2024-01-15T10:30:00Z", error.getMeta().get("timestamp"));
    }

    @Test
    void shouldParseRealWorldExample() {
        // Example from JSON:API specification
        String json = """
            {
              "data": {
                "type": "articles",
                "id": "1",
                "attributes": {
                  "title": "JSON:API paints my bikeshed!",
                  "body": "The shortest article. Ever.",
                  "created": "2015-05-22T14:56:29.000Z",
                  "updated": "2015-05-22T14:56:28.000Z"
                },
                "relationships": {
                  "author": {
                    "data": {"id": "42", "type": "people"}
                  }
                }
              },
              "included": [
                {
                  "type": "people",
                  "id": "42",
                  "attributes": {
                    "name": "John",
                    "age": 80,
                    "gender": "male"
                  }
                }
              ]
            }
            """;

        JsonApiDocument document = parser.parse(json);

        assertNotNull(document);
        assertTrue(document.hasData());
        assertTrue(document.hasIncluded());
        
        ResourceObject article = document.getData().get(0);
        assertEquals("articles", article.getType());
        assertEquals("1", article.getId());
        assertEquals("JSON:API paints my bikeshed!", article.getAttributes().get("title"));
        
        Relationship authorRel = article.getRelationships().get("author");
        ResourceIdentifier authorId = authorRel.getDataAsSingle();
        assertEquals("people", authorId.getType());
        assertEquals("42", authorId.getId());
        
        ResourceObject author = document.getIncluded().get(0);
        assertEquals("people", author.getType());
        assertEquals("42", author.getId());
        assertEquals("John", author.getAttributes().get("name"));
        assertEquals(80, author.getAttributes().get("age"));
    }
}
