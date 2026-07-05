package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import io.kelta.testharness.fixtures.TenantFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Field-permission identifier translation against real Postgres — the query behind
 * {@code BootstrapRepository.SELECT_PROFILE_FIELD_PERMISSIONS} (kelta-worker), which
 * feeds {@code CerbosPolicySyncService.loadProfileFieldPerms} → the Cerbos field
 * policy CEL for HIDDEN / READ_ONLY / MASKED visibility.
 *
 * <p>The UI stores <em>UUIDs</em> in {@code profile_field_permission.collection_id}
 * / {@code field_id} (V100 adds FKs to {@code collection.id} / {@code field.id}, so
 * they can only ever be UUIDs), but the worker advices check Cerbos with the
 * collection <em>name</em> (URL path segment) and field <em>name</em> (JSON:API
 * attribute key). The query therefore joins {@code field} / {@code collection} to
 * resolve UUIDs to names. Before this translation the CEL was keyed on UUIDs and
 * never matched, so UI-configured field permissions silently did nothing — exactly
 * the class of bug Mockito worker tests can't catch (the DB-constraint-test-gap
 * lesson). End-to-end deny behavior is covered by worker unit tests (harness Cerbos
 * is dev allow-all); this scenario proves the SQL translation + profile scoping.
 *
 * <p>The SQL below mirrors {@code BootstrapRepository.SELECT_PROFILE_FIELD_PERMISSIONS}
 * — keep them in sync.
 */
@DisplayName("Field Masking Permission Translation Scenario")
class FieldMaskingPermissionScenarioTest extends ScenarioBase {

    /** Mirror of BootstrapRepository.SELECT_PROFILE_FIELD_PERMISSIONS. */
    private static final String TRANSLATION_SQL = """
            SELECT COALESCE(c.name, pfp.collection_id) AS collection_id,
                   COALESCE(f.name, pfp.field_id) AS field_id,
                   pfp.visibility
            FROM profile_field_permission pfp
            LEFT JOIN field f ON f.id = pfp.field_id
            LEFT JOIN collection c ON c.id = pfp.collection_id
            WHERE pfp.profile_id = ?
            """;

    private record FieldRef(String collectionId, String collectionName, String fieldId, String fieldName) {}

    @Test
    @DisplayName("resolves UUID collection/field ids to names and scopes rows by profile")
    void translatesFieldPermissionIdentifiers() throws Exception {
        String token = auth.loginAsAdmin(TenantFixture.ECOMMERCE_SLUG);
        String tenantId = auth.extractTenantId(token);

        try (Connection admin = openDbConnection()) {
            // Two real tenant-owned (collection, field) pairs so the joins have genuine
            // UUID targets (system collections belong to the platform tenant).
            List<FieldRef> fields = new ArrayList<>();
            try (PreparedStatement ps = admin.prepareStatement("""
                    SELECT c.id AS cid, c.name AS cname, f.id AS fid, f.name AS fname
                    FROM collection c JOIN field f ON f.collection_id = c.id
                    WHERE c.tenant_id = ? ORDER BY c.id, f.id LIMIT 2
                    """)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        fields.add(new FieldRef(rs.getString("cid"), rs.getString("cname"),
                                rs.getString("fid"), rs.getString("fname")));
                    }
                }
            }
            assertThat(fields).as("ecommerce tenant has ≥2 seeded collection fields").hasSize(2);
            FieldRef fieldA = fields.get(0);
            FieldRef fieldB = fields.get(1);
            assertThat(fieldA.fieldId()).as("field id is a UUID, not the field name").isNotEqualTo(fieldA.fieldName());

            // Two real profiles: one under test, one decoy for the scoping assertion.
            String profileId;
            String otherProfileId;
            try (PreparedStatement ps = admin.prepareStatement(
                    "SELECT id FROM profile WHERE tenant_id = ? ORDER BY name LIMIT 2")) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("tenant has seeded profiles").isTrue();
                    profileId = rs.getString("id");
                    assertThat(rs.next()).as("tenant has ≥2 profiles").isTrue();
                    otherProfileId = rs.getString("id");
                }
            }

            List<String> insertedFieldIds = List.of(fieldA.fieldId(), fieldB.fieldId());
            try {
                clearFieldPermissions(admin, insertedFieldIds);
                insertFieldPermission(admin, tenantId, profileId, fieldA.collectionId(), fieldA.fieldId(), "MASKED");
                insertFieldPermission(admin, tenantId, profileId, fieldB.collectionId(), fieldB.fieldId(), "HIDDEN");
                // Decoy on another profile: must not appear in the probed profile's rows.
                insertFieldPermission(admin, tenantId, otherProfileId, fieldA.collectionId(), fieldA.fieldId(), "READ_ONLY");

                Map<String, String[]> rows = queryTranslated(admin, profileId);

                assertThat(rows.get(fieldA.fieldName()))
                        .as("UUID row A resolves to collection/field names, MASKED preserved")
                        .containsExactly(fieldA.collectionName(), "MASKED");
                assertThat(rows.get(fieldB.fieldName()))
                        .as("UUID row B resolves to names, HIDDEN preserved")
                        .containsExactly(fieldB.collectionName(), "HIDDEN");
            } finally {
                clearFieldPermissions(admin, insertedFieldIds);
            }
        }
    }

    private void clearFieldPermissions(Connection conn, List<String> fieldIds) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM profile_field_permission WHERE field_id = ANY (?)")) {
            ps.setArray(1, conn.createArrayOf("varchar", fieldIds.toArray()));
            ps.executeUpdate();
        }
    }

    private void insertFieldPermission(Connection conn, String tenantId, String profileId,
                                       String collectionId, String fieldId, String visibility)
            throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO profile_field_permission
                    (id, tenant_id, profile_id, collection_id, field_id, visibility)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, tenantId);
            ps.setString(3, profileId);
            ps.setString(4, collectionId);
            ps.setString(5, fieldId);
            ps.setString(6, visibility);
            ps.executeUpdate();
        }
    }

    /** Runs the translation query; returns {@code field → [collection, visibility]}. */
    private Map<String, String[]> queryTranslated(Connection conn, String profileId) throws Exception {
        Map<String, String[]> rows = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(TRANSLATION_SQL)) {
            ps.setString(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.put(rs.getString("field_id"),
                            new String[]{rs.getString("collection_id"), rs.getString("visibility")});
                }
            }
        }
        return rows;
    }
}
