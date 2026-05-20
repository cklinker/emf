package io.kelta.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Builds the system prompt for Claude with platform domain knowledge.
 * Combines static knowledge about field types and schemas with dynamic
 * context about the tenant's current collections. For tenants with few
 * collections (≤ {@link #AUTO_INLINE_THRESHOLD}), inlines full field
 * tables to avoid round-trips on get_collection_schema.
 */
@Service
public class SystemPromptService {

    static final int AUTO_INLINE_THRESHOLD = 8;

    private static final Logger log = LoggerFactory.getLogger(SystemPromptService.class);

    private final WorkerApiClient workerApiClient;

    public SystemPromptService(WorkerApiClient workerApiClient) {
        this.workerApiClient = workerApiClient;
    }

    public String buildSystemPrompt(String tenantId, String contextType, String contextId) {
        StringBuilder sb = new StringBuilder();
        sb.append(getStaticPrompt());

        try {
            String tenantContext = buildTenantContext(tenantId, contextType, contextId);
            sb.append("\n\n").append(tenantContext);
        } catch (Exception e) {
            log.warn("Failed to build tenant context for system prompt: {}", e.getMessage());
        }

        return sb.toString();
    }

    private String getStaticPrompt() {
        return """
                You are an AI assistant for the Kelta platform, a multi-tenant data management system.
                You help users design and modify collections (data models), fields, picklists, and page layouts.

                ## Tool Usage Patterns

                You have READ tools to inspect the existing tenant — use them liberally before recommending changes:
                - `get_collection_schema(collectionName)` — fields, types, references on an existing collection
                - `query_records(collectionName, limit)` — sample 1–20 records to see actual data
                - `list_picklists()` / `get_picklist(name)` — picklist values
                - `list_validation_rules(collectionName)` — rules in effect
                - `list_page_layouts(collectionName)` — existing layouts

                You have PROPOSE tools that require user approval:
                - `propose_collection` — create a new collection with fields
                - `propose_layout` — create a page layout
                - `propose_add_fields` — add new fields to an EXISTING collection
                - `propose_update_field` — update a field's metadata (NOT its type)
                - `propose_remove_field` — destructive; remove a field. Include a reason.
                - `propose_picklist` — create a new global picklist

                Rules:
                1. Before recommending fields for an existing collection, ALWAYS call `get_collection_schema` first.
                2. If the user states "products has X" and you don't see X in context, call `get_collection_schema` to verify
                   rather than asking them to retype it.
                3. `propose_update_field` cannot change a field's type. To replace a field, use `propose_remove_field`
                   then `propose_add_fields`.
                4. Call read tools freely — they're internal. Propose tools surface a card the user must approve, so
                   explain what each proposal does when you call it.
                5. Never output raw JSON to the user. Always go through tools.

                ## Platform Built-in Features

                The Kelta platform already provides these features out of the box via system collections.
                Do NOT create collections for any of these — they already exist and are managed by the platform:

                - User Management, Roles & Permissions, Tenants, Schema Management
                - Page Layouts, Navigation, Picklists, Validation Rules, Record Types
                - Sharing & Security, Workflows, Scheduled Jobs, Email, Webhooks & Integrations
                - Analytics (embedded Superset), Audit Trail

                When a user asks for "user management" or "authentication", explain that the platform already handles this.
                Only create collections for the user's domain-specific data (products, orders, customers, etc.).

                If the user's domain needs to reference platform users (e.g., "assigned_to" on a task), use a STRING field
                to store the user ID — do not create a MASTER_DETAIL to the system user collection.

                Built-in record features (do NOT add these as fields):
                - Notes, Attachments, created/updated timestamps, created/updated by, record ID, active/inactive flag.

                ## Collection Rules

                - Collection names must match `^[a-z][a-z0-9_]*$`, max 50 chars
                - Every collection needs a meaningful `displayName` and a `displayFieldName`
                - Reserved names that will fail validation: users, roles, permissions, tenants, collections, fields,
                  profiles, ui_pages, ui_menus, ui_menu_items, page_layouts, layout_sections, layout_fields,
                  layout_assignments, global_picklists, picklist_values, validation_rules, record_types,
                  sharing_rules, workflows, approval_processes, platform_user, connected_apps, scheduled_jobs,
                  email_templates, email_logs, setup_audit_trail, webhook_endpoints, flow_definitions,
                  flow_executions, bulk_jobs

                ## Field Types

                Basic: STRING, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, DATETIME, JSON
                Picklist: PICKLIST, MULTI_PICKLIST (require `enumValues`)
                Specialized: CURRENCY, PERCENT, AUTO_NUMBER, PHONE, EMAIL, URL, RICH_TEXT, ENCRYPTED,
                EXTERNAL_ID, GEOLOCATION
                Relationship: MASTER_DETAIL (the only relationship type — requires `referenceConfig`
                with `targetCollection` and `relationshipName`)
                Computed: FORMULA, ROLLUP_SUMMARY

                Field properties: name, type, nullable, unique, defaultValue, validationRules, enumValues,
                referenceConfig, fieldTypeConfig.

                ## Page Layout Structure

                Layout types: DETAIL, EDIT, MINI, LIST. Each layout has sections; each section has heading,
                columns (1-3), style (DEFAULT/COLLAPSIBLE/CARD), fieldPlacements (fieldName, columnNumber,
                sortOrder). Layouts can have `relatedLists` for child records.

                ## Relational Design

                Normalize data into multiple collections (orders + order_lines, recipes + recipe_ingredients).
                Use MASTER_DETAIL on the child pointing to the parent. Avoid storing lists in a single field.
                When designing a multi-collection system, call `propose_collection` once per collection
                across iterations — you do NOT need to do it all in one response.

                ## Best Practices

                - Include a display field (usually `name` or `title`)
                - Use type-specific fields (EMAIL for emails, PHONE for phones)
                - PICKLIST for known value sets; include `enumValues`
                - Target collections must exist or be proposed first
                - Propose parents before children
                """;
    }

    @SuppressWarnings("unchecked")
    private String buildTenantContext(String tenantId, String contextType, String contextId) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Current Tenant Context\n\n");

        List<Map<String, Object>> collections = workerApiClient.listCollections(tenantId);

        if (collections.isEmpty()) {
            sb.append("This tenant has no collections yet. This is a fresh setup.\n");
        } else {
            sb.append("Existing collections in this tenant:\n\n");
            for (Map<String, Object> collection : collections) {
                Map<String, Object> attrs = (Map<String, Object>) collection.get("attributes");
                if (attrs == null) continue;
                sb.append("- **").append(attrs.getOrDefault("displayName", attrs.get("name")))
                        .append("** (`").append(attrs.get("name")).append("`)");
                Object desc = attrs.get("description");
                if (desc != null && !String.valueOf(desc).isBlank()) {
                    sb.append(": ").append(desc);
                }
                sb.append("\n");
            }

            if (collections.size() <= AUTO_INLINE_THRESHOLD) {
                appendInlineFieldTables(tenantId, collections, sb);
            } else {
                sb.append("\nTenant has ").append(collections.size())
                        .append(" collections — use `get_collection_schema` to inspect fields on demand.\n");
            }
        }

        if ("collection".equals(contextType) && contextId != null) {
            appendCurrentCollectionFields(tenantId, contextId, sb);
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendInlineFieldTables(String tenantId, List<Map<String, Object>> collections,
                                          StringBuilder sb) {
        sb.append("\n### Schemas (auto-loaded — tenant has few collections)\n");
        for (Map<String, Object> collection : collections) {
            Map<String, Object> attrs = (Map<String, Object>) collection.get("attributes");
            if (attrs == null) continue;
            String name = String.valueOf(attrs.get("name"));
            String collectionId = String.valueOf(collection.get("id"));

            sb.append("\n#### `").append(name).append("`\n\n");
            try {
                List<Map<String, Object>> fields = workerApiClient.listFields(tenantId, collectionId);
                appendFieldTable(fields, sb);
            } catch (Exception e) {
                log.warn("Failed to fetch fields for {}: {}", name, e.getMessage());
                sb.append("_(failed to load fields — use `get_collection_schema('").append(name).append("')`)_\n");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void appendCurrentCollectionFields(String tenantId, String contextId, StringBuilder sb) {
        sb.append("\n### Currently Viewing Collection\n\n");
        try {
            List<Map<String, Object>> fields = workerApiClient.listFields(tenantId, contextId);
            appendFieldTable(fields, sb);
        } catch (Exception e) {
            log.warn("Failed to fetch fields for collection {}: {}", contextId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void appendFieldTable(List<Map<String, Object>> fields, StringBuilder sb) {
        if (fields.isEmpty()) {
            sb.append("_(no fields yet)_\n");
            return;
        }
        sb.append("| Field | Type | Required | Notes |\n|-------|------|----------|-------|\n");
        for (Map<String, Object> field : fields) {
            Map<String, Object> attrs = (Map<String, Object>) field.get("attributes");
            if (attrs == null) continue;
            sb.append("| `").append(attrs.getOrDefault("name", ""))
                    .append("` | ").append(attrs.getOrDefault("type", ""))
                    .append(" | ").append(Boolean.TRUE.equals(attrs.get("required")) ? "Yes" : "No")
                    .append(" | ");
            Object refTarget = attrs.get("referenceTarget");
            if (refTarget != null) sb.append("→ ").append(refTarget);
            sb.append(" |\n");
        }
    }
}
