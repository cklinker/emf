package io.kelta.runtime.storage;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinitionBuilder;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.model.StorageConfig;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.FilterOperator;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.query.SortDirection;
import io.kelta.runtime.query.SortField;
import io.kelta.runtime.storage.RestExecutor.RestRequest;
import io.kelta.runtime.storage.RestExecutor.RestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExternalRestStorageAdapter")
class ExternalRestStorageAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Records each request and replies via a programmable handler. */
    private static final class RecordingExecutor implements RestExecutor {
        RestRequest last;
        Function<RestRequest, RestResponse> handler;

        RecordingExecutor(Function<RestRequest, RestResponse> handler) {
            this.handler = handler;
        }

        @Override
        public RestResponse exchange(RestRequest request) {
            this.last = request;
            return handler.apply(request);
        }
    }

    private ExternalRestStorageAdapter adapterReturning(Function<RestRequest, RestResponse> handler) {
        return new ExternalRestStorageAdapter(new RecordingExecutor(handler), objectMapper);
    }

    private static CollectionDefinition orders(Map<String, String> extraConfig) {
        Map<String, String> config = new java.util.HashMap<>();
        config.put("adapterType", "external-rest");
        config.put("baseUrl", "https://api.test");
        config.putAll(extraConfig);
        return new CollectionDefinitionBuilder()
                .name("orders")
                .addField(new FieldDefinitionBuilder().name("name").type(FieldType.STRING).nullable(true).build())
                .storageConfig(new StorageConfig("orders", config))
                .build();
    }

    @Test
    @DisplayName("reports its storage type")
    void storageType() {
        assertThat(adapterReturning(r -> new RestResponse(200, "[]")).storageType()).isEqualTo("external-rest");
    }

    @Test
    @DisplayName("query pushes down pagination, sort and EQ filters and maps idAttribute → id")
    void queryMapsAndPushesDown() {
        RecordingExecutor executor = new RecordingExecutor(r -> new RestResponse(200,
                "{\"data\":[{\"orderId\":\"o1\",\"name\":\"A\"},{\"orderId\":\"o2\",\"name\":\"B\"}],\"total\":42}"));
        ExternalRestStorageAdapter adapter = new ExternalRestStorageAdapter(executor, objectMapper);
        CollectionDefinition def = orders(Map.of("dataPath", "data", "totalPath", "total", "idAttribute", "orderId"));

        QueryRequest request = new QueryRequest(
                new Pagination(2, 10),
                List.of(new SortField("name", SortDirection.DESC)),
                List.of(),
                List.of(new FilterCondition("status", FilterOperator.EQ, "open")));

        QueryResult result = adapter.query(def, request);

        assertThat(executor.last.method()).isEqualTo("GET");
        assertThat(executor.last.url())
                .isEqualTo("https://api.test/orders?page=2&pageSize=10&sort=-name&status=open");
        assertThat(result.data()).hasSize(2);
        assertThat(result.data().get(0)).containsEntry("id", "o1").containsEntry("name", "A");
        assertThat(result.metadata().totalCount()).isEqualTo(42);
        assertThat(result.metadata().totalPages()).isEqualTo(5);
    }

    @Test
    @DisplayName("query reads a bare JSON array body when no dataPath is configured")
    void queryBareArray() {
        ExternalRestStorageAdapter adapter =
                adapterReturning(r -> new RestResponse(200, "[{\"id\":\"a\"},{\"id\":\"b\"}]"));

        QueryResult result = adapter.query(orders(Map.of()), QueryRequest.defaults());

        assertThat(result.data()).extracting(m -> m.get("id")).containsExactly("a", "b");
        assertThat(result.metadata().totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("query maps a non-2xx response to a StorageException")
    void queryError() {
        ExternalRestStorageAdapter adapter = adapterReturning(r -> new RestResponse(500, "boom"));
        assertThatThrownBy(() -> adapter.query(orders(Map.of()), QueryRequest.defaults()))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("getById returns the record on 200 and empty on 404")
    void getById() {
        ExternalRestStorageAdapter ok =
                adapterReturning(r -> new RestResponse(200, "{\"id\":\"o1\",\"name\":\"A\"}"));
        assertThat(ok.getById(orders(Map.of()), "o1")).get().extracting(m -> m.get("name")).isEqualTo("A");

        ExternalRestStorageAdapter missing = adapterReturning(r -> new RestResponse(404, ""));
        assertThat(missing.getById(orders(Map.of()), "nope")).isEmpty();
    }

    @Test
    @DisplayName("create POSTs the serialized body and returns the created record")
    void create() {
        RecordingExecutor executor =
                new RecordingExecutor(r -> new RestResponse(201, "{\"id\":\"new-1\",\"name\":\"A\"}"));
        ExternalRestStorageAdapter adapter = new ExternalRestStorageAdapter(executor, objectMapper);

        Map<String, Object> created = adapter.create(orders(Map.of()), Map.of("name", "A"));

        assertThat(executor.last.method()).isEqualTo("POST");
        assertThat(executor.last.url()).isEqualTo("https://api.test/orders");
        assertThat(executor.last.body()).contains("\"name\":\"A\"");
        assertThat(executor.last.headers()).containsEntry("Content-Type", "application/json");
        assertThat(created).containsEntry("id", "new-1");
    }

    @Test
    @DisplayName("update returns the record on 200 and empty on 404")
    void update() {
        ExternalRestStorageAdapter ok =
                adapterReturning(r -> new RestResponse(200, "{\"id\":\"o1\",\"name\":\"B\"}"));
        assertThat(ok.update(orders(Map.of()), "o1", Map.of("name", "B")))
                .get().extracting(m -> m.get("name")).isEqualTo("B");

        ExternalRestStorageAdapter missing = adapterReturning(r -> new RestResponse(404, ""));
        assertThat(missing.update(orders(Map.of()), "nope", Map.of("name", "B"))).isEmpty();
    }

    @Test
    @DisplayName("delete returns true on 2xx and false on 404")
    void delete() {
        assertThat(adapterReturning(r -> new RestResponse(204, "")).delete(orders(Map.of()), "o1")).isTrue();
        assertThat(adapterReturning(r -> new RestResponse(404, "")).delete(orders(Map.of()), "nope")).isFalse();
    }

    @Test
    @DisplayName("isUnique is false when another record holds the value, true otherwise")
    void isUnique() {
        ExternalRestStorageAdapter clash =
                adapterReturning(r -> new RestResponse(200, "[{\"id\":\"other\"}]"));
        assertThat(clash.isUnique(orders(Map.of()), "email", "a@b.com", "self")).isFalse();

        ExternalRestStorageAdapter onlySelf =
                adapterReturning(r -> new RestResponse(200, "[{\"id\":\"self\"}]"));
        assertThat(onlySelf.isUnique(orders(Map.of()), "email", "a@b.com", "self")).isTrue();

        ExternalRestStorageAdapter none = adapterReturning(r -> new RestResponse(200, "[]"));
        assertThat(none.isUnique(orders(Map.of()), "email", "a@b.com", "self")).isTrue();
    }

    @Test
    @DisplayName("sends a bearer token when configured")
    void bearerToken() {
        RecordingExecutor executor = new RecordingExecutor(r -> new RestResponse(200, "[]"));
        ExternalRestStorageAdapter adapter = new ExternalRestStorageAdapter(executor, objectMapper);

        adapter.query(orders(Map.of("bearerToken", "secret")), QueryRequest.defaults());

        assertThat(executor.last.headers()).containsEntry("Authorization", "Bearer secret");
    }

    @Test
    @DisplayName("aggregate is unsupported")
    void aggregateUnsupported() {
        ExternalRestStorageAdapter adapter = adapterReturning(r -> new RestResponse(200, "[]"));
        assertThatThrownBy(() -> adapter.aggregate(orders(Map.of()), List.of(), List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a missing baseUrl is a configuration error")
    void missingBaseUrl() {
        CollectionDefinition def = new CollectionDefinitionBuilder()
                .name("orders")
                .addField(new FieldDefinitionBuilder().name("name").type(FieldType.STRING).nullable(true).build())
                .storageConfig(new StorageConfig("orders", Map.of("adapterType", "external-rest")))
                .build();
        ExternalRestStorageAdapter adapter = adapterReturning(r -> new RestResponse(200, "[]"));
        assertThatThrownBy(() -> adapter.query(def, QueryRequest.defaults()))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("schema operations are no-ops")
    void schemaNoOps() {
        ExternalRestStorageAdapter adapter = adapterReturning(r -> {
            throw new AssertionError("schema ops must not hit the network");
        });
        adapter.initializeCollection(orders(Map.of()));
        adapter.updateCollectionSchema(orders(Map.of()), orders(Map.of()));
        assertThat(Optional.empty()).isEmpty(); // reached without exception
    }
}
