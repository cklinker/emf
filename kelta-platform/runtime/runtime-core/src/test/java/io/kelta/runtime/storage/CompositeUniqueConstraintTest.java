package io.kelta.runtime.storage;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.model.StorageConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Storage-level tests for composite UNIQUE constraints. Uses H2 in-memory so
 * the same code path that runs against PostgreSQL in production is exercised
 * via standard SQL DDL ({@code ALTER TABLE … ADD CONSTRAINT … UNIQUE}) and
 * INFORMATION_SCHEMA reflection.
 */
class CompositeUniqueConstraintTest {

    private JdbcTemplate jdbcTemplate;
    private PhysicalTableStorageAdapter adapter;
    private CollectionDefinition availability;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS availability"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS kelta_migrations"); } catch (Exception ignored) {}

        SchemaMigrationEngine migrationEngine = new SchemaMigrationEngine(jdbcTemplate);
        adapter = new PhysicalTableStorageAdapter(
                jdbcTemplate, migrationEngine, new tools.jackson.databind.ObjectMapper());

        availability = buildAvailabilityCollection();
        adapter.initializeCollection(availability);
    }

    @Test
    void constraintNameIsDeterministicAcrossFieldOrder() {
        String first = adapter.buildCompositeUniqueName(availability,
                List.of("title", "provider", "region"));
        String reordered = adapter.buildCompositeUniqueName(availability,
                List.of("region", "provider", "title"));
        // Both orderings hash the same sorted column tuple, so the constraint
        // name should be identical — re-creating the "same" constraint with a
        // different argument order must collapse onto one row.
        assertThat(first).isEqualTo(reordered);
        assertThat(first).startsWith(PhysicalTableStorageAdapter.COMPOSITE_UNIQUE_PREFIX);
    }

    @Test
    void addCreatesUniqueConstraintAndIsIdempotent() {
        String name = adapter.addCompositeUniqueConstraint(
                availability, List.of("title", "provider", "region"));
        assertThat(name).startsWith(PhysicalTableStorageAdapter.COMPOSITE_UNIQUE_PREFIX);

        // Second call with the same fields must not raise — the MCP tool can
        // legitimately replay the call after a network blip.
        assertDoesNotThrow(() ->
                adapter.addCompositeUniqueConstraint(availability, List.of("title", "provider", "region")));

        List<CompositeUniqueConstraint> constraints = adapter.listCompositeUniqueConstraints(availability);
        assertThat(constraints).hasSize(1);
        assertThat(constraints.get(0).constraintName()).isEqualTo(name);
        assertThat(constraints.get(0).fieldNames())
                .containsExactlyInAnyOrder("title", "provider", "region");
    }

    @Test
    void duplicateRowOnConstrainedColumnsThrowsUniqueViolationNamingAllFields() {
        adapter.addCompositeUniqueConstraint(availability, List.of("title", "provider", "region"));

        Map<String, Object> row = baseRow("a-1", "Inception", "netflix", "us");
        adapter.create(availability, row);

        // Different ID, same (title, provider, region) tuple — must fail.
        Map<String, Object> dup = baseRow("a-2", "Inception", "netflix", "us");
        assertThatThrownBy(() -> adapter.create(availability, dup))
                .isInstanceOf(UniqueConstraintViolationException.class)
                .satisfies(t -> {
                    UniqueConstraintViolationException u = (UniqueConstraintViolationException) t;
                    // The detected field name lists every column in the constraint
                    // so the 409 response can point to the full tuple rather than
                    // implying one column is the offender.
                    assertThat(u.getFieldName())
                            .contains("title").contains("provider").contains("region");
                });
    }

    @Test
    void distinctTuplesOnConstrainedColumnsAreAccepted() {
        adapter.addCompositeUniqueConstraint(availability, List.of("title", "provider", "region"));

        assertDoesNotThrow(() -> adapter.create(availability,
                baseRow("a-1", "Inception", "netflix", "us")));
        // Only region differs — still a distinct tuple, still allowed.
        assertDoesNotThrow(() -> adapter.create(availability,
                baseRow("a-2", "Inception", "netflix", "gb")));
        // Only provider differs.
        assertDoesNotThrow(() -> adapter.create(availability,
                baseRow("a-3", "Inception", "hbo", "us")));
    }

    @Test
    void dropRemovesTheConstraintAndReportsWhetherItExisted() {
        adapter.addCompositeUniqueConstraint(availability, List.of("title", "provider", "region"));
        assertThat(adapter.listCompositeUniqueConstraints(availability)).hasSize(1);

        boolean dropped = adapter.dropCompositeUniqueConstraint(
                availability, List.of("title", "provider", "region"));
        assertTrue(dropped);
        assertThat(adapter.listCompositeUniqueConstraints(availability)).isEmpty();

        // After the drop, duplicate tuples are accepted again.
        adapter.create(availability, baseRow("a-1", "Inception", "netflix", "us"));
        assertDoesNotThrow(() -> adapter.create(availability,
                baseRow("a-2", "Inception", "netflix", "us")));

        // Dropping the same constraint a second time is a no-op (returns false)
        // instead of throwing — the MCP tool can call it without first checking.
        boolean droppedAgain = adapter.dropCompositeUniqueConstraint(
                availability, List.of("title", "provider", "region"));
        assertFalse(droppedAgain);
    }

    @Test
    void addRejectsLessThanTwoFields() {
        assertThatThrownBy(() ->
                adapter.addCompositeUniqueConstraint(availability, List.of("title")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2");
    }

    @Test
    void addRejectsUnknownField() {
        assertThatThrownBy(() ->
                adapter.addCompositeUniqueConstraint(availability, List.of("title", "ghost")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghost");
    }

    private static CollectionDefinition buildAvailabilityCollection() {
        List<FieldDefinition> fields = List.of(
                new FieldDefinition("title", FieldType.STRING, false, false, false,
                        null, null, null, null, null),
                new FieldDefinition("provider", FieldType.STRING, false, false, false,
                        null, null, null, null, null),
                new FieldDefinition("region", FieldType.STRING, false, false, false,
                        null, null, null, null, null),
                new FieldDefinition("active", FieldType.BOOLEAN, true, false, false,
                        null, null, null, null, null)
        );
        return new CollectionDefinition(
                "availability", "Availability", "Title availability per provider/region",
                fields, StorageConfig.physicalTable("availability"),
                null, null,
                1L, Instant.now(), Instant.now()
        );
    }

    private static Map<String, Object> baseRow(String id, String title, String provider, String region) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("title", title);
        data.put("provider", provider);
        data.put("region", region);
        data.put("active", true);
        data.put("createdAt", Instant.now());
        data.put("updatedAt", Instant.now());
        return data;
    }
}
