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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExternalJdbcStorageAdapter (against H2)")
class ExternalJdbcStorageAdapterTest {

    private JdbcTemplate h2;
    private ExternalJdbcStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:rec4c;DATABASE_TO_LOWER=TRUE;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setDriverClassName("org.h2.Driver");
        h2 = new JdbcTemplate(ds);
        h2.execute("DROP TABLE IF EXISTS orders");
        h2.execute("CREATE TABLE orders (id VARCHAR PRIMARY KEY, name VARCHAR, status VARCHAR)");
        h2.update("INSERT INTO orders (id, name, status) VALUES (?,?,?)", "o1", "Alpha", "open");
        h2.update("INSERT INTO orders (id, name, status) VALUES (?,?,?)", "o2", "Beta", "closed");
        h2.update("INSERT INTO orders (id, name, status) VALUES (?,?,?)", "o3", "Gamma", "open");

        // Fake provider: hand back the shared H2 template regardless of config.
        adapter = new ExternalJdbcStorageAdapter(config -> h2);
    }

    private static CollectionDefinition orders(Map<String, String> extra) {
        Map<String, String> config = new java.util.HashMap<>();
        config.put("adapterType", "external-jdbc");
        config.put("jdbcUrl", "jdbc:h2:mem:rec4c");
        config.putAll(extra);
        return new CollectionDefinitionBuilder()
                .name("orders")
                .addField(new FieldDefinitionBuilder().name("name").type(FieldType.STRING).nullable(true).build())
                .storageConfig(new StorageConfig("orders", config))
                .build();
    }

    @Test
    @DisplayName("reports its storage type")
    void storageType() {
        assertThat(adapter.storageType()).isEqualTo("external-jdbc");
    }

    @Test
    @DisplayName("query applies EQ filter + sort and reports the filtered total")
    void queryFilterSort() {
        QueryRequest request = new QueryRequest(
                new Pagination(1, 10),
                List.of(new SortField("name", SortDirection.DESC)),
                List.of(),
                List.of(new FilterCondition("status", FilterOperator.EQ, "open")));

        QueryResult result = adapter.query(orders(Map.of()), request);

        assertThat(result.data()).extracting(m -> m.get("id")).containsExactly("o3", "o1"); // Gamma, Alpha desc
        assertThat(result.data().get(0)).containsEntry("name", "Gamma");
        assertThat(result.metadata().totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("query paginates with LIMIT/OFFSET")
    void queryPaginates() {
        QueryRequest page2 = new QueryRequest(
                new Pagination(2, 1),
                List.of(new SortField("id", SortDirection.ASC)),
                List.of(),
                List.of());

        QueryResult result = adapter.query(orders(Map.of()), page2);

        assertThat(result.data()).extracting(m -> m.get("id")).containsExactly("o2");
        assertThat(result.metadata().totalCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("getById returns the row, mapping idColumn → id; empty when absent")
    void getById() {
        assertThat(adapter.getById(orders(Map.of()), "o2"))
                .get().extracting(m -> m.get("name")).isEqualTo("Beta");
        assertThat(adapter.getById(orders(Map.of()), "nope")).isEmpty();
    }

    @Test
    @DisplayName("create inserts and returns the row")
    void create() {
        Map<String, Object> created =
                adapter.create(orders(Map.of()), Map.of("id", "o4", "name", "Delta", "status", "open"));

        assertThat(created).containsEntry("id", "o4").containsEntry("name", "Delta");
        assertThat(h2.queryForObject("SELECT name FROM orders WHERE id = ?", String.class, "o4")).isEqualTo("Delta");
    }

    @Test
    @DisplayName("update changes columns and returns the row; empty for an unknown id")
    void update() {
        assertThat(adapter.update(orders(Map.of()), "o1", Map.of("name", "Alpha-2")))
                .get().extracting(m -> m.get("name")).isEqualTo("Alpha-2");
        assertThat(adapter.update(orders(Map.of()), "nope", Map.of("name", "x"))).isEmpty();
    }

    @Test
    @DisplayName("delete returns true when a row is removed, false otherwise")
    void delete() {
        assertThat(adapter.delete(orders(Map.of()), "o1")).isTrue();
        assertThat(adapter.delete(orders(Map.of()), "o1")).isFalse();
        assertThat(adapter.getById(orders(Map.of()), "o1")).isEmpty();
    }

    @Test
    @DisplayName("isUnique is false when another row holds the value, true otherwise")
    void isUnique() {
        assertThat(adapter.isUnique(orders(Map.of()), "status", "open", "o3")).isFalse(); // o1 also open
        assertThat(adapter.isUnique(orders(Map.of()), "status", "closed", "o2")).isTrue(); // only o2
        assertThat(adapter.isUnique(orders(Map.of()), "name", "Nonexistent", null)).isTrue();
    }

    @Test
    @DisplayName("aggregate is unsupported")
    void aggregateUnsupported() {
        assertThatThrownBy(() -> adapter.aggregate(orders(Map.of()), List.of(), List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a missing jdbcUrl is a configuration error")
    void missingJdbcUrl() {
        CollectionDefinition def = new CollectionDefinitionBuilder()
                .name("orders")
                .addField(new FieldDefinitionBuilder().name("name").type(FieldType.STRING).nullable(true).build())
                .storageConfig(new StorageConfig("orders", Map.of("adapterType", "external-jdbc")))
                .build();
        assertThatThrownBy(() -> adapter.query(def, QueryRequest.defaults()))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("an unsafe table identifier is rejected before any SQL runs")
    void rejectsUnsafeIdentifier() {
        CollectionDefinition def = orders(Map.of("table", "orders; DROP TABLE orders"));
        assertThatThrownBy(() -> adapter.query(def, QueryRequest.defaults()))
                .isInstanceOf(StorageException.class);
        // table still intact
        assertThat(h2.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isEqualTo(3L);
    }

    @Test
    @DisplayName("resolves a basic_auth credentialRef into the connection user/password")
    void resolvesCredential() {
        java.util.concurrent.atomic.AtomicReference<ExternalJdbcConnectionProvider.JdbcConfig> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        ExternalJdbcConnectionProvider provider = config -> {
            captured.set(config);
            return h2;
        };
        io.kelta.runtime.credential.ResolvedCredential cred = new io.kelta.runtime.credential.ResolvedCredential(
                "c1", "db", "basic_auth",
                Map.of("username", "dbuser", "password", "dbpass"), Map.of(), java.time.Instant.EPOCH);
        CredentialProvider credentials = ref -> "vault-ref".equals(ref)
                ? java.util.Optional.of(cred) : java.util.Optional.empty();
        ExternalJdbcStorageAdapter credAdapter = new ExternalJdbcStorageAdapter(provider, credentials);

        credAdapter.query(orders(Map.of("credentialRef", "vault-ref")), QueryRequest.defaults());

        assertThat(captured.get().username()).isEqualTo("dbuser");
        assertThat(captured.get().password()).isEqualTo("dbpass");
    }

    @Test
    @DisplayName("an unresolved credentialRef is a StorageException")
    void unresolvedCredentialFails() {
        ExternalJdbcStorageAdapter credAdapter =
                new ExternalJdbcStorageAdapter(config -> h2, ref -> java.util.Optional.empty());
        assertThatThrownBy(() ->
                credAdapter.query(orders(Map.of("credentialRef", "missing")), QueryRequest.defaults()))
                .isInstanceOf(StorageException.class);
    }
}
