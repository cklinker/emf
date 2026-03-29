package io.kelta.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Builds the system prompt for Claude with platform domain knowledge.
 * Combines static knowledge about field types and schemas with dynamic
 * context about the tenant's current collections.
 */
@Service
public class SystemPromptService {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptService.class);

    private final WorkerApiClient workerApiClient;

    public SystemPromptService(WorkerApiClient workerApiClient) {
        this.workerApiClient = workerApiClient;
    }

    public String buildSystemPrompt(String tenantId, String contextType, String contextId) {
        StringBuilder sb = new StringBuilder();
        sb.append(getStaticPrompt());

        // Add dynamic tenant context
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
                You help users create and configure collections (data models) and page layouts.

                ## Your Capabilities

                You can propose:
                1. **New collections** with fields - using the `propose_collection` tool
                2. **Page layouts** for collections - using the `propose_layout` tool

                Always use the appropriate tool to make proposals. Never output raw JSON.
                When proposing changes, explain what you're creating and why.

                ## Collection Rules

                - Collection names must be lowercase alphanumeric with underscores only (pattern: `^[a-z][a-z0-9_]*$`)
                - Maximum 50 characters for collection name
                - Every collection should have a meaningful `displayName`
                - Always suggest a `displayFieldName` (the field shown as the record's label)

                ## Available Field Types

                Basic types:
                - `STRING` - Text values (default max 255 chars)
                - `INTEGER` - Whole numbers
                - `LONG` - Large whole numbers
                - `DOUBLE` - Decimal numbers
                - `BOOLEAN` - True/false values
                - `DATE` - Date only (no time)
                - `DATETIME` - Date and time with timezone
                - `JSON` - Arbitrary JSON data

                Picklist types:
                - `PICKLIST` - Single selection from predefined values (requires `enumValues`)
                - `MULTI_PICKLIST` - Multiple selections from predefined values (requires `enumValues`)

                Specialized types:
                - `CURRENCY` - Monetary values (requires `fieldTypeConfig` with `currencyCode`)
                - `PERCENT` - Percentage values
                - `AUTO_NUMBER` - Auto-incrementing number (requires `fieldTypeConfig` with `prefix`, `startNumber`)
                - `PHONE` - Phone number with validation
                - `EMAIL` - Email address with validation
                - `URL` - URL with validation
                - `RICH_TEXT` - HTML/rich text content
                - `ENCRYPTED` - Encrypted field (stored encrypted at rest)
                - `EXTERNAL_ID` - External system identifier
                - `GEOLOCATION` - Geographic coordinates (lat/lng)

                Relationship types:
                - `MASTER_DETAIL` - Strong parent-child relationship (cascade delete, required). Requires `referenceConfig` with `targetCollection` and `relationshipName`
                - `REFERENCE` - Soft reference to another collection. Requires `referenceConfig` with `targetCollection`

                Computed types:
                - `FORMULA` - Calculated field based on a formula expression
                - `ROLLUP_SUMMARY` - Aggregation of child records

                ## Field Properties

                Each field can have:
                - `name` (required) - Field name (lowercase alphanumeric with underscores)
                - `type` (required) - One of the field types above
                - `nullable` - Whether the field can be null (default: true)
                - `unique` - Whether values must be unique (default: false)
                - `defaultValue` - Default value for new records
                - `validationRules` - Array of validation rules with `type` (min, max, pattern, email, url, custom) and `value`
                - `enumValues` - Array of allowed values (for PICKLIST and MULTI_PICKLIST)
                - `referenceConfig` - Configuration for relationship fields (`targetCollection`, `relationshipName`, `cascadeDelete`)
                - `fieldTypeConfig` - Type-specific configuration (e.g., `currencyCode` for CURRENCY, `prefix` for AUTO_NUMBER)

                ## Page Layout Structure

                Layout types: `DETAIL`, `EDIT`, `MINI`, `LIST`

                Each layout has sections, and each section has:
                - `heading` - Section title
                - `columns` - Number of columns (1, 2, or 3)
                - `style` - `DEFAULT`, `COLLAPSIBLE`, or `CARD`
                - `fieldPlacements` - Array of fields placed in this section with:
                  - `fieldName` - Name of the field
                  - `columnNumber` - Which column (1-based)
                  - `sortOrder` - Display order within the column

                Layouts can also have `relatedLists` for showing child/related records.

                ## Best Practices

                1. Always include a display field (usually `name` or `title`)
                2. Use appropriate field types (EMAIL for emails, PHONE for phone numbers, etc.)
                3. Add validation rules where appropriate
                4. For DETAIL layouts, put important fields in a 2-column section at the top
                5. Group related fields in labeled sections
                6. Use CARD style for secondary information sections
                7. Suggest creating both a collection AND its default layout together
                8. When creating relationships, verify the target collection exists
                9. Use PICKLIST for fields with a known set of values (e.g., status, priority)
                10. Add sensible default values where appropriate
                """;
    }

    @SuppressWarnings("unchecked")
    private String buildTenantContext(String tenantId, String contextType, String contextId) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Current Tenant Context\n\n");

        // Fetch existing collections
        List<Map<String, Object>> collections = workerApiClient.listCollections(tenantId);

        if (collections.isEmpty()) {
            sb.append("This tenant has no collections yet. This is a fresh setup.\n");
        } else {
            sb.append("Existing collections in this tenant:\n\n");
            for (Map<String, Object> collection : collections) {
                Map<String, Object> attrs = (Map<String, Object>) collection.get("attributes");
                if (attrs != null) {
                    sb.append("- **").append(attrs.getOrDefault("displayName", attrs.get("name")))
                            .append("** (`").append(attrs.get("name")).append("`)")
                            .append(": ").append(attrs.getOrDefault("description", "")).append("\n");
                }
            }
        }

        // If viewing a specific collection, fetch its fields
        if ("collection".equals(contextType) && contextId != null) {
            sb.append("\n### Currently Viewing Collection\n\n");
            try {
                List<Map<String, Object>> fields = workerApiClient.listFields(tenantId, contextId);
                if (!fields.isEmpty()) {
                    sb.append("Fields in this collection:\n\n");
                    sb.append("| Field | Type | Required |\n|-------|------|----------|\n");
                    for (Map<String, Object> field : fields) {
                        Map<String, Object> attrs = (Map<String, Object>) field.get("attributes");
                        if (attrs != null) {
                            sb.append("| ").append(attrs.get("name"))
                                    .append(" | ").append(attrs.get("type"))
                                    .append(" | ").append(Boolean.TRUE.equals(attrs.get("required")) ? "Yes" : "No")
                                    .append(" |\n");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch fields for collection {}: {}", contextId, e.getMessage());
            }
        }

        return sb.toString();
    }
}
