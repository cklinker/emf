package io.kelta.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generates Cerbos policy JSON from profile permission data.
 *
 * <p>Produces per-tenant policies:
 * <ul>
 *   <li>Derived roles — one role per profile</li>
 *   <li>Resource policies for system_feature, collection, field, record</li>
 * </ul>
 */
@Component
public class CerbosPolicyGenerator {

    private static final Logger log = LoggerFactory.getLogger(CerbosPolicyGenerator.class);

    private final ObjectMapper objectMapper;

    public CerbosPolicyGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Generates the derived roles definition for a tenant.
     */
    public Map<String, Object> generateDerivedRoles(String tenantId, List<ProfileData> profiles) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("apiVersion", "api.cerbos.dev/v1");

        Map<String, Object> derivedRoles = new LinkedHashMap<>();
        derivedRoles.put("name", "kelta_roles_" + tenantId);

        List<Map<String, Object>> definitions = new ArrayList<>();
        for (ProfileData profile : profiles) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("name", "profile:" + profile.id());
            def.put("parentRoles", List.of("user"));

            Map<String, Object> condition = new LinkedHashMap<>();
            Map<String, Object> match = new LinkedHashMap<>();
            match.put("expr", "P.attr.profileId == \"" + profile.id() + "\" && P.attr.tenantId == \"" + tenantId + "\"");
            condition.put("match", match);
            def.put("condition", condition);

            definitions.add(def);
        }

        derivedRoles.put("definitions", definitions);
        policy.put("derivedRoles", derivedRoles);

        return policy;
    }

    /**
     * Generates the system_feature resource policy for a tenant.
     */
    public Map<String, Object> generateSystemFeaturePolicy(String tenantId, List<ProfileData> profiles) {
        // Collect which profiles grant which system permissions
        Map<String, List<String>> permissionToRoles = new LinkedHashMap<>();
        for (ProfileData profile : profiles) {
            for (Map.Entry<String, Boolean> entry : profile.systemPermissions().entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    permissionToRoles.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                            .add("profile:" + profile.id());
                }
            }
        }

        List<Map<String, Object>> rules = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : permissionToRoles.entrySet()) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("actions", List.of(entry.getKey()));
            rule.put("effect", "EFFECT_ALLOW");
            rule.put("derivedRoles", entry.getValue());
            rules.add(rule);
        }

        return buildResourcePolicy("system_feature", tenantId, rules);
    }

    /**
     * Generates the collection resource policy for a tenant.
     */
    public Map<String, Object> generateCollectionPolicy(String tenantId,
                                                          List<ProfileData> profiles,
                                                          List<String> collectionIds) {
        List<Map<String, Object>> rules = new ArrayList<>();

        // Per-collection CRUD rules
        for (String collectionId : collectionIds) {
            for (String action : List.of("create", "read", "edit", "delete")) {
                List<String> allowedRoles = new ArrayList<>();
                for (ProfileData profile : profiles) {
                    Map<String, Boolean> objPerms = profile.objectPermissions().get(collectionId);
                    if (objPerms != null && isActionAllowed(objPerms, action)) {
                        allowedRoles.add("profile:" + profile.id());
                    }
                }
                if (!allowedRoles.isEmpty()) {
                    Map<String, Object> rule = new LinkedHashMap<>();
                    rule.put("actions", List.of(action));
                    rule.put("effect", "EFFECT_ALLOW");
                    rule.put("derivedRoles", allowedRoles);

                    Map<String, Object> condition = new LinkedHashMap<>();
                    Map<String, Object> match = new LinkedHashMap<>();
                    match.put("expr", "R.attr.collectionId == \"" + collectionId + "\"");
                    condition.put("match", match);
                    rule.put("condition", condition);

                    rules.add(rule);
                }
            }
        }

        // System permission overrides: VIEW_ALL_DATA → read, MODIFY_ALL_DATA → create/edit/delete
        List<String> viewAllRoles = new ArrayList<>();
        List<String> modifyAllRoles = new ArrayList<>();
        for (ProfileData profile : profiles) {
            if (Boolean.TRUE.equals(profile.systemPermissions().get("VIEW_ALL_DATA"))) {
                viewAllRoles.add("profile:" + profile.id());
            }
            if (Boolean.TRUE.equals(profile.systemPermissions().get("MODIFY_ALL_DATA"))) {
                modifyAllRoles.add("profile:" + profile.id());
            }
        }

        if (!viewAllRoles.isEmpty()) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("actions", List.of("read"));
            rule.put("effect", "EFFECT_ALLOW");
            rule.put("derivedRoles", viewAllRoles);
            rules.add(rule);
        }

        if (!modifyAllRoles.isEmpty()) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("actions", List.of("create", "edit", "delete"));
            rule.put("effect", "EFFECT_ALLOW");
            rule.put("derivedRoles", modifyAllRoles);
            rules.add(rule);
        }

        return buildResourcePolicy("collection", tenantId, rules);
    }

    /**
     * Generates the field resource policy for a tenant.
     */
    public Map<String, Object> generateFieldPolicy(String tenantId, List<ProfileData> profiles) {
        List<Map<String, Object>> rules = new ArrayList<>();

        // Default: allow all reads and writes
        Map<String, Object> defaultReadRule = new LinkedHashMap<>();
        defaultReadRule.put("actions", List.of("read"));
        defaultReadRule.put("effect", "EFFECT_ALLOW");
        defaultReadRule.put("roles", List.of("user"));
        rules.add(defaultReadRule);

        Map<String, Object> defaultWriteRule = new LinkedHashMap<>();
        defaultWriteRule.put("actions", List.of("write"));
        defaultWriteRule.put("effect", "EFFECT_ALLOW");
        defaultWriteRule.put("roles", List.of("user"));
        rules.add(defaultWriteRule);

        // Deny rules for HIDDEN and READ_ONLY fields per profile
        for (ProfileData profile : profiles) {
            for (Map.Entry<String, Map<String, String>> collEntry : profile.fieldPermissions().entrySet()) {
                String collectionId = collEntry.getKey();
                for (Map.Entry<String, String> fieldEntry : collEntry.getValue().entrySet()) {
                    String fieldId = fieldEntry.getKey();
                    String visibility = fieldEntry.getValue();

                    String fieldExpr = "R.attr.collectionId == \"" + collectionId
                            + "\" && R.attr.fieldId == \"" + fieldId + "\"";

                    if ("HIDDEN".equals(visibility)) {
                        // Deny read and write
                        Map<String, Object> rule = new LinkedHashMap<>();
                        rule.put("actions", List.of("read", "write"));
                        rule.put("effect", "EFFECT_DENY");
                        rule.put("derivedRoles", List.of("profile:" + profile.id()));
                        Map<String, Object> condition = new LinkedHashMap<>();
                        condition.put("match", Map.of("expr", fieldExpr));
                        rule.put("condition", condition);
                        rules.add(rule);
                    } else if ("READ_ONLY".equals(visibility)) {
                        // Deny write only
                        Map<String, Object> rule = new LinkedHashMap<>();
                        rule.put("actions", List.of("write"));
                        rule.put("effect", "EFFECT_DENY");
                        rule.put("derivedRoles", List.of("profile:" + profile.id()));
                        Map<String, Object> condition = new LinkedHashMap<>();
                        condition.put("match", Map.of("expr", fieldExpr));
                        rule.put("condition", condition);
                        rules.add(rule);
                    }
                }
            }
        }

        return buildResourcePolicy("field", tenantId, rules);
    }

    /**
     * Generates the record resource policy for a tenant (base CRUD + custom ABAC rules).
     */
    public Map<String, Object> generateRecordPolicy(String tenantId,
                                                      List<ProfileData> profiles,
                                                      List<String> collectionIds,
                                                      List<CustomRule> customRules) {
        // Start with same collection-level CRUD rules applied to records
        List<Map<String, Object>> rules = new ArrayList<>();

        // Per-collection CRUD (same as collection policy)
        for (String collectionId : collectionIds) {
            for (String action : List.of("create", "read", "edit", "delete")) {
                List<String> allowedRoles = new ArrayList<>();
                for (ProfileData profile : profiles) {
                    Map<String, Boolean> objPerms = profile.objectPermissions().get(collectionId);
                    if (objPerms != null && isActionAllowed(objPerms, action)) {
                        allowedRoles.add("profile:" + profile.id());
                    }
                }
                if (!allowedRoles.isEmpty()) {
                    Map<String, Object> rule = new LinkedHashMap<>();
                    rule.put("actions", List.of(action));
                    rule.put("effect", "EFFECT_ALLOW");
                    rule.put("derivedRoles", allowedRoles);
                    Map<String, Object> condition = new LinkedHashMap<>();
                    condition.put("match", Map.of("expr", "R.attr.collectionId == \"" + collectionId + "\""));
                    rule.put("condition", condition);
                    rules.add(rule);
                }
            }
        }

        // System permission overrides
        List<String> viewAllRoles = new ArrayList<>();
        List<String> modifyAllRoles = new ArrayList<>();
        for (ProfileData profile : profiles) {
            if (Boolean.TRUE.equals(profile.systemPermissions().get("VIEW_ALL_DATA"))) {
                viewAllRoles.add("profile:" + profile.id());
            }
            if (Boolean.TRUE.equals(profile.systemPermissions().get("MODIFY_ALL_DATA"))) {
                modifyAllRoles.add("profile:" + profile.id());
            }
        }

        if (!viewAllRoles.isEmpty()) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("actions", List.of("read"));
            rule.put("effect", "EFFECT_ALLOW");
            rule.put("derivedRoles", viewAllRoles);
            rules.add(rule);
        }

        if (!modifyAllRoles.isEmpty()) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("actions", List.of("create", "edit", "delete"));
            rule.put("effect", "EFFECT_ALLOW");
            rule.put("derivedRoles", modifyAllRoles);
            rules.add(rule);
        }

        // Custom ABAC rules from admin UI
        for (CustomRule customRule : customRules) {
            if (!customRule.enabled()) continue;

            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("actions", List.of(customRule.action()));
            rule.put("effect", "EFFECT_DENY".equalsIgnoreCase(customRule.effect())
                    ? "EFFECT_DENY" : "EFFECT_ALLOW");
            rule.put("derivedRoles", List.of("profile:" + customRule.profileId()));

            String collExpr = "R.attr.collectionId == \"" + customRule.collectionId() + "\"";
            String celExpr = customRule.celExpression();
            String combinedExpr = celExpr != null && !celExpr.isBlank()
                    ? collExpr + " && " + celExpr
                    : collExpr;

            Map<String, Object> condition = new LinkedHashMap<>();
            condition.put("match", Map.of("expr", combinedExpr));
            rule.put("condition", condition);

            rules.add(rule);
        }

        return buildResourcePolicy("record", tenantId, rules);
    }

    private Map<String, Object> buildResourcePolicy(String resource, String tenantId,
                                                      List<Map<String, Object>> rules) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("apiVersion", "api.cerbos.dev/v1");

        Map<String, Object> resourcePolicy = new LinkedHashMap<>();
        resourcePolicy.put("version", "default");
        resourcePolicy.put("scope", tenantId);
        resourcePolicy.put("resource", resource);
        resourcePolicy.put("importDerivedRoles", List.of("kelta_roles_" + tenantId));
        resourcePolicy.put("rules", rules);

        policy.put("resourcePolicy", resourcePolicy);
        return policy;
    }

    private boolean isActionAllowed(Map<String, Boolean> objPerms, String action) {
        return switch (action) {
            case "create" -> Boolean.TRUE.equals(objPerms.get("canCreate"));
            case "read" -> Boolean.TRUE.equals(objPerms.get("canRead"));
            case "edit" -> Boolean.TRUE.equals(objPerms.get("canEdit"));
            case "delete" -> Boolean.TRUE.equals(objPerms.get("canDelete"));
            default -> false;
        };
    }

    // =========================================================================
    // Data records for policy generation input
    // =========================================================================

    public record ProfileData(
            String id,
            String name,
            Map<String, Boolean> systemPermissions,
            Map<String, Map<String, Boolean>> objectPermissions,
            Map<String, Map<String, String>> fieldPermissions
    ) {}

    public record CustomRule(
            String id,
            String profileId,
            String collectionId,
            String action,
            String effect,
            String celExpression,
            boolean enabled
    ) {}
}
