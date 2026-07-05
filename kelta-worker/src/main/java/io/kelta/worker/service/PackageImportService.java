package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.repository.PackageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Applies a metadata package to the current tenant through the platform's
 * standard write path.
 *
 * <p>System-collection types (collections, fields, flows, layouts, validation
 * rules, picklists, UI pages/menus) import via {@link QueryEngine#create}/
 * {@link QueryEngine#update} against their system collections — the same path
 * the admin API and MCP tools use — so quota hooks, physical-table DDL, and the
 * {@code kelta.config.*} NATS broadcasts all fire (the
 * {@code ExternalEntityMaterializer} pattern). Only the pre-metadata-engine
 * authz tables (role/policy/route_policy/field_policy) stay on raw SQL.
 *
 * <p>Cross-tenant references are resolved by <b>natural key</b> (names), never
 * by source UUID: a field's {@code reference_collection_id} is re-pointed at
 * the target tenant's collection with the same name, a layout field finds its
 * section by layout + sort order, and so on. An unresolvable reference fails
 * that item — a dangling UUID is never written.
 *
 * <p>There is deliberately no global transaction: collection imports run DDL
 * and publish NATS events that cannot roll back. Each item is applied in
 * isolation and reported in the {@link ImportReport}; re-running an import
 * converges because every type upserts on its natural key.
 */
@Service
public class PackageImportService {

    private static final Logger log = LoggerFactory.getLogger(PackageImportService.class);

    /**
     * Import order: referenced types strictly before referencing types.
     * The legacy authz types (ROLE/POLICY/ROUTE_POLICY/FIELD_POLICY) are omitted
     * — those tables were dropped in V47; per-tenant authz is profiles +
     * permission-sets, which a sandbox seeds itself via TenantProvisioningHook.
     */
    private static final List<String> TYPE_ORDER = List.of(
            "COLLECTION", "FIELD", "GLOBAL_PICKLIST", "PICKLIST_VALUE",
            "VALIDATION_RULE", "PAGE_LAYOUT", "LAYOUT_SECTION", "LAYOUT_FIELD",
            "FLOW", "UI_PAGE", "UI_MENU", "UI_MENU_ITEM");

    /** Package type → system collection name (QueryEngine import path). */
    private static final Map<String, String> SYSTEM_COLLECTION_BY_TYPE = Map.ofEntries(
            Map.entry("COLLECTION", "collections"),
            Map.entry("FIELD", "fields"),
            Map.entry("GLOBAL_PICKLIST", "global-picklists"),
            Map.entry("PICKLIST_VALUE", "picklist-values"),
            Map.entry("VALIDATION_RULE", "validation-rules"),
            Map.entry("PAGE_LAYOUT", "page-layouts"),
            Map.entry("LAYOUT_SECTION", "layout-sections"),
            Map.entry("LAYOUT_FIELD", "layout-fields"),
            Map.entry("FLOW", "flows"),
            Map.entry("UI_PAGE", "ui-pages"),
            Map.entry("UI_MENU", "ui-menus"),
            Map.entry("UI_MENU_ITEM", "ui-menu-items"));

    private static final Set<String> AUDIT_FIELD_NAMES =
            Set.of("id", "createdAt", "updatedAt", "createdBy", "updatedBy");

    public enum ConflictMode { SKIP, OVERWRITE }

    public record ImportOptions(ConflictMode conflictMode,
                                boolean dryRun,
                                Set<String> typeFilter,
                                Set<String> itemKeyFilter,
                                String executingUserId) {

        public static ImportOptions defaults() {
            return new ImportOptions(ConflictMode.SKIP, false, null, null, null);
        }
    }

    public record ItemResult(String type, String naturalKey, String action, String error) {}

    public record ImportReport(int created, int updated, int skipped, int failed,
                               List<ItemResult> items) {

        public boolean success() {
            return failed == 0;
        }
    }

    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;
    private final PackageRepository repository;
    private final ObjectMapper objectMapper;

    public PackageImportService(QueryEngine queryEngine,
                                CollectionRegistry collectionRegistry,
                                PackageRepository repository,
                                ObjectMapper objectMapper) {
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public ImportReport importPackage(String tenantId, Map<String, Object> pkg, ImportOptions options) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) pkg.getOrDefault("items", List.of());
        ImportContext ctx = new ImportContext(tenantId, options);

        Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();
        for (var item : items) {
            byType.computeIfAbsent((String) item.get("type"), k -> new ArrayList<>()).add(item);
        }

        // In-package id→natural-key maps drive the raw authz-table remaps
        ctx.indexPackageIds(items);

        List<ItemResult> results = new ArrayList<>();
        for (String type : TYPE_ORDER) {
            for (var item : byType.getOrDefault(type, List.of())) {
                Map<String, Object> data = (Map<String, Object>) item.get("data");
                String naturalKey = naturalKeyFor(type, data);
                if (!included(type, naturalKey, options)) {
                    continue;
                }
                try {
                    results.add(importItem(ctx, type, naturalKey, data));
                } catch (Exception e) {
                    log.warn("Package import failed for {} '{}' (tenant={})", type, naturalKey, tenantId, e);
                    results.add(new ItemResult(type, naturalKey, "FAILED", e.getMessage()));
                }
            }
        }

        int created = 0, updated = 0, skipped = 0, failed = 0;
        for (var r : results) {
            switch (r.action()) {
                case "CREATED" -> created++;
                case "UPDATED" -> updated++;
                case "SKIPPED" -> skipped++;
                case "FAILED" -> failed++;
            }
        }
        return new ImportReport(created, updated, skipped, failed, results);
    }

    private boolean included(String type, String naturalKey, ImportOptions options) {
        if (options.typeFilter() != null && !options.typeFilter().contains(type)) {
            return false;
        }
        return options.itemKeyFilter() == null
                || options.itemKeyFilter().contains(type + ":" + naturalKey);
    }

    // ------------------------------------------------------------------
    // Per-item dispatch
    // ------------------------------------------------------------------

    private ItemResult importItem(ImportContext ctx, String type, String naturalKey,
                                  Map<String, Object> data) {
        return switch (type) {
            case "COLLECTION" -> importCollection(ctx, naturalKey, data);
            case "FIELD" -> importField(ctx, naturalKey, data);
            case "GLOBAL_PICKLIST" -> importSimpleByName(ctx, "GLOBAL_PICKLIST", naturalKey, data,
                    ctx.globalPicklistIdByName);
            case "PICKLIST_VALUE" -> importPicklistValue(ctx, naturalKey, data);
            case "VALIDATION_RULE" -> importCollectionChild(ctx, "VALIDATION_RULE", naturalKey, data,
                    ctx.validationRuleIdByKey);
            case "PAGE_LAYOUT" -> importCollectionChild(ctx, "PAGE_LAYOUT", naturalKey, data,
                    ctx.layoutIdByKey);
            case "LAYOUT_SECTION" -> importLayoutSection(ctx, naturalKey, data);
            case "LAYOUT_FIELD" -> importLayoutField(ctx, naturalKey, data);
            case "FLOW" -> importFlow(ctx, naturalKey, data);
            case "ROLE" -> importRole(ctx, naturalKey, data);
            case "POLICY" -> importPolicy(ctx, naturalKey, data);
            case "ROUTE_POLICY" -> importRoutePolicy(ctx, naturalKey, data);
            case "FIELD_POLICY" -> importFieldPolicy(ctx, naturalKey, data);
            case "UI_PAGE" -> importUiPage(ctx, naturalKey, data);
            case "UI_MENU" -> importSimpleByName(ctx, "UI_MENU", naturalKey, data, ctx.menuIdByName);
            case "UI_MENU_ITEM" -> importUiMenuItem(ctx, naturalKey, data);
            default -> new ItemResult(type, naturalKey, "SKIPPED", "Unsupported item type");
        };
    }

    /** Cross-tenant identity of a package item — shared with environment diffing. */
    public static String naturalKeyFor(String type, Map<String, Object> data) {
        return switch (type) {
            case "FIELD" -> data.get("collection_name") + "." + data.get("name");
            case "PICKLIST_VALUE" -> {
                String source = "GLOBAL".equals(data.get("picklist_source_type"))
                        ? String.valueOf(data.get("picklist_name"))
                        : data.get("field_collection_name") + "." + data.get("field_name");
                yield source + ":" + data.get("value");
            }
            case "VALIDATION_RULE", "PAGE_LAYOUT" -> data.get("collection_name") + ":" + data.get("name");
            case "LAYOUT_SECTION" -> data.get("collection_name") + ":" + data.get("layout_name")
                    + ":" + data.get("sort_order");
            case "LAYOUT_FIELD" -> data.get("collection_name") + ":" + data.get("layout_name")
                    + ":" + data.get("section_sort_order") + ":" + data.get("field_name");
            case "UI_PAGE" -> String.valueOf(data.getOrDefault("path", data.get("name")));
            case "UI_MENU_ITEM" -> data.get("menu_name") + ":" + data.get("label");
            case "ROUTE_POLICY" -> data.get("collection_id") + ":" + data.get("operation");
            case "FIELD_POLICY" -> data.get("field_id") + ":" + data.get("operation");
            default -> String.valueOf(data.get("name"));
        };
    }

    // ------------------------------------------------------------------
    // QueryEngine-path importers
    // ------------------------------------------------------------------

    private ItemResult importCollection(ImportContext ctx, String key, Map<String, Object> data) {
        String name = (String) data.get("name");
        String existingId = ctx.collectionIdByName().get(name);
        CollectionDefinition def = systemDef("COLLECTION");

        Map<String, Object> mapped = mapRowToFields(def, data);
        mapped.put("tenantId", ctx.tenantId);
        mapped.put("systemCollection", false);

        return upsertViaEngine(ctx, "COLLECTION", key, def, existingId, mapped,
                id -> ctx.collectionIdByName().put(name, id));
    }

    private ItemResult importField(ImportContext ctx, String key, Map<String, Object> data) {
        CollectionDefinition def = systemDef("FIELD");
        String collectionId = ctx.requireCollection((String) data.get("collection_name"));

        Map<String, Object> mapped = mapRowToFields(def, data);
        mapped.put("collectionId", collectionId);

        Object refName = data.get("reference_collection_name");
        if (data.get("reference_collection_id") != null || refName != null) {
            if (refName == null) {
                throw new IllegalStateException(
                        "Field references a collection but the package carries no reference name "
                                + "(v1 package?) — cannot remap safely");
            }
            mapped.put("referenceCollectionId", ctx.requireCollection((String) refName));
        }

        String existingId = ctx.fieldIdByKey().get(key);
        return upsertViaEngine(ctx, "FIELD", key, def, existingId, mapped,
                id -> ctx.fieldIdByKey().put(key, id));
    }

    private ItemResult importSimpleByName(ImportContext ctx, String type, String key,
                                          Map<String, Object> data, Map<String, String> registry) {
        CollectionDefinition def = systemDef(type);
        Map<String, Object> mapped = mapRowToFields(def, data);
        String existingId = registry.get(key);
        return upsertViaEngine(ctx, type, key, def, existingId, mapped,
                id -> registry.put(key, id));
    }

    private ItemResult importPicklistValue(ImportContext ctx, String key, Map<String, Object> data) {
        CollectionDefinition def = systemDef("PICKLIST_VALUE");
        Map<String, Object> mapped = mapRowToFields(def, data);

        String sourceType = (String) data.get("picklist_source_type");
        String sourceId;
        if ("GLOBAL".equals(sourceType)) {
            String picklistName = (String) data.get("picklist_name");
            sourceId = ctx.globalPicklistIdByName.get(picklistName);
            if (sourceId == null) {
                throw new IllegalStateException("Global picklist not found in target: " + picklistName);
            }
        } else {
            String fieldKey = data.get("field_collection_name") + "." + data.get("field_name");
            sourceId = ctx.fieldIdByKey().get(fieldKey);
            if (sourceId == null) {
                throw new IllegalStateException("Picklist field not found in target: " + fieldKey);
            }
        }
        mapped.put("picklistSourceId", sourceId);

        String existingId = ctx.picklistValueIdByKey.get(sourceType + ":" + sourceId + ":" + data.get("value"));
        return upsertViaEngine(ctx, "PICKLIST_VALUE", key, def, existingId, mapped,
                id -> ctx.picklistValueIdByKey.put(sourceType + ":" + sourceId + ":" + data.get("value"), id));
    }

    /** Shared path for types whose only remapped reference is the owning collection. */
    private ItemResult importCollectionChild(ImportContext ctx, String type, String key,
                                             Map<String, Object> data, Map<String, String> registry) {
        CollectionDefinition def = systemDef(type);
        Map<String, Object> mapped = mapRowToFields(def, data);
        mapped.put("collectionId", ctx.requireCollection((String) data.get("collection_name")));
        String existingId = registry.get(key);
        return upsertViaEngine(ctx, type, key, def, existingId, mapped,
                id -> registry.put(key, id));
    }

    private ItemResult importLayoutSection(ImportContext ctx, String key, Map<String, Object> data) {
        CollectionDefinition def = systemDef("LAYOUT_SECTION");
        Map<String, Object> mapped = mapRowToFields(def, data);
        String layoutKey = data.get("collection_name") + ":" + data.get("layout_name");
        String layoutId = ctx.layoutIdByKey.get(layoutKey);
        if (layoutId == null) {
            throw new IllegalStateException("Layout not found in target: " + layoutKey);
        }
        mapped.put("layoutId", layoutId);
        String existingId = ctx.sectionIdByKey.get(key);
        return upsertViaEngine(ctx, "LAYOUT_SECTION", key, def, existingId, mapped,
                id -> ctx.sectionIdByKey.put(key, id));
    }

    private ItemResult importLayoutField(ImportContext ctx, String key, Map<String, Object> data) {
        CollectionDefinition def = systemDef("LAYOUT_FIELD");
        Map<String, Object> mapped = mapRowToFields(def, data);

        String sectionKey = data.get("collection_name") + ":" + data.get("layout_name")
                + ":" + data.get("section_sort_order");
        String sectionId = ctx.sectionIdByKey.get(sectionKey);
        if (sectionId == null) {
            throw new IllegalStateException("Layout section not found in target: " + sectionKey);
        }
        String fieldKey = data.get("field_collection_name") + "." + data.get("field_name");
        String fieldId = ctx.fieldIdByKey().get(fieldKey);
        if (fieldId == null) {
            throw new IllegalStateException("Layout field target not found: " + fieldKey);
        }
        mapped.put("sectionId", sectionId);
        mapped.put("fieldId", fieldId);

        String existingId = ctx.layoutFieldIdByKey.get(key);
        return upsertViaEngine(ctx, "LAYOUT_FIELD", key, def, existingId, mapped,
                id -> ctx.layoutFieldIdByKey.put(key, id));
    }

    private ItemResult importFlow(ImportContext ctx, String key, Map<String, Object> data) {
        CollectionDefinition def = systemDef("FLOW");
        Map<String, Object> mapped = mapRowToFields(def, data);
        // flow.created_by is NOT NULL REFERENCES platform_user — source user ids
        // are stripped at export, so bind to the executing user (else the target
        // tenant's first user, i.e. the seeded admin).
        mapped.put("createdBy", ctx.resolveDefaultUserId());
        String existingId = ctx.flowIdByName.get(key);
        return upsertViaEngine(ctx, "FLOW", key, def, existingId, mapped,
                id -> ctx.flowIdByName.put(key, id));
    }

    private ItemResult importUiPage(ImportContext ctx, String key, Map<String, Object> data) {
        CollectionDefinition def = systemDef("UI_PAGE");
        Map<String, Object> mapped = mapRowToFields(def, data);
        String existingId = ctx.uiPageIdByPath.get(key);
        return upsertViaEngine(ctx, "UI_PAGE", key, def, existingId, mapped,
                id -> ctx.uiPageIdByPath.put(key, id));
    }

    private ItemResult importUiMenuItem(ImportContext ctx, String key, Map<String, Object> data) {
        CollectionDefinition def = systemDef("UI_MENU_ITEM");
        Map<String, Object> mapped = mapRowToFields(def, data);
        String menuName = (String) data.get("menu_name");
        String menuId = ctx.menuIdByName.get(menuName);
        if (menuId == null) {
            throw new IllegalStateException("Menu not found in target: " + menuName);
        }
        mapped.put("menuId", menuId);
        String existingId = ctx.menuItemIdByKey.get(key);
        return upsertViaEngine(ctx, "UI_MENU_ITEM", key, def, existingId, mapped,
                id -> ctx.menuItemIdByKey.put(key, id));
    }

    private ItemResult upsertViaEngine(ImportContext ctx, String type, String key,
                                       CollectionDefinition def, String existingId,
                                       Map<String, Object> mapped,
                                       java.util.function.Consumer<String> register) {
        // Tenant-scoped system collections (ui-menus, layout-sections, picklist-
        // values, …) need tenant_id: the storage adapter writes it only when the
        // record carries "tenantId" (the JSON:API layer injects it on the HTTP
        // path; a direct queryEngine.create must set it itself, or the NOT NULL
        // tenant_id is violated).
        if (def.tenantScoped()) {
            mapped.putIfAbsent("tenantId", ctx.tenantId);
        }
        if (existingId != null) {
            if (ctx.options.conflictMode() == ConflictMode.SKIP) {
                return new ItemResult(type, key, "SKIPPED", null);
            }
            if (!ctx.options.dryRun()) {
                queryEngine.update(def, existingId, mapped);
            }
            return new ItemResult(type, key, "UPDATED", null);
        }
        if (ctx.options.dryRun()) {
            register.accept("dry-run:" + UUID.randomUUID());
            return new ItemResult(type, key, "CREATED", null);
        }
        Map<String, Object> created = queryEngine.create(def, mapped);
        register.accept(String.valueOf(created.get("id")));
        return new ItemResult(type, key, "CREATED", null);
    }

    // ------------------------------------------------------------------
    // Raw-SQL importers (pre-metadata-engine authz tables)
    // ------------------------------------------------------------------

    private ItemResult importRole(ImportContext ctx, String key, Map<String, Object> data) {
        String existingId = ctx.roleIdByName.get(key);
        if (existingId != null && ctx.options.conflictMode() == ConflictMode.SKIP) {
            return new ItemResult("ROLE", key, "SKIPPED", null);
        }
        if (ctx.options.dryRun()) {
            if (existingId == null) ctx.roleIdByName.put(key, "dry-run:" + UUID.randomUUID());
            return new ItemResult("ROLE", key, existingId != null ? "UPDATED" : "CREATED", null);
        }
        if (existingId != null) {
            repository.getJdbcTemplate().update(
                    "UPDATE role SET description = ? WHERE id = ?",
                    data.get("description"), existingId);
            return new ItemResult("ROLE", key, "UPDATED", null);
        }
        String id = UUID.randomUUID().toString();
        repository.getJdbcTemplate().update(
                "INSERT INTO role (id, tenant_id, name, description, created_at) VALUES (?, ?, ?, ?, ?)",
                id, ctx.tenantId, data.get("name"), data.get("description"), now());
        ctx.roleIdByName.put(key, id);
        return new ItemResult("ROLE", key, "CREATED", null);
    }

    private ItemResult importPolicy(ImportContext ctx, String key, Map<String, Object> data) {
        String rulesJson = jsonString(data.get("rules"));
        String existingId = ctx.policyIdByName.get(key);
        if (existingId != null && ctx.options.conflictMode() == ConflictMode.SKIP) {
            return new ItemResult("POLICY", key, "SKIPPED", null);
        }
        if (ctx.options.dryRun()) {
            if (existingId == null) ctx.policyIdByName.put(key, "dry-run:" + UUID.randomUUID());
            return new ItemResult("POLICY", key, existingId != null ? "UPDATED" : "CREATED", null);
        }
        if (existingId != null) {
            repository.getJdbcTemplate().update(
                    "UPDATE policy SET description = ?, rules = ?::jsonb WHERE id = ?",
                    data.get("description"), rulesJson, existingId);
            return new ItemResult("POLICY", key, "UPDATED", null);
        }
        String id = UUID.randomUUID().toString();
        repository.getJdbcTemplate().update(
                "INSERT INTO policy (id, tenant_id, name, description, rules, created_at) " +
                        "VALUES (?, ?, ?, ?, ?::jsonb, ?)",
                id, ctx.tenantId, data.get("name"), data.get("description"), rulesJson, now());
        ctx.policyIdByName.put(key, id);
        return new ItemResult("POLICY", key, "CREATED", null);
    }

    private ItemResult importRoutePolicy(ImportContext ctx, String key, Map<String, Object> data) {
        String collectionName = ctx.packageCollectionName((String) data.get("collection_id"));
        String policyName = ctx.packagePolicyName((String) data.get("policy_id"));
        String collectionId = ctx.requireCollection(collectionName);
        String policyId = ctx.requirePolicy(policyName);
        String readableKey = collectionName + ":" + data.get("operation");

        if (ctx.options.dryRun()) {
            return new ItemResult("ROUTE_POLICY", readableKey, "CREATED", null);
        }
        int updatedRows = repository.getJdbcTemplate().update(
                "UPDATE route_policy SET policy_id = ? WHERE collection_id = ? AND operation = ?",
                policyId, collectionId, data.get("operation"));
        if (updatedRows > 0) {
            return new ItemResult("ROUTE_POLICY", readableKey, "UPDATED", null);
        }
        repository.getJdbcTemplate().update(
                "INSERT INTO route_policy (id, collection_id, operation, policy_id, created_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), collectionId, data.get("operation"), policyId, now());
        return new ItemResult("ROUTE_POLICY", readableKey, "CREATED", null);
    }

    private ItemResult importFieldPolicy(ImportContext ctx, String key, Map<String, Object> data) {
        String fieldKey = ctx.packageFieldKey((String) data.get("field_id"));
        String policyName = ctx.packagePolicyName((String) data.get("policy_id"));
        String fieldId = ctx.fieldIdByKey().get(fieldKey);
        if (fieldId == null) {
            throw new IllegalStateException("Field-policy target field not found: " + fieldKey);
        }
        String policyId = ctx.requirePolicy(policyName);
        String readableKey = fieldKey + ":" + data.get("operation");

        if (ctx.options.dryRun()) {
            return new ItemResult("FIELD_POLICY", readableKey, "CREATED", null);
        }
        int updatedRows = repository.getJdbcTemplate().update(
                "UPDATE field_policy SET policy_id = ? WHERE field_id = ? AND operation = ?",
                policyId, fieldId, data.get("operation"));
        if (updatedRows > 0) {
            return new ItemResult("FIELD_POLICY", readableKey, "UPDATED", null);
        }
        repository.getJdbcTemplate().update(
                "INSERT INTO field_policy (id, field_id, operation, policy_id, created_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), fieldId, data.get("operation"), policyId, now());
        return new ItemResult("FIELD_POLICY", readableKey, "CREATED", null);
    }

    // ------------------------------------------------------------------
    // Row → system-collection field mapping
    // ------------------------------------------------------------------

    private CollectionDefinition systemDef(String type) {
        String name = SYSTEM_COLLECTION_BY_TYPE.get(type);
        CollectionDefinition def = collectionRegistry.get(name);
        if (def == null) {
            throw new IllegalStateException("System collection not initialized: " + name);
        }
        return def;
    }

    /**
     * Maps an exported snake_case DB row onto the system collection's field
     * names. Audit fields and ids are dropped (the engine owns them); JSON
     * column values arrive as PGobject/String and are parsed back to structures
     * so the storage adapter serializes them exactly once.
     */
    private Map<String, Object> mapRowToFields(CollectionDefinition def, Map<String, Object> row) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (FieldDefinition fd : def.fields()) {
            if (AUDIT_FIELD_NAMES.contains(fd.name()) || "tenantId".equals(fd.name())) {
                continue;
            }
            String column = fd.effectiveColumnName();
            if (!row.containsKey(column)) {
                continue;
            }
            Object value = normalizeValue(row.get(column));
            if (value != null) {
                mapped.put(fd.name(), value);
            }
        }
        return mapped;
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        // JSONB columns come back as org.postgresql.util.PGobject from
        // queryForList, or as raw JSON strings from a deserialized package.
        String candidate = null;
        if ("org.postgresql.util.PGobject".equals(value.getClass().getName())) {
            candidate = value.toString();
        } else if (value instanceof String s) {
            String trimmed = s.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                candidate = trimmed;
            }
        }
        if (candidate != null) {
            try {
                return objectMapper.readValue(candidate, Object.class);
            } catch (Exception e) {
                return value instanceof String ? value : candidate;
            }
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant().toString();
        }
        return value;
    }

    private String jsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON value", e);
        }
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    // ------------------------------------------------------------------
    // Import context: target-tenant natural-key registries
    // ------------------------------------------------------------------

    private class ImportContext {
        final String tenantId;
        final ImportOptions options;

        private Map<String, String> collectionIdByName;
        private Map<String, String> fieldIdByKey;
        final Map<String, String> globalPicklistIdByName = new HashMap<>();
        final Map<String, String> picklistValueIdByKey = new HashMap<>();
        final Map<String, String> validationRuleIdByKey = new HashMap<>();
        final Map<String, String> layoutIdByKey = new HashMap<>();
        final Map<String, String> sectionIdByKey = new HashMap<>();
        final Map<String, String> layoutFieldIdByKey = new HashMap<>();
        final Map<String, String> flowIdByName = new HashMap<>();
        final Map<String, String> roleIdByName = new HashMap<>();
        final Map<String, String> policyIdByName = new HashMap<>();
        final Map<String, String> uiPageIdByPath = new HashMap<>();
        final Map<String, String> menuIdByName = new HashMap<>();
        final Map<String, String> menuItemIdByKey = new HashMap<>();

        // Source-package id → natural key (raw authz-table remap)
        private final Map<String, String> pkgCollectionNameById = new HashMap<>();
        private final Map<String, String> pkgPolicyNameById = new HashMap<>();
        private final Map<String, String> pkgFieldKeyById = new HashMap<>();

        private String defaultUserId;

        ImportContext(String tenantId, ImportOptions options) {
            this.tenantId = tenantId;
            this.options = options;
            seed();
        }

        @SuppressWarnings("unchecked")
        void indexPackageIds(List<Map<String, Object>> items) {
            for (var item : items) {
                Map<String, Object> data = (Map<String, Object>) item.get("data");
                String id = data.get("id") != null ? String.valueOf(data.get("id")) : null;
                if (id == null) {
                    continue;
                }
                switch ((String) item.get("type")) {
                    case "COLLECTION" -> pkgCollectionNameById.put(id, (String) data.get("name"));
                    case "POLICY" -> pkgPolicyNameById.put(id, (String) data.get("name"));
                    case "FIELD" -> pkgFieldKeyById.put(id,
                            data.get("collection_name") + "." + data.get("name"));
                    default -> { }
                }
            }
        }

        void seed() {
            var jdbc = repository.getJdbcTemplate();
            collectionIdByName = new HashMap<>();
            jdbc.queryForList("SELECT id, name FROM collection WHERE tenant_id = ?", tenantId)
                    .forEach(r -> collectionIdByName.put((String) r.get("name"), (String) r.get("id")));
            fieldIdByKey = new HashMap<>();
            jdbc.queryForList(
                    "SELECT f.id, f.name, c.name AS coll FROM field f " +
                            "JOIN collection c ON f.collection_id = c.id WHERE c.tenant_id = ?", tenantId)
                    .forEach(r -> fieldIdByKey.put(r.get("coll") + "." + r.get("name"), (String) r.get("id")));
            jdbc.queryForList("SELECT id, name FROM global_picklist WHERE tenant_id = ?", tenantId)
                    .forEach(r -> globalPicklistIdByName.put((String) r.get("name"), (String) r.get("id")));
            jdbc.queryForList(
                    "SELECT pv.id, pv.picklist_source_type, pv.picklist_source_id, pv.value " +
                            "FROM picklist_value pv WHERE pv.tenant_id = ?", tenantId)
                    .forEach(r -> picklistValueIdByKey.put(
                            r.get("picklist_source_type") + ":" + r.get("picklist_source_id") + ":" + r.get("value"),
                            (String) r.get("id")));
            jdbc.queryForList(
                    "SELECT vr.id, vr.name, c.name AS coll FROM validation_rule vr " +
                            "JOIN collection c ON vr.collection_id = c.id WHERE vr.tenant_id = ?", tenantId)
                    .forEach(r -> validationRuleIdByKey.put(r.get("coll") + ":" + r.get("name"), (String) r.get("id")));
            jdbc.queryForList(
                    "SELECT pl.id, pl.name, c.name AS coll FROM page_layout pl " +
                            "JOIN collection c ON pl.collection_id = c.id WHERE pl.tenant_id = ?", tenantId)
                    .forEach(r -> layoutIdByKey.put(r.get("coll") + ":" + r.get("name"), (String) r.get("id")));
            jdbc.queryForList(
                    "SELECT ls.id, ls.sort_order, pl.name AS layout_name, c.name AS coll " +
                            "FROM layout_section ls JOIN page_layout pl ON ls.layout_id = pl.id " +
                            "JOIN collection c ON pl.collection_id = c.id WHERE pl.tenant_id = ?", tenantId)
                    .forEach(r -> sectionIdByKey.put(
                            r.get("coll") + ":" + r.get("layout_name") + ":" + r.get("sort_order"),
                            (String) r.get("id")));
            jdbc.queryForList(
                    "SELECT lf.id, ls.sort_order AS section_sort, pl.name AS layout_name, " +
                            "c.name AS coll, f.name AS field_name " +
                            "FROM layout_field lf JOIN layout_section ls ON lf.section_id = ls.id " +
                            "JOIN page_layout pl ON ls.layout_id = pl.id " +
                            "JOIN collection c ON pl.collection_id = c.id " +
                            "JOIN field f ON lf.field_id = f.id WHERE pl.tenant_id = ?", tenantId)
                    .forEach(r -> layoutFieldIdByKey.put(
                            r.get("coll") + ":" + r.get("layout_name") + ":" + r.get("section_sort")
                                    + ":" + r.get("field_name"),
                            (String) r.get("id")));
            jdbc.queryForList("SELECT id, name FROM flow WHERE tenant_id = ?", tenantId)
                    .forEach(r -> flowIdByName.put((String) r.get("name"), (String) r.get("id")));
            // role/policy tables were dropped in V47 — not seeded, not imported.
            jdbc.queryForList("SELECT id, name, path FROM ui_page WHERE tenant_id = ?", tenantId)
                    .forEach(r -> uiPageIdByPath.put(
                            String.valueOf(r.get("path") != null ? r.get("path") : r.get("name")),
                            (String) r.get("id")));
            jdbc.queryForList("SELECT id, name FROM ui_menu WHERE tenant_id = ?", tenantId)
                    .forEach(r -> menuIdByName.put((String) r.get("name"), (String) r.get("id")));
            jdbc.queryForList(
                    "SELECT mi.id, mi.label, m.name AS menu_name FROM ui_menu_item mi " +
                            "JOIN ui_menu m ON mi.menu_id = m.id WHERE mi.tenant_id = ?", tenantId)
                    .forEach(r -> menuItemIdByKey.put(
                            r.get("menu_name") + ":" + r.get("label"), (String) r.get("id")));
        }

        Map<String, String> collectionIdByName() {
            return collectionIdByName;
        }

        Map<String, String> fieldIdByKey() {
            return fieldIdByKey;
        }

        String requireCollection(String name) {
            String id = collectionIdByName.get(name);
            if (id == null) {
                throw new IllegalStateException("Collection not found in target: " + name);
            }
            return id;
        }

        String requirePolicy(String name) {
            String id = policyIdByName.get(name);
            if (id == null) {
                throw new IllegalStateException("Policy not found in target: " + name);
            }
            return id;
        }

        String packageCollectionName(String sourceId) {
            String name = pkgCollectionNameById.get(sourceId);
            if (name == null) {
                throw new IllegalStateException(
                        "Route policy references a collection not present in the package: " + sourceId);
            }
            return name;
        }

        String packagePolicyName(String sourceId) {
            String name = pkgPolicyNameById.get(sourceId);
            if (name == null) {
                throw new IllegalStateException(
                        "Policy reference not present in the package: " + sourceId);
            }
            return name;
        }

        String packageFieldKey(String sourceId) {
            String fieldKey = pkgFieldKeyById.get(sourceId);
            if (fieldKey == null) {
                throw new IllegalStateException(
                        "Field policy references a field not present in the package: " + sourceId);
            }
            return fieldKey;
        }

        String resolveDefaultUserId() {
            if (options.executingUserId() != null && !options.executingUserId().isBlank()) {
                return options.executingUserId();
            }
            if (defaultUserId == null) {
                var rows = repository.getJdbcTemplate().queryForList(
                        "SELECT id FROM platform_user WHERE tenant_id = ? ORDER BY created_at ASC LIMIT 1",
                        tenantId);
                if (rows.isEmpty()) {
                    throw new IllegalStateException("Target tenant has no users to own imported flows");
                }
                defaultUserId = (String) rows.get(0).get("id");
            }
            return defaultUserId;
        }
    }
}
