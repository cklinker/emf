package io.kelta.worker.service;

import io.kelta.worker.repository.PackageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PackageService {

    private static final Logger log = LoggerFactory.getLogger(PackageService.class);

    private final PackageRepository repository;
    private final ObjectMapper objectMapper;

    public PackageService(PackageRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> exportPackage(String tenantId, Map<String, Object> options) {
        String name = (String) options.get("name");
        String version = (String) options.get("version");
        String description = (String) options.getOrDefault("description", "");

        List<String> collectionIds = getStringList(options, "collectionIds");
        List<String> roleIds = getStringList(options, "roleIds");
        List<String> policyIds = getStringList(options, "policyIds");
        List<String> uiPageIds = getStringList(options, "uiPageIds");
        List<String> uiMenuIds = getStringList(options, "uiMenuIds");

        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("name", name);
        pkg.put("version", version);
        pkg.put("description", description);
        pkg.put("exportedAt", java.time.Instant.now().toString());

        List<Map<String, Object>> items = new ArrayList<>();

        // Export collections and their fields
        var collections = repository.findCollectionsByIds(tenantId, collectionIds);
        for (var col : collections) {
            items.add(buildItem("COLLECTION", col));
        }
        var fields = repository.findFieldsByCollectionIds(tenantId, collectionIds);
        for (var field : fields) {
            items.add(buildItem("FIELD", field));
        }

        // Export roles
        var roles = repository.findRolesByIds(tenantId, roleIds);
        for (var role : roles) {
            items.add(buildItem("ROLE", role));
        }

        // Export policies and their route/field policies
        var policies = repository.findPoliciesByIds(tenantId, policyIds);
        for (var policy : policies) {
            items.add(buildItem("POLICY", policy));
        }
        var routePolicies = repository.findRoutePoliciesByPolicyIds(tenantId, policyIds);
        for (var rp : routePolicies) {
            items.add(buildItem("ROUTE_POLICY", rp));
        }
        var fieldPolicies = repository.findFieldPoliciesByPolicyIds(tenantId, policyIds);
        for (var fp : fieldPolicies) {
            items.add(buildItem("FIELD_POLICY", fp));
        }

        // Export UI pages
        var pages = repository.findUiPagesByIds(tenantId, uiPageIds);
        for (var page : pages) {
            items.add(buildItem("UI_PAGE", page));
        }

        // Export UI menus and their items
        var menus = repository.findUiMenusByIds(tenantId, uiMenuIds);
        for (var menu : menus) {
            items.add(buildItem("UI_MENU", menu));
        }
        var menuItems = repository.findUiMenuItemsByMenuIds(tenantId, uiMenuIds);
        for (var mi : menuItems) {
            items.add(buildItem("UI_MENU_ITEM", mi));
        }

        pkg.put("items", items);

        // Record in history
        List<Map<String, Object>> summaryItems = items.stream()
                .map(item -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("type", mapItemTypeToUiType((String) item.get("type")));
                    summary.put("id", ((Map<String, Object>) item.get("data")).get("id"));
                    summary.put("name", ((Map<String, Object>) item.get("data")).get("name"));
                    return summary;
                })
                .toList();

        try {
            String itemsJson = objectMapper.writeValueAsString(summaryItems);
            String historyId = repository.save(tenantId, name, version, description, "export", "success", itemsJson);
            log.info("Package exported: id={}, name={}, version={}, items={}", historyId, name, version, items.size());
        } catch (Exception e) {
            log.error("Failed to record export history", e);
        }

        return pkg;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> previewImport(String tenantId, Map<String, Object> pkg) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) pkg.getOrDefault("items", List.of());

        List<Map<String, Object>> creates = new ArrayList<>();
        List<Map<String, Object>> updates = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();

        // Group items by type
        Map<String, List<Map<String, Object>>> itemsByType = items.stream()
                .collect(Collectors.groupingBy(item -> (String) item.get("type")));

        // Check collections
        checkExisting(tenantId, itemsByType.getOrDefault("COLLECTION", List.of()),
                creates, updates, conflicts, "collection");

        // Check roles
        checkExisting(tenantId, itemsByType.getOrDefault("ROLE", List.of()),
                creates, updates, conflicts, "role");

        // Check policies
        checkExisting(tenantId, itemsByType.getOrDefault("POLICY", List.of()),
                creates, updates, conflicts, "policy");

        // Check UI pages
        checkExisting(tenantId, itemsByType.getOrDefault("UI_PAGE", List.of()),
                creates, updates, conflicts, "page");

        // Check UI menus
        checkExisting(tenantId, itemsByType.getOrDefault("UI_MENU", List.of()),
                creates, updates, conflicts, "menu");

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("creates", creates);
        preview.put("updates", updates);
        preview.put("conflicts", conflicts);
        return preview;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> importPackage(String tenantId, Map<String, Object> pkg, boolean dryRun) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) pkg.getOrDefault("items", List.of());
        String name = (String) pkg.getOrDefault("name", "unnamed");
        String version = (String) pkg.getOrDefault("version", "0.0.0");

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        if (dryRun) {
            // For dry run, just return the preview counts
            var preview = previewImport(tenantId, pkg);
            List<?> createsList = (List<?>) preview.get("creates");
            List<?> updatesList = (List<?>) preview.get("updates");
            List<?> conflictsList = (List<?>) preview.get("conflicts");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("created", createsList.size());
            result.put("updated", updatesList.size());
            result.put("skipped", conflictsList.size());
            result.put("errors", List.of());
            return result;
        }

        // Group items by type for ordered processing
        Map<String, List<Map<String, Object>>> itemsByType = items.stream()
                .collect(Collectors.groupingBy(item -> (String) item.get("type")));

        // Process in dependency order: collections -> fields -> roles -> policies -> route/field policies -> UI
        created += importItems(tenantId, itemsByType.getOrDefault("COLLECTION", List.of()),
                "collection", errors);
        created += importItems(tenantId, itemsByType.getOrDefault("FIELD", List.of()),
                "field", errors);
        created += importItems(tenantId, itemsByType.getOrDefault("ROLE", List.of()),
                "role", errors);
        created += importItems(tenantId, itemsByType.getOrDefault("POLICY", List.of()),
                "policy", errors);
        created += importItems(tenantId, itemsByType.getOrDefault("ROUTE_POLICY", List.of()),
                "route_policy", errors);
        created += importItems(tenantId, itemsByType.getOrDefault("FIELD_POLICY", List.of()),
                "field_policy", errors);
        created += importItems(tenantId, itemsByType.getOrDefault("UI_PAGE", List.of()),
                "page", errors);
        created += importItems(tenantId, itemsByType.getOrDefault("UI_MENU", List.of()),
                "menu", errors);
        created += importItems(tenantId, itemsByType.getOrDefault("UI_MENU_ITEM", List.of()),
                "menu_item", errors);

        boolean success = errors.isEmpty();
        String status = success ? "success" : "failed";

        // Record in history
        try {
            List<Map<String, Object>> summaryItems = items.stream()
                    .filter(item -> "COLLECTION".equals(item.get("type"))
                            || "ROLE".equals(item.get("type"))
                            || "POLICY".equals(item.get("type"))
                            || "UI_PAGE".equals(item.get("type"))
                            || "UI_MENU".equals(item.get("type")))
                    .map(item -> {
                        Map<String, Object> data = (Map<String, Object>) item.get("data");
                        Map<String, Object> summary = new LinkedHashMap<>();
                        summary.put("type", mapItemTypeToUiType((String) item.get("type")));
                        summary.put("id", data.get("id"));
                        summary.put("name", data.get("name"));
                        return summary;
                    })
                    .toList();
            String itemsJson = objectMapper.writeValueAsString(summaryItems);
            repository.save(tenantId, name, version, null, "import", status, itemsJson);
        } catch (Exception e) {
            log.error("Failed to record import history", e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    public List<Map<String, Object>> getHistory(String tenantId) {
        var rows = repository.findAllByTenantId(tenantId);
        return rows.stream().map(this::mapHistoryRow).toList();
    }

    private Map<String, Object> mapHistoryRow(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("name", row.get("name"));
        result.put("version", row.get("version"));
        result.put("type", row.get("type"));
        result.put("status", row.get("status"));
        result.put("createdAt", row.get("created_at") != null ? row.get("created_at").toString() : null);

        // Parse items JSON
        Object itemsRaw = row.get("items");
        if (itemsRaw instanceof String itemsStr && !itemsStr.isBlank()) {
            try {
                List<Map<String, Object>> items = objectMapper.readValue(itemsStr,
                        new TypeReference<List<Map<String, Object>>>() {});
                result.put("items", items);
            } catch (Exception e) {
                result.put("items", List.of());
            }
        } else {
            result.put("items", List.of());
        }

        return result;
    }

    private Map<String, Object> buildItem(String type, Map<String, Object> data) {
        Map<String, Object> cleanData = new LinkedHashMap<>(data);
        // Remove tenant_id from exported data
        cleanData.remove("tenant_id");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("data", cleanData);
        return item;
    }

    @SuppressWarnings("unchecked")
    private void checkExisting(String tenantId, List<Map<String, Object>> items,
                               List<Map<String, Object>> creates,
                               List<Map<String, Object>> updates,
                               List<Map<String, Object>> conflicts,
                               String uiType) {
        for (var item : items) {
            Map<String, Object> data = (Map<String, Object>) item.get("data");
            String itemName = (String) data.get("name");
            String itemPath = (String) data.get("path");

            boolean exists = false;
            switch (uiType) {
                case "collection" -> {
                    var existing = repository.findCollectionsByNames(tenantId, List.of(itemName));
                    exists = !existing.isEmpty();
                }
                case "role" -> {
                    var existing = repository.findRolesByNames(tenantId, List.of(itemName));
                    exists = !existing.isEmpty();
                }
                case "policy" -> {
                    var existing = repository.findPoliciesByNames(tenantId, List.of(itemName));
                    exists = !existing.isEmpty();
                }
                case "page" -> {
                    if (itemPath != null) {
                        var existing = repository.findUiPagesByPaths(tenantId, List.of(itemPath));
                        exists = !existing.isEmpty();
                    }
                }
                case "menu" -> {
                    var existing = repository.findUiMenusByNames(tenantId, List.of(itemName));
                    exists = !existing.isEmpty();
                }
            }

            Map<String, Object> packageItem = new LinkedHashMap<>();
            packageItem.put("type", uiType);
            packageItem.put("id", data.get("id"));
            packageItem.put("name", itemName != null ? itemName : itemPath);

            if (exists) {
                // Existing item = conflict (user decides skip/overwrite)
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("item", packageItem);
                conflict.put("existingItem", packageItem);
                conflicts.add(conflict);
            } else {
                creates.add(packageItem);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int importItems(String tenantId, List<Map<String, Object>> items,
                            String itemType, List<Map<String, Object>> errors) {
        int count = 0;
        for (var item : items) {
            Map<String, Object> data = (Map<String, Object>) item.get("data");
            try {
                importSingleItem(tenantId, itemType, data);
                count++;
            } catch (Exception e) {
                Map<String, Object> error = new LinkedHashMap<>();
                Map<String, Object> packageItem = new LinkedHashMap<>();
                packageItem.put("type", itemType);
                packageItem.put("id", data.get("id"));
                packageItem.put("name", data.get("name"));
                error.put("item", packageItem);
                error.put("message", e.getMessage());
                errors.add(error);
                log.warn("Failed to import {} item: {}", itemType, data.get("name"), e);
            }
        }
        return count;
    }

    private void importSingleItem(String tenantId, String itemType, Map<String, Object> data) {
        String id = UUID.randomUUID().toString();
        java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());

        switch (itemType) {
            case "collection" -> importCollection(tenantId, id, data, now);
            case "field" -> importField(tenantId, id, data, now);
            case "role" -> importRole(tenantId, id, data, now);
            case "policy" -> importPolicy(tenantId, id, data, now);
            case "page" -> importUiPage(tenantId, id, data, now);
            case "menu" -> importUiMenu(tenantId, id, data, now);
            case "menu_item" -> importUiMenuItem(tenantId, id, data, now);
            default -> log.debug("Skipping unsupported item type during import: {}", itemType);
        }
    }

    private void importCollection(String tenantId, String id, Map<String, Object> data, java.sql.Timestamp now) {
        repository.getJdbcTemplate().update(
                "INSERT INTO collection (id, tenant_id, name, description, active, path, system_collection, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, false, ?, ?)",
                id, tenantId, data.get("name"), data.get("description"),
                data.getOrDefault("active", true), data.get("path"), now, now
        );
    }

    private void importField(String tenantId, String id, Map<String, Object> data, java.sql.Timestamp now) {
        // Fields reference their collection by collection_id - the collection must already exist
        repository.getJdbcTemplate().update(
                "INSERT INTO field (id, collection_id, name, type, required, active, description, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, data.get("collection_id"), data.get("name"), data.get("type"),
                data.getOrDefault("required", false), data.getOrDefault("active", true),
                data.get("description"), now, now
        );
    }

    private void importRole(String tenantId, String id, Map<String, Object> data, java.sql.Timestamp now) {
        repository.getJdbcTemplate().update(
                "INSERT INTO role (id, tenant_id, name, description, created_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                id, tenantId, data.get("name"), data.get("description"), now
        );
    }

    private void importPolicy(String tenantId, String id, Map<String, Object> data, java.sql.Timestamp now) {
        String rulesJson = null;
        Object rules = data.get("rules");
        if (rules != null) {
            try {
                rulesJson = rules instanceof String ? (String) rules : objectMapper.writeValueAsString(rules);
            } catch (Exception e) {
                log.warn("Failed to serialize policy rules", e);
            }
        }
        repository.getJdbcTemplate().update(
                "INSERT INTO policy (id, tenant_id, name, description, rules, created_at) " +
                        "VALUES (?, ?, ?, ?, ?::jsonb, ?)",
                id, tenantId, data.get("name"), data.get("description"), rulesJson, now
        );
    }

    private void importUiPage(String tenantId, String id, Map<String, Object> data, java.sql.Timestamp now) {
        String configJson = null;
        Object config = data.get("config");
        if (config != null) {
            try {
                configJson = config instanceof String ? (String) config : objectMapper.writeValueAsString(config);
            } catch (Exception e) {
                log.warn("Failed to serialize UI page config", e);
            }
        }
        repository.getJdbcTemplate().update(
                "INSERT INTO ui_page (id, tenant_id, name, path, title, config, active, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                id, tenantId, data.get("name"), data.get("path"), data.get("title"),
                configJson, data.getOrDefault("active", true), now, now
        );
    }

    private void importUiMenu(String tenantId, String id, Map<String, Object> data, java.sql.Timestamp now) {
        repository.getJdbcTemplate().update(
                "INSERT INTO ui_menu (id, tenant_id, name, description, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                id, tenantId, data.get("name"), data.get("description"), now, now
        );
    }

    private void importUiMenuItem(String tenantId, String id, Map<String, Object> data, java.sql.Timestamp now) {
        repository.getJdbcTemplate().update(
                "INSERT INTO ui_menu_item (id, menu_id, tenant_id, label, path, icon, display_order, active, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, data.get("menu_id"), tenantId, data.get("label"), data.get("path"),
                data.get("icon"), data.getOrDefault("display_order", 0),
                data.getOrDefault("active", true), now
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof String)
                    .map(item -> (String) item)
                    .toList();
        }
        return List.of();
    }

    private String mapItemTypeToUiType(String type) {
        return switch (type) {
            case "COLLECTION" -> "collection";
            case "FIELD" -> "collection";
            case "ROLE" -> "role";
            case "POLICY", "ROUTE_POLICY", "FIELD_POLICY" -> "policy";
            case "UI_PAGE" -> "page";
            case "UI_MENU", "UI_MENU_ITEM" -> "menu";
            default -> type.toLowerCase();
        };
    }
}
