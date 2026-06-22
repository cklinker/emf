package io.kelta.worker.service.api;

import io.kelta.runtime.model.FieldType;
import io.kelta.worker.service.api.OpenApiCollectionMapper.DerivedField;
import io.kelta.worker.service.api.OpenApiCollectionMapper.DerivedSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OpenApiCollectionMapper")
class OpenApiCollectionMapperTest {

    private final OpenApiCollectionMapper mapper = new OpenApiCollectionMapper();
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode parse(String s) {
        return json.readTree(s);
    }

    private Map<String, FieldType> typesByName(DerivedSchema schema) {
        return schema.fields().stream()
                .collect(Collectors.toMap(DerivedField::name, DerivedField::type));
    }

    @Test
    @DisplayName("derives fields from a bare array response and maps OpenAPI types")
    void bareArrayResponse() {
        JsonNode responses = parse("""
            {
              "200": {
                "content": {
                  "application/json": {
                    "schema": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "id": {"type": "string"},
                          "count": {"type": "integer"},
                          "big": {"type": "integer", "format": "int64"},
                          "price": {"type": "number"},
                          "active": {"type": "boolean"},
                          "createdAt": {"type": "string", "format": "date-time"},
                          "birthday": {"type": "string", "format": "date"},
                          "email": {"type": "string", "format": "email"},
                          "homepage": {"type": "string", "format": "uri"},
                          "tags": {"type": "array", "items": {"type": "string"}},
                          "meta": {"type": "object"}
                        }
                      }
                    }
                  }
                }
              }
            }
            """);

        DerivedSchema schema = mapper.map(responses);

        assertThat(schema.dataPath()).isEmpty();
        assertThat(schema.idAttribute()).isEqualTo("id");
        Map<String, FieldType> types = typesByName(schema);
        assertThat(types).containsEntry("id", FieldType.STRING)
                .containsEntry("count", FieldType.INTEGER)
                .containsEntry("big", FieldType.LONG)
                .containsEntry("price", FieldType.DOUBLE)
                .containsEntry("active", FieldType.BOOLEAN)
                .containsEntry("createdAt", FieldType.DATETIME)
                .containsEntry("birthday", FieldType.DATE)
                .containsEntry("email", FieldType.EMAIL)
                .containsEntry("homepage", FieldType.URL)
                .containsEntry("tags", FieldType.JSON)
                .containsEntry("meta", FieldType.JSON);
    }

    @Test
    @DisplayName("infers dataPath from an array-valued wrapper property")
    void wrapperObjectResponse() {
        JsonNode responses = parse("""
            {
              "200": {
                "content": {
                  "application/json": {
                    "schema": {
                      "type": "object",
                      "properties": {
                        "total": {"type": "integer"},
                        "data": {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "properties": {
                              "sku": {"type": "string"},
                              "qty": {"type": "integer"}
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """);

        DerivedSchema schema = mapper.map(responses);

        assertThat(schema.dataPath()).isEqualTo("data");
        assertThat(schema.idAttribute()).isEqualTo("sku"); // no "id" → first field
        assertThat(typesByName(schema)).containsOnlyKeys("sku", "qty");
    }

    @Test
    @DisplayName("falls back to a 2xx response when 200 is absent")
    void picks2xxWhenNo200() {
        JsonNode responses = parse("""
            {
              "201": {
                "content": {
                  "application/json": {
                    "schema": {
                      "type": "object",
                      "properties": {"id": {"type": "string"}}
                    }
                  }
                }
              }
            }
            """);

        DerivedSchema schema = mapper.map(responses);
        assertThat(schema.idAttribute()).isEqualTo("id");
    }

    @Test
    @DisplayName("rejects a response with no 2xx schema")
    void rejectsNoSuccess() {
        JsonNode responses = parse("""
            {"404": {"description": "not found"}}
            """);
        assertThatThrownBy(() -> mapper.map(responses))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no 2xx");
    }

    @Test
    @DisplayName("rejects a schema with no derivable properties")
    void rejectsNoProperties() {
        JsonNode responses = parse("""
            {
              "200": {
                "content": {"application/json": {"schema": {"type": "object"}}}
              }
            }
            """);
        assertThatThrownBy(() -> mapper.map(responses))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
