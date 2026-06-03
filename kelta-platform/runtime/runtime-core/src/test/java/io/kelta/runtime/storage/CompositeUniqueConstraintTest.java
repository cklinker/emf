package io.kelta.runtime.storage;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.model.StorageConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for composite (multi-column) unique constraints on the
 * physical-table storage adapter. Covers DDL emission, idempotent
 * creation, listing via information_schema, drop, end-to-end
 * enforcement, and violation-to-exception mapping.
 */
class CompositeUniqueConstraintTest {

    private JdbcTemplate jdbcTemplate;
    private PhysicalTableStorageAdapter adapter;
    private CollectionDefinition collection;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);

        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS kelta_migrations");
            jdbcTemplate.execute("DROP TABLE IF EXISTS availability");
        } catch (Exception ignored) {
            // first-run is fine
        }

        SchemaMigrationEngine migrationEngine = new SchemaMigrationEngine(jdbcTemplate);
        adapter = new PhysicalTableStorageAdapter(
                jdbcTemplate, migrationEngine, new tools.jackson.databind.ObjectMapper());

        // (title, provider, region) — the Availability example from the task brief.
        List<FieldDefinition> fields = List.of(
                new FieldDefinition("title", FieldType.STRING, false, false, false,
                        null, null, null, null, null),
                new FieldDefinition("provider", FieldType.STRING, false, false, false,
                        null, null, null, null, null),
                new FieldDefinition("region", FieldType.STRING, false, false, false,
                        null, null, null, null, null),
                new FieldDefinition("notes", FieldType.STRING, true, false, false,
                        null, null, null, null, null));
        StorageConfig storageConfig = new StorageConfig("availability", Map.of());
        collection = new CollectionDefinition(
                "availability", "Availability", "Title availability matrix",
                fields, storageConfig, null, null, 1L,
                Instant.now(), Instant.now());

        adapter.initializeCollection(collection);
    }

    @Test
    @DisplayName("Creates a composite unique constraint over the named columns")
    void createsCompositeUniqueConstraint() {
        String name = adapter.createCompositeUniqueConstraint(
                collection, List.of("title", "provider", "region"));

        assertNotNull(name);
        assertTrue(name.toLowerCase().startsWith("cuq_"),
                "expected cuq_-prefixed name, got: " + name);
        assertTrue(name.length() <= 63,
                "constraint name must fit Postgres's 63-char identifier limit, got: " + name);

        List<CompositeUniqueConstraint> constraints =
                adapter.listCompositeUniqueConstraints(collection);
        assertEquals(1, constraints.size());
        assertEquals(List.of("title", "provider", "region"), constraints.get(0).fieldNames());
        assertEquals(name.toLowerCase(), constraints.get(0).name().toLowerCase());
    }

    @Test
    @DisplayName("Creating the same constraint twice is idempotent")
    void createCompositeUniqueConstraintIsIdempotent() {
        String first = adapter.createCompositeUniqueConstraint(
                collection, List.of("title", "provider"));
        String second = adapter.createCompositeUniqueConstraint(
                collection, List.of("title", "provider"));

        assertEquals(first, second);
        assertEquals(1, adapter.listCompositeUniqueConstraints(collection).size());
    }

    @Test
    @DisplayName("Column order is part of the constraint identity")
    void columnOrderProducesDistinctConstraints() {
        String ab = adapter.createCompositeUniqueConstraint(
                collection, List.of("title", "provider"));
        String ba = adapter.createCompositeUniqueConstraint(
                collection, List.of("provider", "title"));

        assertNotEquals(ab, ba,
                "(a,b) and (b,a) should yield distinct constraint names — order matters for index lookups");
        assertEquals(2, adapter.listCompositeUniqueConstraints(collection).size());
    }

    @Test
    @DisplayName("Rejects field names that don't exist on the collection")
    void rejectsUnknownField() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.createCompositeUniqueConstraint(
                        collection, List.of("title", "nonexistent")));
        assertTrue(ex.getMessage().contains("nonexistent"),
                "error message should name the missing field, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Rejects constraint requests with fewer than 2 fields")
    void rejectsSingleField() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.createCompositeUniqueConstraint(
                        collection, List.of("title")));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.createCompositeUniqueConstraint(
                        collection, List.of()));
    }

    @Test
    @DisplayName("Rejects duplicate field names within a single constraint")
    void rejectsDuplicateField() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.createCompositeUniqueConstraint(
                        collection, List.of("title", "title")));
        assertTrue(ex.getMessage().toLowerCase().contains("title"));
    }

    @Test
    @DisplayName("Inserting a duplicate tuple raises UniqueConstraintViolationException")
    void duplicateInsertRaisesViolation() {
        adapter.createCompositeUniqueConstraint(
                collection, List.of("title", "provider", "region"));

        Map<String, Object> first = newRow("1", "The Matrix", "Netflix", "US");
        adapter.create(collection, first);

        Map<String, Object> dupe = newRow("2", "The Matrix", "Netflix", "US");
        UniqueConstraintViolationException ex = assertThrows(
                UniqueConstraintViolationException.class,
                () -> adapter.create(collection, dupe),
                "duplicate tuple insert must raise UniqueConstraintViolationException "
                        + "so GlobalExceptionHandler maps it to HTTP 409 — not a generic "
                        + "StorageException that would surface as 500");

        // The exception should point at the constrained field tuple — exact
        // formatting depends on the underlying JDBC driver, but the title
        // field must appear so a caller can act on the 409 response.
        String detail = ex.getMessage().toLowerCase();
        assertTrue(detail.contains("title"),
                "violation message should mention 'title', got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Different tuples with overlapping columns are allowed")
    void nonDuplicateTuplesAreAllowed() {
        adapter.createCompositeUniqueConstraint(
                collection, List.of("title", "provider", "region"));

        adapter.create(collection, newRow("1", "Movie", "Netflix", "US"));
        // Only region differs — should be accepted.
        adapter.create(collection, newRow("2", "Movie", "Netflix", "UK"));
        // Only provider differs — accepted.
        adapter.create(collection, newRow("3", "Movie", "Hulu", "US"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM availability", Integer.class);
        assertEquals(3, count);
    }

    @Test
    @DisplayName("Drop is idempotent and removes the constraint")
    void dropCompositeUniqueConstraint() {
        String name = adapter.createCompositeUniqueConstraint(
                collection, List.of("title", "provider"));
        assertEquals(1, adapter.listCompositeUniqueConstraints(collection).size());

        adapter.dropCompositeUniqueConstraint(collection, name);
        assertEquals(0, adapter.listCompositeUniqueConstraints(collection).size());

        // Dropping again is a no-op (IF EXISTS).
        adapter.dropCompositeUniqueConstraint(collection, name);
    }

    @Test
    @DisplayName("Constraint name builder produces a stable, bounded identifier")
    void constraintNameIsStableAndBounded() {
        String a = PhysicalTableStorageAdapter.buildCompositeConstraintName(
                "availability", List.of("title", "provider", "region"));
        String b = PhysicalTableStorageAdapter.buildCompositeConstraintName(
                "availability", List.of("title", "provider", "region"));
        assertEquals(a, b, "buildCompositeConstraintName must be deterministic");
        assertTrue(a.length() <= 63);

        // A very long table name should still produce a 63-char identifier
        // via the CRC fallback inside buildBoundedIdentifier.
        String long63 = PhysicalTableStorageAdapter.buildCompositeConstraintName(
                "this_is_a_very_long_table_name_that_pushes_the_postgres_identifier_limit",
                List.of("col_a", "col_b"));
        assertTrue(long63.length() <= 63,
                "long names must be CRC-folded to fit the 63-char limit, got: " + long63);
        assertTrue(long63.startsWith("cuq_"));
    }

    private static Map<String, Object> newRow(String id, String title, String provider, String region) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("title", title);
        row.put("provider", provider);
        row.put("region", region);
        row.put("createdAt", Instant.now());
        row.put("updatedAt", Instant.now());
        return row;
    }
}
