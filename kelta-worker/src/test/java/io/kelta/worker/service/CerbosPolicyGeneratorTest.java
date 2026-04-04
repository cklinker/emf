package io.kelta.worker.service;

import io.kelta.worker.service.CerbosPolicyGenerator.CustomRule;
import io.kelta.worker.service.CerbosPolicyGenerator.ProfileData;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CerbosPolicyGenerator")
class CerbosPolicyGeneratorTest {

    private CerbosPolicyGenerator generator;

    private static final String TENANT_ID = "tenant-abc";

    @BeforeEach
    void setUp() {
        generator = new CerbosPolicyGenerator(new ObjectMapper());
    }

    private ProfileData adminProfile() {
        return new ProfileData(
                "admin-profile",
                "System Admin",
                Map.of("VIEW_ALL_DATA", true, "MODIFY_ALL_DATA", true, "MANAGE_USERS", true),
                Map.of("col-1", Map.of("canCreate", true, "canRead", true, "canEdit", true, "canDelete", true)),
                Map.of()
        );
    }

    private ProfileData readOnlyProfile() {
        return new ProfileData(
                "readonly-profile",
                "Read Only",
                Map.of("VIEW_ALL_DATA", false, "MODIFY_ALL_DATA", false),
                Map.of("col-1", Map.of("canCreate", false, "canRead", true, "canEdit", false, "canDelete", false)),
                Map.of()
        );
    }

    @Nested
    @DisplayName("generateDerivedRoles")
    class DerivedRolesTests {

        @Test
        @DisplayName("should generate derived roles with correct API version")
        void generatesCorrectApiVersion() {
            Map<String, Object> policy = generator.generateDerivedRoles(TENANT_ID, List.of(adminProfile()));

            assertThat(policy.get("apiVersion")).isEqualTo("api.cerbos.dev/v1");
        }

        @Test
        @DisplayName("should create one role definition per profile")
        @SuppressWarnings("unchecked")
        void createsOneRolePerProfile() {
            Map<String, Object> policy = generator.generateDerivedRoles(
                    TENANT_ID, List.of(adminProfile(), readOnlyProfile()));

            Map<String, Object> derivedRoles = (Map<String, Object>) policy.get("derivedRoles");
            assertThat(derivedRoles.get("name")).isEqualTo("kelta_roles_" + TENANT_ID);

            List<Map<String, Object>> definitions = (List<Map<String, Object>>) derivedRoles.get("definitions");
            assertThat(definitions).hasSize(2);
            assertThat(definitions.get(0).get("name")).isEqualTo("profile_admin-profile");
            assertThat(definitions.get(1).get("name")).isEqualTo("profile_readonly-profile");
        }

        @Test
        @DisplayName("should set parent role to user")
        @SuppressWarnings("unchecked")
        void setsParentRoleToUser() {
            Map<String, Object> policy = generator.generateDerivedRoles(TENANT_ID, List.of(adminProfile()));

            Map<String, Object> derivedRoles = (Map<String, Object>) policy.get("derivedRoles");
            List<Map<String, Object>> definitions = (List<Map<String, Object>>) derivedRoles.get("definitions");
            assertThat(definitions.get(0).get("parentRoles")).isEqualTo(List.of("user"));
        }

        @Test
        @DisplayName("should include profile and tenant condition in CEL expression")
        @SuppressWarnings("unchecked")
        void includesConditionExpression() {
            Map<String, Object> policy = generator.generateDerivedRoles(TENANT_ID, List.of(adminProfile()));

            Map<String, Object> derivedRoles = (Map<String, Object>) policy.get("derivedRoles");
            List<Map<String, Object>> definitions = (List<Map<String, Object>>) derivedRoles.get("definitions");
            Map<String, Object> condition = (Map<String, Object>) definitions.get(0).get("condition");
            Map<String, Object> match = (Map<String, Object>) condition.get("match");
            String expr = (String) match.get("expr");

            assertThat(expr).contains("P.attr.profileId == \"admin-profile\"");
            assertThat(expr).contains("P.attr.tenantId == \"" + TENANT_ID + "\"");
        }

        @Test
        @DisplayName("should handle empty profiles list")
        @SuppressWarnings("unchecked")
        void handlesEmptyProfiles() {
            Map<String, Object> policy = generator.generateDerivedRoles(TENANT_ID, List.of());

            Map<String, Object> derivedRoles = (Map<String, Object>) policy.get("derivedRoles");
            List<Map<String, Object>> definitions = (List<Map<String, Object>>) derivedRoles.get("definitions");
            assertThat(definitions).isEmpty();
        }
    }

    @Nested
    @DisplayName("generateSystemFeaturePolicy")
    class SystemFeaturePolicyTests {

        @Test
        @DisplayName("should create rules for granted system permissions")
        @SuppressWarnings("unchecked")
        void createsRulesForGrantedPermissions() {
            Map<String, Object> policy = generator.generateSystemFeaturePolicy(
                    TENANT_ID, List.of(adminProfile()));

            Map<String, Object> resourcePolicy = (Map<String, Object>) policy.get("resourcePolicy");
            assertThat(resourcePolicy.get("resource")).isEqualTo("system_feature");
            assertThat(resourcePolicy.get("scope")).isEqualTo(TENANT_ID);

            List<Map<String, Object>> rules = (List<Map<String, Object>>) resourcePolicy.get("rules");
            assertThat(rules).isNotEmpty();

            // Admin has VIEW_ALL_DATA, MODIFY_ALL_DATA, MANAGE_USERS — all true
            List<String> ruleActions = rules.stream()
                    .flatMap(r -> ((List<String>) r.get("actions")).stream())
                    .toList();
            assertThat(ruleActions).contains("VIEW_ALL_DATA", "MODIFY_ALL_DATA", "MANAGE_USERS");
        }

        @Test
        @DisplayName("should not create rules for denied permissions")
        @SuppressWarnings("unchecked")
        void skipsdeniedPermissions() {
            Map<String, Object> policy = generator.generateSystemFeaturePolicy(
                    TENANT_ID, List.of(readOnlyProfile()));

            Map<String, Object> resourcePolicy = (Map<String, Object>) policy.get("resourcePolicy");
            List<Map<String, Object>> rules = (List<Map<String, Object>>) resourcePolicy.get("rules");
            // ReadOnly profile has no true system permissions
            assertThat(rules).isEmpty();
        }
    }

    @Nested
    @DisplayName("generateCollectionPolicy")
    class CollectionPolicyTests {

        @Test
        @DisplayName("should create per-collection CRUD rules")
        @SuppressWarnings("unchecked")
        void createsPerCollectionCrudRules() {
            Map<String, Object> policy = generator.generateCollectionPolicy(
                    TENANT_ID, List.of(adminProfile()), List.of("col-1"));

            Map<String, Object> resourcePolicy = (Map<String, Object>) policy.get("resourcePolicy");
            assertThat(resourcePolicy.get("resource")).isEqualTo("collection");

            List<Map<String, Object>> rules = (List<Map<String, Object>>) resourcePolicy.get("rules");
            // 4 CRUD rules for col-1 + system override rules (VIEW_ALL_DATA read, MODIFY_ALL_DATA create/edit/delete)
            assertThat(rules.size()).isGreaterThanOrEqualTo(4);
        }

        @Test
        @DisplayName("should add VIEW_ALL_DATA override rule")
        @SuppressWarnings("unchecked")
        void addsViewAllDataOverride() {
            Map<String, Object> policy = generator.generateCollectionPolicy(
                    TENANT_ID, List.of(adminProfile()), List.of("col-1"));

            Map<String, Object> resourcePolicy = (Map<String, Object>) policy.get("resourcePolicy");
            List<Map<String, Object>> rules = (List<Map<String, Object>>) resourcePolicy.get("rules");

            boolean hasViewAllOverride = rules.stream().anyMatch(rule -> {
                List<String> actions = (List<String>) rule.get("actions");
                List<String> derivedRoles = (List<String>) rule.get("derivedRoles");
                return actions.equals(List.of("read"))
                        && derivedRoles != null && derivedRoles.contains("profile_admin-profile")
                        && !rule.containsKey("condition");
            });
            assertThat(hasViewAllOverride).isTrue();
        }

        @Test
        @DisplayName("should only include read for read-only profiles")
        @SuppressWarnings("unchecked")
        void onlyReadForReadOnlyProfiles() {
            Map<String, Object> policy = generator.generateCollectionPolicy(
                    TENANT_ID, List.of(readOnlyProfile()), List.of("col-1"));

            Map<String, Object> resourcePolicy = (Map<String, Object>) policy.get("resourcePolicy");
            List<Map<String, Object>> rules = (List<Map<String, Object>>) resourcePolicy.get("rules");

            // Only read action should have the readonly profile's role
            boolean hasReadRule = rules.stream().anyMatch(rule -> {
                List<String> actions = (List<String>) rule.get("actions");
                List<String> derivedRoles = (List<String>) rule.get("derivedRoles");
                return actions.contains("read")
                        && derivedRoles != null && derivedRoles.contains("profile_readonly-profile");
            });
            assertThat(hasReadRule).isTrue();

            // No create/edit/delete rules should have the readonly profile
            boolean hasWriteRule = rules.stream().anyMatch(rule -> {
                List<String> actions = (List<String>) rule.get("actions");
                List<String> derivedRoles = (List<String>) rule.get("derivedRoles");
                return (actions.contains("create") || actions.contains("edit") || actions.contains("delete"))
                        && derivedRoles != null && derivedRoles.contains("profile_readonly-profile");
            });
            assertThat(hasWriteRule).isFalse();
        }
    }

    @Nested
    @DisplayName("generateFieldPolicy")
    class FieldPolicyTests {

        @Test
        @DisplayName("should create default allow rules for all users")
        @SuppressWarnings("unchecked")
        void createsDefaultAllowRules() {
            Map<String, Object> policy = generator.generateFieldPolicy(TENANT_ID, List.of());

            Map<String, Object> resourcePolicy = (Map<String, Object>) policy.get("resourcePolicy");
            List<Map<String, Object>> rules = (List<Map<String, Object>>) resourcePolicy.get("rules");

            assertThat(rules).hasSizeGreaterThanOrEqualTo(2);
            assertThat(rules.get(0).get("actions")).isEqualTo(List.of("read"));
            assertThat(rules.get(0).get("effect")).isEqualTo("EFFECT_ALLOW");
            assertThat(rules.get(1).get("actions")).isEqualTo(List.of("write"));
            assertThat(rules.get(1).get("effect")).isEqualTo("EFFECT_ALLOW");
        }

        @Test
        @DisplayName("should add deny rules for HIDDEN fields")
        @SuppressWarnings("unchecked")
        void addsDenyRulesForHiddenFields() {
            ProfileData profileWithHiddenField = new ProfileData(
                    "p1", "Profile 1",
                    Map.of(), Map.of(),
                    Map.of("col-1", Map.of("field-secret", "HIDDEN"))
            );

            Map<String, Object> policy = generator.generateFieldPolicy(
                    TENANT_ID, List.of(profileWithHiddenField));

            Map<String, Object> resourcePolicy = (Map<String, Object>) policy.get("resourcePolicy");
            List<Map<String, Object>> rules = (List<Map<String, Object>>) resourcePolicy.get("rules");

            boolean hasDenyRule = rules.stream().anyMatch(rule ->
                    "EFFECT_DENY".equals(rule.get("effect"))
                            && ((List<?>) rule.get("actions")).containsAll(List.of("read", "write")));
            assertThat(hasDenyRule).isTrue();
        }

        @Test
        @DisplayName("should add write-only deny for READ_ONLY fields")
        @SuppressWarnings("unchecked")
        void addsWriteDenyForReadOnlyFields() {
            ProfileData profileWithReadOnlyField = new ProfileData(
                    "p1", "Profile 1",
                    Map.of(), Map.of(),
                    Map.of("col-1", Map.of("field-locked", "READ_ONLY"))
            );

            Map<String, Object> policy = generator.generateFieldPolicy(
                    TENANT_ID, List.of(profileWithReadOnlyField));

            Map<String, Object> resourcePolicy = (Map<String, Object>) policy.get("resourcePolicy");
            List<Map<String, Object>> rules = (List<Map<String, Object>>) resourcePolicy.get("rules");

            boolean hasWriteDeny = rules.stream().anyMatch(rule ->
                    "EFFECT_DENY".equals(rule.get("effect"))
                            && ((List<?>) rule.get("actions")).equals(List.of("write")));
            assertThat(hasWriteDeny).isTrue();
        }
    }

    @Nested
    @DisplayName("generateRecordPolicy")
    class RecordPolicyTests {

        @Test
        @DisplayName("should include custom ABAC rules")
        @SuppressWarnings("unchecked")
        void includesCustomAbacRules() {
            CustomRule customRule = new CustomRule(
                    "rule-1", "admin-profile", "col-1",
                    "delete", "EFFECT_DENY",
                    "R.attr.status == \"locked\"", true);

            Map<String, Object> policy = generator.generateRecordPolicy(
                    TENANT_ID, List.of(adminProfile()), List.of("col-1"), List.of(customRule));

            Map<String, Object> resourcePolicy = (Map<String, Object>) policy.get("resourcePolicy");
            List<Map<String, Object>> rules = (List<Map<String, Object>>) resourcePolicy.get("rules");

            boolean hasCustomDeny = rules.stream().anyMatch(rule -> {
                if (!"EFFECT_DENY".equals(rule.get("effect"))) return false;
                List<String> actions = (List<String>) rule.get("actions");
                if (!actions.contains("delete")) return false;
                Map<String, Object> condition = (Map<String, Object>) rule.get("condition");
                if (condition == null) return false;
                Map<String, Object> match = (Map<String, Object>) condition.get("match");
                String expr = (String) match.get("expr");
                return expr.contains("R.attr.status == \"locked\"");
            });
            assertThat(hasCustomDeny).isTrue();
        }

        @Test
        @DisplayName("should skip disabled custom rules")
        @SuppressWarnings("unchecked")
        void skipsDisabledCustomRules() {
            CustomRule disabledRule = new CustomRule(
                    "rule-1", "admin-profile", "col-1",
                    "delete", "EFFECT_DENY", "true", false);

            Map<String, Object> policy = generator.generateRecordPolicy(
                    TENANT_ID, List.of(), List.of(), List.of(disabledRule));

            Map<String, Object> resourcePolicy = (Map<String, Object>) policy.get("resourcePolicy");
            List<Map<String, Object>> rules = (List<Map<String, Object>>) resourcePolicy.get("rules");

            boolean hasCustomRule = rules.stream().anyMatch(rule ->
                    "EFFECT_DENY".equals(rule.get("effect")));
            assertThat(hasCustomRule).isFalse();
        }
    }

    @Nested
    @DisplayName("generateBaseResourcePolicy")
    class BaseResourcePolicyTests {

        @Test
        @DisplayName("should generate empty unscoped base policy")
        @SuppressWarnings("unchecked")
        void generatesEmptyBasePolicy() {
            Map<String, Object> policy = generator.generateBaseResourcePolicy("collection");

            assertThat(policy.get("apiVersion")).isEqualTo("api.cerbos.dev/v1");
            Map<String, Object> resourcePolicy = (Map<String, Object>) policy.get("resourcePolicy");
            assertThat(resourcePolicy.get("version")).isEqualTo("default");
            assertThat(resourcePolicy.get("resource")).isEqualTo("collection");
            assertThat(resourcePolicy.get("rules")).isEqualTo(List.of());
            assertThat(resourcePolicy).doesNotContainKey("scope");
        }
    }
}
