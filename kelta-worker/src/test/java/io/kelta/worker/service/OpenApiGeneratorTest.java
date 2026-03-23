package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenApiGenerator Tests")
class OpenApiGeneratorTest {

    private OpenApiGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new OpenApiGenerator();
    }

    private CollectionDefinition createCollection(String name, String displayName, boolean readOnly, boolean system) {
        return CollectionDefinition.builder()
                .name(name)
                .displayName(displayName)
                .description("Test collection: " + name)
                .addField(FieldDefinition.requiredString("name"))
                .addField(new FieldDefinition("email", FieldType.STRING, true, false, false, null, null, null, null, null))
                .addField(new FieldDefinition("age", FieldType.INTEGER, true, false, false, null, null, null, null, null))
                .addField(new FieldDefinition("active", FieldType.BOOLEAN, false, false, false, null, null, null, null, null))
                .addField(new FieldDefinition("birthDate", FieldType.DATE, true, false, false, null, null, null, null, null))
                .readOnly(readOnly)
                .systemCollection(system)
                .build();
    }

    @Test
    @DisplayName("Should generate valid OpenAPI 3.0 structure")
    @SuppressWarnings("unchecked")
    void shouldGenerateValidStructure() {
        var collections = List.of(createCollection("contacts", "Contacts", false, false));
        Map<String, Object> spec = generator.generate(collections, "https://api.kelta.io");

        assertThat(spec.get("openapi")).isEqualTo("3.0.3");
        assertThat(spec.get("info")).isNotNull();
        assertThat(spec.get("paths")).isNotNull();
        assertThat(spec.get("components")).isNotNull();
        assertThat(spec.get("security")).isNotNull();

        // Server URL
        List<Map<String, Object>> servers = (List<Map<String, Object>>) spec.get("servers");
        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).get("url")).isEqualTo("https://api.kelta.io");
    }

    @Test
    @DisplayName("Should include CRUD paths for each collection")
    @SuppressWarnings("unchecked")
    void shouldIncludeCrudPaths() {
        var collections = List.of(createCollection("contacts", "Contacts", false, false));
        Map<String, Object> spec = generator.generate(collections, null);

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        assertThat(paths).containsKey("/api/contacts");
        assertThat(paths).containsKey("/api/contacts/{id}");

        Map<String, Object> listPath = (Map<String, Object>) paths.get("/api/contacts");
        assertThat(listPath).containsKey("get");
        assertThat(listPath).containsKey("post");

        Map<String, Object> itemPath = (Map<String, Object>) paths.get("/api/contacts/{id}");
        assertThat(itemPath).containsKeys("get", "put", "patch", "delete");
    }

    @Test
    @DisplayName("Should only include GET for read-only collections")
    @SuppressWarnings("unchecked")
    void shouldOnlyGetForReadOnly() {
        var collections = List.of(createCollection("audit-logs", "Audit Logs", true, false));
        Map<String, Object> spec = generator.generate(collections, null);

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> listPath = (Map<String, Object>) paths.get("/api/audit-logs");
        assertThat(listPath).containsKey("get");
        assertThat(listPath).doesNotContainKey("post");

        Map<String, Object> itemPath = (Map<String, Object>) paths.get("/api/audit-logs/{id}");
        assertThat(itemPath).containsKey("get");
        assertThat(itemPath).doesNotContainKeys("put", "patch", "delete");
    }

    @Test
    @DisplayName("Should exclude system collections")
    @SuppressWarnings("unchecked")
    void shouldExcludeSystemCollections() {
        var collections = List.of(
                createCollection("contacts", "Contacts", false, false),
                createCollection("internal-config", "Config", false, true)
        );
        Map<String, Object> spec = generator.generate(collections, null);

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        assertThat(paths).containsKey("/api/contacts");
        assertThat(paths).doesNotContainKey("/api/internal-config");
    }

    @Test
    @DisplayName("Should include security scheme")
    @SuppressWarnings("unchecked")
    void shouldIncludeSecurityScheme() {
        var collections = List.of(createCollection("contacts", "Contacts", false, false));
        Map<String, Object> spec = generator.generate(collections, null);

        Map<String, Object> components = (Map<String, Object>) spec.get("components");
        Map<String, Object> securitySchemes = (Map<String, Object>) components.get("securitySchemes");
        assertThat(securitySchemes).containsKey("bearerAuth");
    }

    @Test
    @DisplayName("Should include Atomic Operations endpoint")
    @SuppressWarnings("unchecked")
    void shouldIncludeAtomicOperations() {
        var collections = List.of(createCollection("contacts", "Contacts", false, false));
        Map<String, Object> spec = generator.generate(collections, null);

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        assertThat(paths).containsKey("/api/operations");
    }

    @Test
    @DisplayName("Should generate schemas for collection fields")
    @SuppressWarnings("unchecked")
    void shouldGenerateFieldSchemas() {
        var collections = List.of(createCollection("contacts", "Contacts", false, false));
        Map<String, Object> spec = generator.generate(collections, null);

        Map<String, Object> components = (Map<String, Object>) spec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        assertThat(schemas).containsKey("contactsRequest");
    }
}
