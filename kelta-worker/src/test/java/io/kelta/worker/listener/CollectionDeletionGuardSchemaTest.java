package io.kelta.worker.listener;

import io.kelta.worker.listener.CollectionDeletionGuardHook.ChildTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Pins {@link CollectionDeletionGuardHook}'s child-table map against the shipped Flyway
 * migrations.
 *
 * <p>The hook's unit tests stub {@code JdbcTemplate} by SQL substring, so they pass whether
 * or not the column names are real — exactly the "mocks hide schema drift" gap that
 * {@code concerns.md} keeps recording. This test reads the migration DDL instead. It exists
 * because the first draft of the map assumed a uniform {@code collection_id} +
 * {@code tenant_id} shape and was wrong on three tables: {@code report} keys on
 * {@code primary_collection_id}, {@code layout_related_list} on
 * {@code related_collection_id}, and {@code script_trigger} has no {@code tenant_id}. Each
 * would have thrown at runtime, degraded that count to zero, and silently dropped the table
 * from the confirmation message — the guard would have under-reported what it destroys.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("CollectionDeletionGuardHook schema conformance")
class CollectionDeletionGuardSchemaTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /** table name → its CREATE TABLE body. */
    private Map<String, String> tableBodies;

    /** Full concatenated migration text, for constraint assertions. */
    private String allSql;

    @BeforeAll
    void parseMigrations() throws IOException {
        assertThat(MIGRATIONS)
                .as("migration directory (test must run with the module as cwd)")
                .exists();

        StringBuilder combined = new StringBuilder();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            // Must be VERSION order, not lexicographic: "V181" sorts before "V1__baseline"
            // ('8' < '_'), which would replay the baseline's bare FKs on top of the fix and
            // make noBareCollectionForeignKeysRemain fail against a correct schema. Flyway
            // applies by version number, so this mirrors it.
            List<Path> ordered = files
                    .filter(f -> f.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted(java.util.Comparator.comparingInt(this::migrationVersion))
                    .toList();
            for (Path p : ordered) {
                combined.append(Files.readString(p)).append('\n');
            }
        }
        allSql = combined.toString();

        tableBodies = new HashMap<>();
        Matcher m = Pattern.compile(
                        "CREATE TABLE (?:IF NOT EXISTS )?(\\w+)\\s*\\((.*?)\\n\\);",
                        Pattern.DOTALL)
                .matcher(allSql);
        while (m.find()) {
            tableBodies.put(m.group(1), m.group(2));
        }
        assertThat(tableBodies).as("parsed CREATE TABLE statements").isNotEmpty();
    }

    /** Numeric Flyway version from a {@code V<n>__name.sql} filename. */
    private int migrationVersion(Path p) {
        Matcher m = Pattern.compile("^V(\\d+)__").matcher(p.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    private String bodyOf(String table) {
        String body = tableBodies.get(table);
        if (body == null) {
            fail("No CREATE TABLE found for '" + table + "' in " + MIGRATIONS);
        }
        return body;
    }

    private boolean hasColumn(String table, String column) {
        return Pattern.compile("^\\s*" + Pattern.quote(column) + "\\s", Pattern.MULTILINE)
                .matcher(bodyOf(table))
                .find();
    }

    @Test
    @DisplayName("every counted child table declares its configured collection FK column")
    void collectionColumnsExist() {
        for (ChildTable child : CollectionDeletionGuardHook.CASCADING_CHILDREN) {
            assertThat(hasColumn(child.table(), child.collectionColumn()))
                    .as("%s.%s (label '%s')",
                            child.table(), child.collectionColumn(), child.label())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("tenantScoped matches whether the table actually has tenant_id")
    void tenantScopingMatchesSchema() {
        for (ChildTable child : CollectionDeletionGuardHook.CASCADING_CHILDREN) {
            assertThat(child.tenantScoped())
                    .as("%s.tenantScoped — table %s a tenant_id column",
                            child.table(), hasColumn(child.table(), "tenant_id") ? "HAS" : "has NO")
                    .isEqualTo(hasColumn(child.table(), "tenant_id"));
        }
    }

    @Test
    @DisplayName("generated count SQL omits the tenant predicate exactly when unsupported")
    void countSqlShapeFollowsSchema() {
        for (ChildTable child : CollectionDeletionGuardHook.CASCADING_CHILDREN) {
            String sql = CollectionDeletionGuardHook.countSql(child);
            assertThat(sql).contains(child.collectionColumn() + " = ?");
            if (child.tenantScoped()) {
                assertThat(sql).as("%s is tenant-scoped", child.table()).contains("tenant_id = ?");
            } else {
                assertThat(sql).as("%s has no tenant_id", child.table())
                        .doesNotContain("tenant_id");
            }
        }
    }

    @Test
    @DisplayName("storage-key columns exist on the storage-bearing tables")
    void storageKeyColumnsExist() {
        CollectionDeletionGuardHook.STORAGE_KEY_COLUMNS.forEach((table, column) ->
                assertThat(hasColumn(table, column)).as("%s.%s", table, column).isTrue());
    }

    @Test
    @DisplayName("every counted table is actually given ON DELETE CASCADE by a migration")
    void countedTablesAreCascaded() {
        for (ChildTable child : CollectionDeletionGuardHook.CASCADING_CHILDREN) {
            Pattern cascade = Pattern.compile(
                    "ALTER TABLE " + child.table() + "\\b.*?REFERENCES collection\\(id\\) "
                            + "ON DELETE CASCADE",
                    Pattern.DOTALL);
            assertThat(cascade.matcher(allSql).find())
                    .as("%s should have an ON DELETE CASCADE to collection(id) "
                            + "(counting it in the guard implies the delete destroys it)",
                            child.table())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("no FK to collection(id) is left without a delete action")
    void noBareCollectionForeignKeysRemain() {
        // A bare FK is what made a used collection undeletable (23503 on every delete).
        // V174 reintroduced one a single migration after V173 fixed the same class for
        // field(id), so this asserts the whole surface, not just the tables V181 touched.
        Matcher m = Pattern.compile(
                        "ADD CONSTRAINT (\\w+)\\s+FOREIGN KEY \\([^)]+\\)\\s+"
                                + "REFERENCES collection\\(id\\)([^;]*);",
                        Pattern.DOTALL)
                .matcher(allSql);

        Map<String, String> bare = new HashMap<>();
        while (m.find()) {
            String constraint = m.group(1);
            String tail = m.group(2);
            if (!tail.contains("ON DELETE")) {
                bare.put(constraint, tail.strip());
            } else {
                bare.remove(constraint); // a later migration fixed it
            }
        }

        assertThat(bare.keySet())
                .as("FKs to collection(id) with no ON DELETE action — each one makes a used "
                        + "collection permanently undeletable; add CASCADE or SET NULL and "
                        + "extend CASCADING_CHILDREN if it destroys data")
                .isEmpty();
    }
}
