package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecordRuleIndex")
class RecordRuleIndexTest {

    private static final String TENANT = "tenant-1";
    private static final String COLLECTION_ID = "11111111-1111-1111-1111-111111111111";

    private JdbcTemplate jdbcTemplate;
    private RecordRuleIndex index;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build());
        jdbcTemplate.execute("""
                CREATE TABLE profile_custom_rules (
                    id VARCHAR(36) PRIMARY KEY,
                    tenant_id VARCHAR(36) NOT NULL,
                    profile_id VARCHAR(36),
                    collection_id VARCHAR(36),
                    action VARCHAR(32),
                    effect VARCHAR(32),
                    condition_json VARCHAR(4000),
                    enabled BOOLEAN NOT NULL
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE collection (
                    id UUID PRIMARY KEY,
                    name VARCHAR(255)
                )""");
        index = new RecordRuleIndex(jdbcTemplate);
    }

    private void insertRule(String id, String collectionId, boolean enabled) {
        jdbcTemplate.update(
                "INSERT INTO profile_custom_rules (id, tenant_id, profile_id, collection_id, action, effect, condition_json, enabled) "
                        + "VALUES (?, ?, 'profile-1', ?, 'read', 'EFFECT_DENY', '{}', ?)",
                id, TENANT, collectionId, enabled);
    }

    @Test
    @DisplayName("no custom rules → nothing is record-variant")
    void noRules() {
        assertThat(index.hasRecordVariantRules(TENANT, "contacts")).isFalse();
        assertThat(index.hasRecordVariantRules(TENANT, COLLECTION_ID)).isFalse();
    }

    @Test
    @DisplayName("an enabled rule marks the collection variant by UUID and by name")
    void enabledRuleMatchesIdAndName() {
        jdbcTemplate.update("INSERT INTO collection (id, name) VALUES (CAST(? AS UUID), 'contacts')",
                COLLECTION_ID);
        insertRule("r1", COLLECTION_ID, true);

        assertThat(index.hasRecordVariantRules(TENANT, COLLECTION_ID)).isTrue();
        assertThat(index.hasRecordVariantRules(TENANT, "contacts")).isTrue();
        assertThat(index.hasRecordVariantRules(TENANT, "other-collection")).isFalse();
    }

    @Test
    @DisplayName("disabled rules do not count")
    void disabledRuleIgnored() {
        insertRule("r1", COLLECTION_ID, false);
        assertThat(index.hasRecordVariantRules(TENANT, COLLECTION_ID)).isFalse();
    }

    @Test
    @DisplayName("a rule whose collection name cannot be resolved marks everything variant")
    void unresolvableCollectionIsConservative() {
        insertRule("r1", "99999999-9999-9999-9999-999999999999", true);
        assertThat(index.hasRecordVariantRules(TENANT, "anything-at-all")).isTrue();
    }

    @Test
    @DisplayName("eviction picks up rule changes; cached result served until then")
    void evictionRefreshes() {
        assertThat(index.hasRecordVariantRules(TENANT, COLLECTION_ID)).isFalse();

        insertRule("r1", COLLECTION_ID, true);
        // Still the cached answer until the policy-changed event evicts.
        assertThat(index.hasRecordVariantRules(TENANT, COLLECTION_ID)).isFalse();

        index.evictTenant(TENANT);
        assertThat(index.hasRecordVariantRules(TENANT, COLLECTION_ID)).isTrue();
    }

    @Test
    @DisplayName("a lookup failure fails towards 'variant' (full batch check)")
    void lookupFailureIsConservative() {
        jdbcTemplate.execute("DROP TABLE profile_custom_rules");
        assertThat(index.hasRecordVariantRules(TENANT, "contacts")).isTrue();
    }

    @Test
    @DisplayName("null tenant or collection falls back to the batch path")
    void nullArgumentsAreConservative() {
        assertThat(index.hasRecordVariantRules(null, "contacts")).isTrue();
        assertThat(index.hasRecordVariantRules(TENANT, null)).isTrue();
    }
}
