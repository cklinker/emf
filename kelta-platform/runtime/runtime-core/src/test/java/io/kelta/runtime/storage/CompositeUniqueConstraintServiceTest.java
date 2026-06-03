package io.kelta.runtime.storage;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.model.StorageConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
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
 * Tests CompositeUniqueConstraintService against an embedded H2 database in
 * PostgreSQL compatibility mode. H2 accepts the {@code CREATE UNIQUE INDEX}
 * DDL we issue and enforces it the same way Postgres does, which is what the
 * service relies on for actual data-layer guarantees.
 *
 * <p>{@link #list(CollectionDefinition)} reads from {@code pg_indexes} and so
 * is exercised separately via {@link #parseColumnsFromIndexDef} unit checks
 * rather than a live H2 round-trip.
 */
class CompositeUniqueConstraintServiceTest {

    private JdbcTemplate jdbcTemplate;
    private PhysicalTableStorageAdapter adapter;
    private CompositeUniqueConstraintService service;
    private CollectionDefinition collection;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        SchemaMigrationEngine migrationEngine = new SchemaMigrationEngine(jdbcTemplate);
        adapter = new PhysicalTableStorageAdapter(jdbcTemplate, migrationEngine,
                new tools.jackson.databind.ObjectMapper());
        service = new CompositeUniqueConstraintService(jdbcTemplate, adapter);

        collection = buildAvailability();
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS availability");
        } catch (RuntimeException ignored) {
            // fresh database — no-op
        }
        adapter.initializeCollection(collection);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CollectionDefinition buildAvailability() {
        List<FieldDefinition> fields = List.of(
                new FieldDefinition("title", FieldType.STRING, false, false, false, null, null, null, null, null),
                new FieldDefinition("provider", FieldType.STRING, false, false, false, null, null, null, null, null),
                new FieldDefinition("region", FieldType.STRING, false, false, false, null, null, null, null, null),
                new FieldDefinition("note", FieldType.STRING, true, false, false, null, null, null, null, null)
        );
        StorageConfig storageConfig = new StorageConfig("availability", Map.of());
        return new CollectionDefinition(
                "availability",
                "Availability",
                "Composite unique constraint test target",
                fields, storageConfig,
                null, null,
                1L, Instant.now(), Instant.now());
    }

    private Map<String, Object> row(String title, String provider, String region) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", java.util.UUID.randomUUID().toString());
        r.put("title", title);
        r.put("provider", provider);
        r.put("region", region);
        r.put("createdAt", Instant.now());
        r.put("updatedAt", Instant.now());
        return r;
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates the index and enforces uniqueness on the column tuple")
        void enforcesUniqueness() {
            CompositeUniqueConstraintService.ConstraintInfo info =
                    service.create(collection, List.of("title", "provider", "region"));

            assertTrue(info.indexName().startsWith("uniq_availability_"),
                    "index name should be prefixed for discoverability: " + info.indexName());
            assertEquals(List.of("title", "provider", "region"), info.fieldNames());
            assertEquals(List.of("title", "provider", "region"), info.columns());

            adapter.create(collection, row("Show A", "Netflix", "US"));
            adapter.create(collection, row("Show A", "Netflix", "UK")); // differing region — allowed

            assertThrows(UniqueConstraintViolationException.class,
                    () -> adapter.create(collection, row("Show A", "Netflix", "US")));
        }

        @Test
        @DisplayName("is idempotent — a second create with the same tuple is a no-op")
        void idempotent() {
            service.create(collection, List.of("title", "provider"));
            assertDoesNotThrow(() -> service.create(collection, List.of("title", "provider")));
        }

        @Test
        @DisplayName("rejects empty field list")
        void rejectsEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.create(collection, List.of()));
        }

        @Test
        @DisplayName("rejects unknown field")
        void rejectsUnknownField() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.create(collection, List.of("title", "nonexistent")));
        }

        @Test
        @DisplayName("refuses to create when existing rows already violate the proposed constraint")
        void refusesWhenExistingDuplicates() {
            adapter.create(collection, row("Show A", "Netflix", "US"));
            adapter.create(collection, row("Show A", "Netflix", "US"));

            assertThrows(UniqueConstraintViolationException.class,
                    () -> service.create(collection, List.of("title", "provider", "region")));
        }
    }

    @Nested
    @DisplayName("buildIndexName()")
    class IndexNaming {

        @Test
        @DisplayName("joins columns under the uniq_ prefix")
        void simpleName() {
            String name = CompositeUniqueConstraintService.buildIndexName(
                    "season", List.of("show_id", "season_number"));
            assertEquals("uniq_season_show_id_season_number", name);
        }

        @Test
        @DisplayName("collapses to a hashed form when the joined name would exceed 63 chars")
        void hashesWhenLong() {
            String veryLong = "a_collection_with_a_distinctly_long_name_to_force_hashing";
            String name = CompositeUniqueConstraintService.buildIndexName(
                    veryLong, List.of("field_one", "field_two", "field_three"));
            assertTrue(name.length() <= 63, "index name must respect Postgres 63-char limit, got " + name.length());
            assertTrue(name.startsWith("uniq_"));
        }
    }

    @Nested
    @DisplayName("parseColumnsFromIndexDef()")
    class ParseIndexDef {

        @Test
        @DisplayName("extracts plain column list")
        void plain() {
            List<String> cols = CompositeUniqueConstraintService.parseColumnsFromIndexDef(
                    "CREATE UNIQUE INDEX uniq_x ON public.x USING btree (a, b, c)");
            assertEquals(List.of("a", "b", "c"), cols);
        }

        @Test
        @DisplayName("strips Postgres double-quoted identifiers")
        void quoted() {
            List<String> cols = CompositeUniqueConstraintService.parseColumnsFromIndexDef(
                    "CREATE UNIQUE INDEX uniq_x ON \"tenant\".\"x\" USING btree (\"first_name\", \"last_name\")");
            assertEquals(List.of("first_name", "last_name"), cols);
        }

        @Test
        @DisplayName("returns empty list for malformed input")
        void malformed() {
            assertEquals(List.of(), CompositeUniqueConstraintService.parseColumnsFromIndexDef("no parens here"));
            assertEquals(List.of(), CompositeUniqueConstraintService.parseColumnsFromIndexDef(null));
        }
    }

    @Nested
    @DisplayName("PhysicalTableStorageAdapter composite violation detection")
    class CompositeViolationDetection {

        @Test
        @DisplayName("extracts a uniq_ index name from the Postgres error message")
        void extractsIndexName() {
            DuplicateKeyException ex = new DuplicateKeyException(
                    "ERROR: duplicate key value violates unique constraint \"uniq_availability_title_provider_region\"\n  Detail: ...");
            assertEquals("uniq_availability_title_provider_region",
                    PhysicalTableStorageAdapter.extractCompositeConstraintName(ex));
        }

        @Test
        @DisplayName("returns null for non-Kelta constraint names")
        void returnsNullForNonKelta() {
            DuplicateKeyException ex = new DuplicateKeyException(
                    "ERROR: duplicate key value violates unique constraint \"pk_availability\"");
            assertNull(PhysicalTableStorageAdapter.extractCompositeConstraintName(ex));
        }

        @Test
        @DisplayName("returns null when message has no constraint name")
        void returnsNullForNoConstraintName() {
            DuplicateKeyException ex = new DuplicateKeyException("no info here");
            assertNull(PhysicalTableStorageAdapter.extractCompositeConstraintName(ex));
        }
    }
}
