package com.emf.runtime.model.system;

import com.emf.runtime.model.*;

import java.util.*;

/**
 * Registry of all system collection definitions.
 *
 * <p>System collections map to existing JPA tables managed by Flyway migrations.
 * They are served by the worker's DynamicCollectionRouter, just like user-defined collections,
 * but their tables are NOT created by the worker (Flyway manages them).
 *
 * <p>Each definition specifies:
 * <ul>
 *   <li>Collection name (used in API paths: /api/{name})</li>
 *   <li>Physical table name (existing Flyway-managed table)</li>
 *   <li>Field definitions with column mappings (API name -> DB column)</li>
 *   <li>Relationship types (LOOKUP/MASTER_DETAIL) for ?include= resolution</li>
 *   <li>Whether the collection is tenant-scoped</li>
 *   <li>Whether the collection is read-only (audit logs, history)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class SystemCollectionDefinitions {

    /**
     * The system tenant ID used for system collection metadata.
     */
    public static final String SYSTEM_TENANT_ID = "00000000-0000-0000-0000-000000000001";

    private SystemCollectionDefinitions() {
        // Utility class
    }

    /**
     * Returns all system collection definitions.
     *
     * @return unmodifiable list of all system collection definitions
     */
    public static List<CollectionDefinition> all() {
        List<CollectionDefinition> definitions = new ArrayList<>();

        // Core entity collections
        definitions.add(tenants());
        definitions.add(users());
        definitions.add(profiles());
        definitions.add(permissionSets());
        definitions.add(collections());
        definitions.add(fields());

        // Groups & Membership
        definitions.add(userGroups());
        definitions.add(groupMemberships());

        // Permission tables (profile-based)
        definitions.add(profileSystemPermissions());
        definitions.add(profileObjectPermissions());
        definitions.add(profileFieldPermissions());

        // Permission tables (permission-set-based)
        definitions.add(permsetSystemPermissions());
        definitions.add(permsetObjectPermissions());
        definitions.add(permsetFieldPermissions());

        // Permission assignments
        definitions.add(userPermissionSets());
        definitions.add(groupPermissionSets());

        // UI & Layout
        definitions.add(pageLayouts());
        definitions.add(layoutSections());
        definitions.add(layoutFields());
        definitions.add(layoutRelatedLists());
        definitions.add(layoutAssignments());
        definitions.add(listViews());
        definitions.add(uiPages());
        definitions.add(uiMenus());
        definitions.add(uiMenuItems());

        // Picklists
        definitions.add(globalPicklists());
        definitions.add(picklistValues());
        definitions.add(picklistDependencies());

        // Record types & Validation
        definitions.add(recordTypes());
        definitions.add(recordTypePicklists());
        definitions.add(validationRules());

        // Workflows & Automation
        definitions.add(workflowRules());
        definitions.add(workflowActions());
        definitions.add(workflowActionTypes());
        definitions.add(workflowPendingActions());
        definitions.add(scripts());
        definitions.add(scriptTriggers());
        definitions.add(flows());
        definitions.add(approvalProcesses());
        definitions.add(approvalSteps());
        definitions.add(approvalInstances());
        definitions.add(approvalStepInstances());
        definitions.add(scheduledJobs());

        // Communication
        definitions.add(emailTemplates());
        definitions.add(webhooks());

        // Integration
        definitions.add(connectedApps());
        definitions.add(connectedAppTokens());
        definitions.add(oidcProviders());

        // Reports & Dashboards
        definitions.add(reports());
        definitions.add(reportFolders());
        definitions.add(dashboards());
        definitions.add(dashboardComponents());

        // Collaboration
        definitions.add(notes());
        definitions.add(attachments());

        // Platform management
        definitions.add(bulkJobs());
        definitions.add(packages());
        definitions.add(packageItems());
        definitions.add(migrationRuns());

        // Read-only audit/log collections
        definitions.add(securityAuditLogs());
        definitions.add(setupAuditEntries());
        definitions.add(fieldHistory());
        definitions.add(workflowExecutionLogs());
        definitions.add(workflowActionLogs());
        definitions.add(workflowRuleVersions());
        definitions.add(scriptExecutionLogs());
        definitions.add(flowExecutions());
        definitions.add(jobExecutionLogs());
        definitions.add(bulkJobResults());
        definitions.add(emailLogs());
        definitions.add(webhookDeliveries());
        definitions.add(loginHistory());
        definitions.add(collectionVersions());
        definitions.add(fieldVersions());
        definitions.add(migrationSteps());

        return Collections.unmodifiableList(definitions);
    }

    /**
     * Returns a map of collection name to definition for quick lookup.
     */
    public static Map<String, CollectionDefinition> byName() {
        Map<String, CollectionDefinition> map = new LinkedHashMap<>();
        for (CollectionDefinition def : all()) {
            map.put(def.name(), def);
        }
        return Collections.unmodifiableMap(map);
    }

    // =========================================================================
    // Core Entity Collections
    // =========================================================================

    public static CollectionDefinition tenants() {
        return systemBuilder("tenants", "Tenants", "tenant")
            .tenantScoped(false)
            .displayFieldName("slug")
            .addField(FieldDefinition.requiredString("slug", 63)
                .withUnique(true)
                .withValidation(ValidationRules.forString(null, 63, "^[a-z][a-z0-9-]{1,61}[a-z0-9]$")))
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.requiredString("edition", 20)
                .withDefault("PROFESSIONAL")
                .withEnumValues(List.of("FREE", "PROFESSIONAL", "ENTERPRISE", "UNLIMITED")))
            .addField(FieldDefinition.requiredString("status", 20)
                .withDefault("PROVISIONING")
                .withEnumValues(List.of("PROVISIONING", "ACTIVE", "SUSPENDED", "DECOMMISSIONED")))
            .addField(FieldDefinition.json("settings"))
            .addField(FieldDefinition.json("limits"))
            .build();
    }

    public static CollectionDefinition users() {
        return systemBuilder("users", "Users", "platform_user")
            .displayFieldName("email")
            .addImmutableField("tenantId")
            .addField(FieldDefinition.requiredString("email", 320))
            .addField(FieldDefinition.string("username", 100))
            .addField(FieldDefinition.string("firstName", 100).withColumnName("first_name"))
            .addField(FieldDefinition.string("lastName", 100).withColumnName("last_name"))
            .addField(FieldDefinition.requiredString("status", 20)
                .withDefault("ACTIVE")
                .withEnumValues(List.of("ACTIVE", "INACTIVE", "LOCKED", "PENDING_ACTIVATION")))
            .addField(FieldDefinition.string("locale", 10).withDefault("en_US"))
            .addField(FieldDefinition.string("timezone", 50).withDefault("UTC"))
            .addField(FieldDefinition.lookup("profileId", "profiles", "Profile")
                .withColumnName("profile_id"))
            .addField(FieldDefinition.lookup("managerId", "users", "Manager")
                .withColumnName("manager_id"))
            .addField(FieldDefinition.datetime("lastLoginAt").withColumnName("last_login_at"))
            .addField(FieldDefinition.integer("loginCount").withColumnName("login_count").withDefault(0))
            .addField(FieldDefinition.bool("mfaEnabled").withColumnName("mfa_enabled").withDefault(false))
            .addField(FieldDefinition.json("settings"))
            .build();
    }

    public static CollectionDefinition profiles() {
        return systemBuilder("profiles", "Profiles", "profile")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 255))
            .addField(FieldDefinition.text("description"))
            .addField(FieldDefinition.bool("isSystem").withColumnName("is_system").withDefault(false))
            .build();
    }

    public static CollectionDefinition permissionSets() {
        return systemBuilder("permission-sets", "Permission Sets", "permission_set")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 255))
            .addField(FieldDefinition.text("description"))
            .addField(FieldDefinition.bool("isSystem").withColumnName("is_system").withDefault(false))
            .build();
    }

    public static CollectionDefinition collections() {
        return systemBuilder("collections", "Collections", "collection")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 100).withUnique(true))
            .addField(FieldDefinition.string("displayName", 100).withColumnName("display_name"))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.string("path", 255))
            .addField(FieldDefinition.string("storageMode", 50).withColumnName("storage_mode")
                .withDefault("PHYSICAL_TABLES"))
            .addField(FieldDefinition.bool("active").withDefault(true))
            .addField(FieldDefinition.bool("systemCollection").withColumnName("system_collection")
                .withDefault(false))
            .addField(FieldDefinition.requiredInteger("currentVersion").withColumnName("current_version")
                .withDefault(1))
            .addField(FieldDefinition.lookup("displayFieldId", "fields", "Display Field")
                .withColumnName("display_field_id"))
            .build();
    }

    public static CollectionDefinition fields() {
        return systemBuilder("fields", "Fields", "field")
            .displayFieldName("name")
            .tenantScoped(false)
            .addImmutableField("collectionId")
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("displayName", 100).withColumnName("display_name"))
            .addField(FieldDefinition.requiredString("type", 50))
            .addField(FieldDefinition.bool("required").withDefault(false))
            .addField(FieldDefinition.bool("uniqueConstraint").withColumnName("unique_constraint")
                .withDefault(false))
            .addField(FieldDefinition.bool("indexed").withDefault(false))
            .addField(FieldDefinition.json("defaultValue").withColumnName("default_value"))
            .addField(FieldDefinition.string("referenceTarget", 100).withColumnName("reference_target"))
            .addField(FieldDefinition.integer("fieldOrder").withColumnName("field_order"))
            .addField(FieldDefinition.bool("active").withDefault(true))
            .addField(FieldDefinition.json("constraints"))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.json("fieldTypeConfig").withColumnName("field_type_config"))
            .addField(FieldDefinition.string("autoNumberSequenceName", 100)
                .withColumnName("auto_number_sequence_name"))
            .addField(FieldDefinition.string("relationshipType", 20).withColumnName("relationship_type"))
            .addField(FieldDefinition.string("relationshipName", 100).withColumnName("relationship_name"))
            .addField(FieldDefinition.bool("cascadeDelete").withColumnName("cascade_delete")
                .withDefault(false))
            .addField(FieldDefinition.lookup("referenceCollectionId", "collections", "Reference Collection")
                .withColumnName("reference_collection_id"))
            .addField(FieldDefinition.bool("trackHistory").withColumnName("track_history")
                .withDefault(false))
            .build();
    }

    // =========================================================================
    // UI & Layout Collections
    // =========================================================================

    public static CollectionDefinition pageLayouts() {
        return systemBuilder("page-layouts", "Page Layouts", "page_layout")
            .displayFieldName("name")
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.string("layoutType", 20).withColumnName("layout_type")
                .withDefault("DETAIL"))
            .addField(FieldDefinition.bool("isDefault").withColumnName("is_default")
                .withDefault(false))
            .build();
    }

    public static CollectionDefinition layoutAssignments() {
        return systemBuilder("layout-assignments", "Layout Assignments", "layout_assignment")
            .tenantScoped(true)
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("profileId", 36).withColumnName("profile_id"))
            .addField(FieldDefinition.string("recordTypeId", 36).withColumnName("record_type_id"))
            .addField(FieldDefinition.masterDetail("layoutId", "page-layouts", "Layout")
                .withColumnName("layout_id"))
            .build();
    }

    public static CollectionDefinition listViews() {
        return systemBuilder("list-views", "List Views", "list_view")
            .displayFieldName("name")
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("visibility", 20).withDefault("PRIVATE"))
            .addField(FieldDefinition.bool("isDefault").withColumnName("is_default"))
            .addField(FieldDefinition.requiredJson("columns"))
            .addField(FieldDefinition.string("filterLogic").withColumnName("filter_logic"))
            .addField(FieldDefinition.json("filters").withDefault("[]"))
            .addField(FieldDefinition.string("sortField").withColumnName("sort_field"))
            .addField(FieldDefinition.string("sortDirection", 4).withColumnName("sort_direction")
                .withDefault("ASC"))
            .addField(FieldDefinition.integer("rowLimit").withColumnName("row_limit")
                .withDefault(50))
            .addField(FieldDefinition.json("chartConfig").withColumnName("chart_config"))
            .build();
    }

    public static CollectionDefinition uiPages() {
        return systemBuilder("ui-pages", "UI Pages", "ui_page")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.requiredString("path", 200))
            .addField(FieldDefinition.string("title", 200))
            .addField(FieldDefinition.json("config"))
            .addField(FieldDefinition.bool("active").withDefault(true).withNullable(false))
            .build();
    }

    public static CollectionDefinition uiMenus() {
        return systemBuilder("ui-menus", "UI Menus", "ui_menu")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.integer("displayOrder").withColumnName("display_order")
                .withDefault(0))
            .build();
    }

    // =========================================================================
    // Picklist Collections
    // =========================================================================

    public static CollectionDefinition globalPicklists() {
        return systemBuilder("global-picklists", "Global Picklists", "global_picklist")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.bool("sorted").withDefault(false))
            .addField(FieldDefinition.bool("restricted").withDefault(true))
            .build();
    }

    public static CollectionDefinition picklistValues() {
        return systemBuilder("picklist-values", "Picklist Values", "picklist_value")
            .displayFieldName("label")
            .tenantScoped(false)
            .addField(FieldDefinition.requiredString("picklistSourceType", 20)
                .withColumnName("picklist_source_type")
                .withEnumValues(List.of("FIELD", "GLOBAL")))
            .addField(FieldDefinition.requiredString("picklistSourceId", 36)
                .withColumnName("picklist_source_id"))
            .addField(FieldDefinition.requiredString("value", 255))
            .addField(FieldDefinition.requiredString("label", 255))
            .addField(FieldDefinition.bool("isDefault").withColumnName("is_default"))
            .addField(FieldDefinition.bool("isActive").withColumnName("is_active"))
            .addField(FieldDefinition.integer("sortOrder").withColumnName("sort_order")
                .withDefault(0))
            .addField(FieldDefinition.string("color", 20))
            .addField(FieldDefinition.string("description", 500))
            .build();
    }

    // =========================================================================
    // Record Types & Validation
    // =========================================================================

    public static CollectionDefinition recordTypes() {
        return systemBuilder("record-types", "Record Types", "record_type")
            .displayFieldName("name")
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.bool("isActive").withColumnName("is_active")
                .withDefault(true))
            .addField(FieldDefinition.bool("isDefault").withColumnName("is_default")
                .withDefault(false))
            .build();
    }

    public static CollectionDefinition validationRules() {
        return systemBuilder("validation-rules", "Validation Rules", "validation_rule")
            .displayFieldName("name")
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.requiredString("errorConditionFormula")
                .withColumnName("error_condition_formula"))
            .addField(FieldDefinition.requiredString("errorMessage", 1000)
                .withColumnName("error_message"))
            .addField(FieldDefinition.string("errorField", 100).withColumnName("error_field"))
            .addField(FieldDefinition.requiredString("evaluateOn", 20)
                .withColumnName("evaluate_on")
                .withDefault("CREATE_AND_UPDATE")
                .withEnumValues(List.of("CREATE", "UPDATE", "CREATE_AND_UPDATE")))
            .build();
    }

    // =========================================================================
    // Workflow & Automation Collections
    // =========================================================================

    public static CollectionDefinition workflowRules() {
        return systemBuilder("workflow-rules", "Workflow Rules", "workflow_rule")
            .displayFieldName("name")
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 1000))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.requiredString("triggerType", 30).withColumnName("trigger_type")
                .withEnumValues(List.of("ON_CREATE", "ON_UPDATE", "ON_DELETE",
                    "ON_CREATE_OR_UPDATE", "SCHEDULED", "MANUAL",
                    "BEFORE_CREATE", "BEFORE_UPDATE")))
            .addField(FieldDefinition.string("filterFormula").withColumnName("filter_formula"))
            .addField(FieldDefinition.bool("reEvaluateOnUpdate")
                .withColumnName("re_evaluate_on_update"))
            .addField(FieldDefinition.integer("executionOrder").withColumnName("execution_order")
                .withDefault(0))
            .addField(FieldDefinition.string("errorHandling", 30).withColumnName("error_handling")
                .withDefault("STOP_ON_ERROR"))
            .addField(FieldDefinition.json("triggerFields").withColumnName("trigger_fields"))
            .addField(FieldDefinition.string("cronExpression", 100)
                .withColumnName("cron_expression"))
            .addField(FieldDefinition.string("timezone", 50))
            .addField(FieldDefinition.datetime("lastScheduledRun")
                .withColumnName("last_scheduled_run"))
            .addField(FieldDefinition.requiredString("executionMode", 20)
                .withColumnName("execution_mode")
                .withDefault("SEQUENTIAL"))
            .build();
    }

    public static CollectionDefinition workflowActionTypes() {
        return systemBuilder("workflow-action-types", "Workflow Action Types", "workflow_action_type")
            .displayFieldName("key")
            .tenantScoped(false)
            .addField(FieldDefinition.requiredString("key", 50).withUnique(true))
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.requiredString("category", 50))
            .addField(FieldDefinition.json("configSchema").withColumnName("config_schema"))
            .addField(FieldDefinition.string("icon", 50))
            .addField(FieldDefinition.requiredString("handlerClass", 255)
                .withColumnName("handler_class"))
            .addField(FieldDefinition.bool("active").withDefault(true))
            .addField(FieldDefinition.bool("builtIn").withColumnName("built_in")
                .withDefault(true))
            .build();
    }

    public static CollectionDefinition scripts() {
        return systemBuilder("scripts", "Scripts", "script")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.requiredString("scriptType", 30).withColumnName("script_type")
                .withEnumValues(List.of("BEFORE_TRIGGER", "AFTER_TRIGGER", "SCHEDULED",
                    "API_ENDPOINT", "VALIDATION", "EVENT_HANDLER", "EMAIL_HANDLER")))
            .addField(FieldDefinition.requiredString("language", 20).withDefault("javascript"))
            .addField(FieldDefinition.requiredText("sourceCode").withColumnName("source_code"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.integer("version"))
            .build();
    }

    public static CollectionDefinition flows() {
        return systemBuilder("flows", "Flows", "flow")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 1000))
            .addField(FieldDefinition.requiredString("flowType", 30).withColumnName("flow_type")
                .withEnumValues(List.of("RECORD_TRIGGERED", "SCHEDULED",
                    "AUTOLAUNCHED", "SCREEN")))
            .addField(FieldDefinition.bool("active").withDefault(false))
            .addField(FieldDefinition.integer("version").withDefault(1))
            .addField(FieldDefinition.json("triggerConfig").withColumnName("trigger_config"))
            .addField(FieldDefinition.requiredJson("definition"))
            .build();
    }

    public static CollectionDefinition approvalProcesses() {
        return systemBuilder("approval-processes", "Approval Processes", "approval_process")
            .displayFieldName("name")
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 1000))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.string("entryCriteria").withColumnName("entry_criteria"))
            .addField(FieldDefinition.string("recordEditability", 20)
                .withColumnName("record_editability").withDefault("LOCKED"))
            .addField(FieldDefinition.string("initialSubmitterField", 100)
                .withColumnName("initial_submitter_field"))
            .addField(FieldDefinition.json("onSubmitFieldUpdates")
                .withColumnName("on_submit_field_updates"))
            .addField(FieldDefinition.json("onApprovalFieldUpdates")
                .withColumnName("on_approval_field_updates"))
            .addField(FieldDefinition.json("onRejectionFieldUpdates")
                .withColumnName("on_rejection_field_updates"))
            .addField(FieldDefinition.json("onRecallFieldUpdates")
                .withColumnName("on_recall_field_updates"))
            .addField(FieldDefinition.bool("allowRecall").withColumnName("allow_recall")
                .withDefault(true))
            .addField(FieldDefinition.integer("executionOrder").withColumnName("execution_order")
                .withDefault(0))
            .build();
    }

    public static CollectionDefinition scheduledJobs() {
        return systemBuilder("scheduled-jobs", "Scheduled Jobs", "scheduled_job")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.requiredString("jobType", 20).withColumnName("job_type")
                .withEnumValues(List.of("FLOW", "SCRIPT", "REPORT_EXPORT")))
            .addField(FieldDefinition.string("jobReferenceId", 36)
                .withColumnName("job_reference_id"))
            .addField(FieldDefinition.requiredString("cronExpression", 100)
                .withColumnName("cron_expression"))
            .addField(FieldDefinition.string("timezone", 50).withDefault("UTC"))
            .addField(FieldDefinition.bool("active").withDefault(true))
            .addField(FieldDefinition.datetime("lastRunAt").withColumnName("last_run_at"))
            .addField(FieldDefinition.string("lastStatus", 20).withColumnName("last_status"))
            .addField(FieldDefinition.datetime("nextRunAt").withColumnName("next_run_at"))
            .build();
    }

    // =========================================================================
    // Communication Collections
    // =========================================================================

    public static CollectionDefinition emailTemplates() {
        return systemBuilder("email-templates", "Email Templates", "email_template")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.requiredString("subject", 500))
            .addField(FieldDefinition.requiredText("bodyHtml").withColumnName("body_html"))
            .addField(FieldDefinition.text("bodyText").withColumnName("body_text"))
            .addField(FieldDefinition.string("relatedCollectionId")
                .withColumnName("related_collection_id"))
            .addField(FieldDefinition.string("folder"))
            .addField(FieldDefinition.bool("isActive").withColumnName("is_active"))
            .build();
    }

    public static CollectionDefinition webhooks() {
        return systemBuilder("webhooks", "Webhooks", "webhook")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.requiredString("url", 2048))
            .addField(FieldDefinition.requiredJson("events"))
            .addField(FieldDefinition.string("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.string("filterFormula").withColumnName("filter_formula"))
            .addField(FieldDefinition.json("headers"))
            .addField(FieldDefinition.string("secret", 200))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.json("retryPolicy").withColumnName("retry_policy"))
            .build();
    }

    // =========================================================================
    // Integration Collections
    // =========================================================================

    public static CollectionDefinition connectedApps() {
        return systemBuilder("connected-apps", "Connected Apps", "connected_app")
            .displayFieldName("name")
            .addImmutableField("clientId")
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.requiredString("clientId", 100)
                .withColumnName("client_id").withUnique(true))
            .addField(FieldDefinition.requiredString("clientSecretHash", 200)
                .withColumnName("client_secret_hash").withImmutable(true))
            .addField(FieldDefinition.json("redirectUris").withColumnName("redirect_uris"))
            .addField(FieldDefinition.json("scopes"))
            .addField(FieldDefinition.json("ipRestrictions").withColumnName("ip_restrictions"))
            .addField(FieldDefinition.integer("rateLimitPerHour")
                .withColumnName("rate_limit_per_hour").withDefault(10000))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.datetime("lastUsedAt").withColumnName("last_used_at"))
            .build();
    }

    public static CollectionDefinition oidcProviders() {
        return systemBuilder("oidc-providers", "OIDC Providers", "oidc_provider")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.requiredString("issuer", 500))
            .addField(FieldDefinition.requiredString("jwksUri", 500)
                .withColumnName("jwks_uri"))
            .addField(FieldDefinition.bool("active").withDefault(true))
            .addField(FieldDefinition.string("clientId", 200).withColumnName("client_id"))
            .addField(FieldDefinition.string("audience", 200))
            .addField(FieldDefinition.string("rolesClaim", 200)
                .withColumnName("roles_claim"))
            .addField(FieldDefinition.string("rolesMapping", 200)
                .withColumnName("roles_mapping"))
            .addField(FieldDefinition.string("emailClaim", 200)
                .withColumnName("email_claim").withDefault("email"))
            .addField(FieldDefinition.string("usernameClaim", 200)
                .withColumnName("username_claim").withDefault("preferred_username"))
            .addField(FieldDefinition.string("nameClaim", 200)
                .withColumnName("name_claim").withDefault("name"))
            .build();
    }

    // =========================================================================
    // Reports & Dashboards
    // =========================================================================

    public static CollectionDefinition reports() {
        return systemBuilder("reports", "Reports", "report")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 1000))
            .addField(FieldDefinition.requiredString("reportType", 20)
                .withColumnName("report_type"))
            .addField(FieldDefinition.masterDetail("primaryCollectionId", "collections",
                "Primary Collection").withColumnName("primary_collection_id"))
            .addField(FieldDefinition.json("relatedJoins").withColumnName("related_joins"))
            .addField(FieldDefinition.requiredJson("columns"))
            .addField(FieldDefinition.json("filters"))
            .addField(FieldDefinition.string("filterLogic", 500)
                .withColumnName("filter_logic"))
            .addField(FieldDefinition.json("rowGroupings").withColumnName("row_groupings"))
            .addField(FieldDefinition.json("columnGroupings")
                .withColumnName("column_groupings"))
            .addField(FieldDefinition.json("sortOrder").withColumnName("sort_order"))
            .addField(FieldDefinition.string("chartType", 20).withColumnName("chart_type"))
            .addField(FieldDefinition.json("chartConfig").withColumnName("chart_config"))
            .addField(FieldDefinition.string("scope", 20).withDefault("MY_RECORDS"))
            .addField(FieldDefinition.lookup("folderId", "report-folders", "Folder")
                .withColumnName("folder_id"))
            .addField(FieldDefinition.string("accessLevel", 20)
                .withColumnName("access_level").withDefault("PRIVATE"))
            .build();
    }

    public static CollectionDefinition reportFolders() {
        return systemBuilder("report-folders", "Report Folders", "report_folder")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("accessLevel", 20)
                .withColumnName("access_level").withDefault("PRIVATE"))
            .build();
    }

    public static CollectionDefinition dashboards() {
        return systemBuilder("dashboards", "Dashboards", "dashboard")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 1000))
            .addField(FieldDefinition.lookup("folderId", "report-folders", "Folder")
                .withColumnName("folder_id"))
            .addField(FieldDefinition.string("accessLevel", 20)
                .withColumnName("access_level"))
            .addField(FieldDefinition.bool("dynamic").withColumnName("is_dynamic"))
            .addField(FieldDefinition.string("runningUserId", 36)
                .withColumnName("running_user_id"))
            .addField(FieldDefinition.integer("columnCount").withColumnName("column_count"))
            .build();
    }

    // =========================================================================
    // Collaboration Collections
    // =========================================================================

    public static CollectionDefinition notes() {
        return systemBuilder("notes", "Notes", "note")
            .addImmutableField("collectionId")
            .addImmutableField("recordId")
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("recordId").withColumnName("record_id"))
            .addField(FieldDefinition.requiredText("content"))
            .build();
    }

    public static CollectionDefinition attachments() {
        return systemBuilder("attachments", "Attachments", "file_attachment")
            .displayFieldName("fileName")
            .addImmutableField("collectionId")
            .addImmutableField("recordId")
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("recordId").withColumnName("record_id"))
            .addField(FieldDefinition.requiredString("fileName", 500)
                .withColumnName("file_name"))
            .addField(FieldDefinition.longField("fileSize").withColumnName("file_size"))
            .addField(FieldDefinition.requiredString("contentType", 200)
                .withColumnName("content_type"))
            .addField(FieldDefinition.string("storageKey", 500)
                .withColumnName("storage_key"))
            .addField(FieldDefinition.requiredString("uploadedBy", 320)
                .withColumnName("uploaded_by"))
            .addField(FieldDefinition.datetime("uploadedAt").withColumnName("uploaded_at"))
            .build();
    }

    // =========================================================================
    // Platform Management Collections
    // =========================================================================

    public static CollectionDefinition bulkJobs() {
        return systemBuilder("bulk-jobs", "Bulk Jobs", "bulk_job")
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("operation", 20))
            .addField(FieldDefinition.requiredString("status", 20).withDefault("QUEUED"))
            .addField(FieldDefinition.integer("totalRecords").withColumnName("total_records"))
            .addField(FieldDefinition.integer("processedRecords")
                .withColumnName("processed_records"))
            .addField(FieldDefinition.integer("successRecords")
                .withColumnName("success_records"))
            .addField(FieldDefinition.integer("errorRecords")
                .withColumnName("error_records"))
            .addField(FieldDefinition.string("externalIdField")
                .withColumnName("external_id_field"))
            .addField(FieldDefinition.string("contentType", 50)
                .withColumnName("content_type").withDefault("application/json"))
            .addField(FieldDefinition.integer("batchSize").withColumnName("batch_size")
                .withDefault(200))
            .addField(FieldDefinition.datetime("startedAt").withColumnName("started_at"))
            .addField(FieldDefinition.datetime("completedAt").withColumnName("completed_at"))
            .build();
    }

    public static CollectionDefinition packages() {
        return systemBuilder("packages", "Packages", "package")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.requiredString("version", 50))
            .addField(FieldDefinition.string("description"))
            .build();
    }

    public static CollectionDefinition packageItems() {
        return systemBuilder("package-items", "Package Items", "package_item")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("packageId", "packages", "Package")
                .withColumnName("package_id"))
            .addField(FieldDefinition.requiredString("itemType", 50)
                .withColumnName("item_type")
                .withEnumValues(List.of("COLLECTION", "FIELD", "ROLE", "POLICY",
                    "ROUTE_POLICY", "FIELD_POLICY", "OIDC_PROVIDER",
                    "UI_PAGE", "UI_MENU", "UI_MENU_ITEM")))
            .addField(FieldDefinition.requiredString("itemId", 36)
                .withColumnName("item_id"))
            .addField(FieldDefinition.json("content"))
            .build();
    }

    public static CollectionDefinition migrationRuns() {
        return systemBuilder("migration-runs", "Migration Runs", "migration_run")
            .addField(FieldDefinition.requiredString("collectionId", 36)
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredInteger("fromVersion")
                .withColumnName("from_version"))
            .addField(FieldDefinition.requiredInteger("toVersion")
                .withColumnName("to_version"))
            .addField(FieldDefinition.requiredString("status", 50))
            .addField(FieldDefinition.string("errorMessage", 2000)
                .withColumnName("error_message"))
            .build();
    }

    // =========================================================================
    // Read-Only Audit/Log Collections
    // =========================================================================

    public static CollectionDefinition securityAuditLogs() {
        return readOnlySystemBuilder("security-audit-logs", "Security Audit Logs", "security_audit_log")
            .addField(FieldDefinition.requiredString("eventType", 50)
                .withColumnName("event_type"))
            .addField(FieldDefinition.requiredString("eventCategory", 30)
                .withColumnName("event_category"))
            .addField(FieldDefinition.string("actorUserId", 36)
                .withColumnName("actor_user_id"))
            .addField(FieldDefinition.string("actorEmail", 320)
                .withColumnName("actor_email"))
            .addField(FieldDefinition.string("targetType", 50)
                .withColumnName("target_type"))
            .addField(FieldDefinition.string("targetId", 36)
                .withColumnName("target_id"))
            .addField(FieldDefinition.string("targetName", 255)
                .withColumnName("target_name"))
            .addField(FieldDefinition.json("details"))
            .addField(FieldDefinition.string("ipAddress", 45)
                .withColumnName("ip_address"))
            .addField(FieldDefinition.text("userAgent").withColumnName("user_agent"))
            .addField(FieldDefinition.string("correlationId", 36)
                .withColumnName("correlation_id"))
            .build();
    }

    public static CollectionDefinition setupAuditEntries() {
        return readOnlySystemBuilder("setup-audit-entries", "Setup Audit Entries", "setup_audit_trail")
            .addField(FieldDefinition.requiredString("userId", 36)
                .withColumnName("user_id"))
            .addField(FieldDefinition.requiredString("action", 50))
            .addField(FieldDefinition.requiredString("section", 100))
            .addField(FieldDefinition.requiredString("entityType", 50)
                .withColumnName("entity_type"))
            .addField(FieldDefinition.string("entityId", 36)
                .withColumnName("entity_id"))
            .addField(FieldDefinition.string("entityName", 200)
                .withColumnName("entity_name"))
            .addField(FieldDefinition.json("oldValue").withColumnName("old_value"))
            .addField(FieldDefinition.json("newValue").withColumnName("new_value"))
            .addField(FieldDefinition.datetime("timestamp"))
            .build();
    }

    public static CollectionDefinition fieldHistory() {
        return readOnlySystemBuilder("field-history", "Field History", "field_history")
            .tenantScoped(true)
            .addField(FieldDefinition.requiredString("collectionId", 36)
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("recordId", 36)
                .withColumnName("record_id"))
            .addField(FieldDefinition.requiredString("fieldName", 100)
                .withColumnName("field_name"))
            .addField(FieldDefinition.json("oldValue").withColumnName("old_value"))
            .addField(FieldDefinition.json("newValue").withColumnName("new_value"))
            .addField(FieldDefinition.requiredString("changedBy", 36)
                .withColumnName("changed_by"))
            .addField(FieldDefinition.datetime("changedAt").withColumnName("changed_at"))
            .addField(FieldDefinition.requiredString("changeSource", 20)
                .withColumnName("change_source"))
            .build();
    }

    public static CollectionDefinition workflowExecutionLogs() {
        return readOnlySystemBuilder("workflow-execution-logs", "Workflow Execution Logs", "workflow_execution_log")
            .tenantScoped(true)
            .addField(FieldDefinition.masterDetail("workflowRuleId", "workflow-rules",
                "Workflow Rule").withColumnName("workflow_rule_id"))
            .addField(FieldDefinition.requiredString("recordId", 36)
                .withColumnName("record_id"))
            .addField(FieldDefinition.requiredString("triggerType", 30)
                .withColumnName("trigger_type"))
            .addField(FieldDefinition.requiredString("status", 20))
            .addField(FieldDefinition.integer("actionsExecuted")
                .withColumnName("actions_executed"))
            .addField(FieldDefinition.text("errorMessage").withColumnName("error_message"))
            .addField(FieldDefinition.datetime("executedAt").withColumnName("executed_at"))
            .addField(FieldDefinition.integer("durationMs").withColumnName("duration_ms"))
            .addField(FieldDefinition.integer("ruleVersion").withColumnName("rule_version"))
            .build();
    }

    public static CollectionDefinition emailLogs() {
        return readOnlySystemBuilder("email-logs", "Email Logs", "email_log")
            .addField(FieldDefinition.lookup("templateId", "email-templates", "Email Template")
                .withColumnName("template_id"))
            .addField(FieldDefinition.requiredString("recipientEmail", 320)
                .withColumnName("recipient_email"))
            .addField(FieldDefinition.requiredString("subject", 500))
            .addField(FieldDefinition.requiredString("status", 20))
            .addField(FieldDefinition.string("source", 30))
            .addField(FieldDefinition.string("sourceId", 36).withColumnName("source_id"))
            .addField(FieldDefinition.text("errorMessage").withColumnName("error_message"))
            .addField(FieldDefinition.datetime("sentAt").withColumnName("sent_at"))
            .build();
    }

    public static CollectionDefinition webhookDeliveries() {
        return readOnlySystemBuilder("webhook-deliveries", "Webhook Deliveries", "webhook_delivery")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("webhookId", "webhooks", "Webhook")
                .withColumnName("webhook_id"))
            .addField(FieldDefinition.string("eventType").withColumnName("event_type"))
            .addField(FieldDefinition.json("payload"))
            .addField(FieldDefinition.integer("responseStatus")
                .withColumnName("response_status"))
            .addField(FieldDefinition.text("responseBody").withColumnName("response_body"))
            .addField(FieldDefinition.integer("attemptCount")
                .withColumnName("attempt_count"))
            .addField(FieldDefinition.string("status"))
            .addField(FieldDefinition.datetime("nextRetryAt").withColumnName("next_retry_at"))
            .addField(FieldDefinition.datetime("deliveredAt")
                .withColumnName("delivered_at"))
            .build();
    }

    public static CollectionDefinition loginHistory() {
        return readOnlySystemBuilder("login-history", "Login History", "login_history")
            .addField(FieldDefinition.requiredString("userId", 36)
                .withColumnName("user_id"))
            .addField(FieldDefinition.datetime("loginTime").withColumnName("login_time"))
            .addField(FieldDefinition.string("sourceIp", 45).withColumnName("source_ip"))
            .addField(FieldDefinition.string("loginType", 20).withColumnName("login_type"))
            .addField(FieldDefinition.string("status", 20))
            .addField(FieldDefinition.text("userAgent").withColumnName("user_agent"))
            .build();
    }

    // =========================================================================
    // Groups & Membership Collections
    // =========================================================================

    public static CollectionDefinition userGroups() {
        return systemBuilder("user-groups", "User Groups", "user_group")
            .displayFieldName("name")
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.string("groupType", 20).withColumnName("group_type")
                .withDefault("PUBLIC")
                .withEnumValues(List.of("PUBLIC", "QUEUE", "SYSTEM")))
            .addField(FieldDefinition.requiredString("source", 20)
                .withDefault("MANUAL"))
            .addField(FieldDefinition.string("oidcGroupName", 200)
                .withColumnName("oidc_group_name"))
            .build();
    }

    public static CollectionDefinition groupMemberships() {
        return systemBuilder("group-memberships", "Group Memberships", "group_membership")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("groupId", "user-groups", "Group")
                .withColumnName("group_id"))
            .addField(FieldDefinition.requiredString("memberType", 10)
                .withColumnName("member_type")
                .withEnumValues(List.of("USER", "GROUP")))
            .addField(FieldDefinition.requiredString("memberId", 36)
                .withColumnName("member_id"))
            .build();
    }

    // =========================================================================
    // Permission Collections
    // =========================================================================

    public static CollectionDefinition profileSystemPermissions() {
        return systemBuilder("profile-system-permissions", "Profile System Permissions",
                "profile_system_permission")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("profileId", "profiles", "Profile")
                .withColumnName("profile_id"))
            .addField(FieldDefinition.requiredString("permissionName", 100)
                .withColumnName("permission_name"))
            .addField(FieldDefinition.bool("granted").withDefault(false)
                .withNullable(false))
            .build();
    }

    public static CollectionDefinition profileObjectPermissions() {
        return systemBuilder("profile-object-permissions", "Profile Object Permissions",
                "profile_object_permission")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("profileId", "profiles", "Profile")
                .withColumnName("profile_id"))
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.bool("canCreate").withColumnName("can_create")
                .withDefault(false).withNullable(false))
            .addField(FieldDefinition.bool("canRead").withColumnName("can_read")
                .withDefault(false).withNullable(false))
            .addField(FieldDefinition.bool("canEdit").withColumnName("can_edit")
                .withDefault(false).withNullable(false))
            .addField(FieldDefinition.bool("canDelete").withColumnName("can_delete")
                .withDefault(false).withNullable(false))
            .addField(FieldDefinition.bool("canViewAll").withColumnName("can_view_all")
                .withDefault(false).withNullable(false))
            .addField(FieldDefinition.bool("canModifyAll").withColumnName("can_modify_all")
                .withDefault(false).withNullable(false))
            .build();
    }

    public static CollectionDefinition profileFieldPermissions() {
        return systemBuilder("profile-field-permissions", "Profile Field Permissions",
                "profile_field_permission")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("profileId", "profiles", "Profile")
                .withColumnName("profile_id"))
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.masterDetail("fieldId", "fields", "Field")
                .withColumnName("field_id"))
            .addField(FieldDefinition.requiredString("visibility", 20)
                .withDefault("VISIBLE"))
            .build();
    }

    public static CollectionDefinition permsetSystemPermissions() {
        return systemBuilder("permset-system-permissions", "Permission Set System Permissions",
                "permset_system_permission")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("permissionSetId", "permission-sets",
                "Permission Set").withColumnName("permission_set_id"))
            .addField(FieldDefinition.requiredString("permissionName", 100)
                .withColumnName("permission_name"))
            .addField(FieldDefinition.bool("granted").withDefault(false)
                .withNullable(false))
            .build();
    }

    public static CollectionDefinition permsetObjectPermissions() {
        return systemBuilder("permset-object-permissions", "Permission Set Object Permissions",
                "permset_object_permission")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("permissionSetId", "permission-sets",
                "Permission Set").withColumnName("permission_set_id"))
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.bool("canCreate").withColumnName("can_create")
                .withDefault(false).withNullable(false))
            .addField(FieldDefinition.bool("canRead").withColumnName("can_read")
                .withDefault(false).withNullable(false))
            .addField(FieldDefinition.bool("canEdit").withColumnName("can_edit")
                .withDefault(false).withNullable(false))
            .addField(FieldDefinition.bool("canDelete").withColumnName("can_delete")
                .withDefault(false).withNullable(false))
            .addField(FieldDefinition.bool("canViewAll").withColumnName("can_view_all")
                .withDefault(false).withNullable(false))
            .addField(FieldDefinition.bool("canModifyAll").withColumnName("can_modify_all")
                .withDefault(false).withNullable(false))
            .build();
    }

    public static CollectionDefinition permsetFieldPermissions() {
        return systemBuilder("permset-field-permissions", "Permission Set Field Permissions",
                "permset_field_permission")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("permissionSetId", "permission-sets",
                "Permission Set").withColumnName("permission_set_id"))
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.masterDetail("fieldId", "fields", "Field")
                .withColumnName("field_id"))
            .addField(FieldDefinition.requiredString("visibility", 20)
                .withDefault("VISIBLE"))
            .build();
    }

    // =========================================================================
    // Permission Assignment Collections
    // =========================================================================

    public static CollectionDefinition userPermissionSets() {
        return systemBuilder("user-permission-sets", "User Permission Sets",
                "user_permission_set")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("userId", "users", "User")
                .withColumnName("user_id"))
            .addField(FieldDefinition.masterDetail("permissionSetId", "permission-sets",
                "Permission Set").withColumnName("permission_set_id"))
            .build();
    }

    public static CollectionDefinition groupPermissionSets() {
        return systemBuilder("group-permission-sets", "Group Permission Sets",
                "group_permission_set")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("groupId", "user-groups", "Group")
                .withColumnName("group_id"))
            .addField(FieldDefinition.masterDetail("permissionSetId", "permission-sets",
                "Permission Set").withColumnName("permission_set_id"))
            .build();
    }

    // =========================================================================
    // Layout Child Collections
    // =========================================================================

    public static CollectionDefinition layoutSections() {
        return systemBuilder("layout-sections", "Layout Sections", "layout_section")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("layoutId", "page-layouts", "Layout")
                .withColumnName("layout_id"))
            .addField(FieldDefinition.string("heading", 200))
            .addField(FieldDefinition.integer("columns").withDefault(2))
            .addField(FieldDefinition.requiredInteger("sortOrder")
                .withColumnName("sort_order"))
            .addField(FieldDefinition.bool("collapsed").withDefault(false))
            .addField(FieldDefinition.string("style", 20).withDefault("DEFAULT"))
            .addField(FieldDefinition.string("sectionType", 30)
                .withColumnName("section_type").withDefault("STANDARD"))
            .addField(FieldDefinition.string("tabGroup", 100)
                .withColumnName("tab_group"))
            .addField(FieldDefinition.string("tabLabel", 200)
                .withColumnName("tab_label"))
            .addField(FieldDefinition.json("visibilityRule")
                .withColumnName("visibility_rule"))
            .build();
    }

    public static CollectionDefinition layoutFields() {
        return systemBuilder("layout-fields", "Layout Fields", "layout_field")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("sectionId", "layout-sections", "Section")
                .withColumnName("section_id"))
            .addField(FieldDefinition.masterDetail("fieldId", "fields", "Field")
                .withColumnName("field_id"))
            .addField(FieldDefinition.integer("columnNumber")
                .withColumnName("column_number").withDefault(1))
            .addField(FieldDefinition.requiredInteger("sortOrder")
                .withColumnName("sort_order"))
            .addField(FieldDefinition.bool("isRequiredOnLayout")
                .withColumnName("is_required_on_layout").withDefault(false))
            .addField(FieldDefinition.bool("isReadOnlyOnLayout")
                .withColumnName("is_read_only_on_layout").withDefault(false))
            .addField(FieldDefinition.string("labelOverride", 200)
                .withColumnName("label_override"))
            .addField(FieldDefinition.string("helpTextOverride", 500)
                .withColumnName("help_text_override"))
            .addField(FieldDefinition.json("visibilityRule")
                .withColumnName("visibility_rule"))
            .addField(FieldDefinition.integer("columnSpan")
                .withColumnName("column_span").withDefault(1))
            .build();
    }

    public static CollectionDefinition layoutRelatedLists() {
        return systemBuilder("layout-related-lists", "Layout Related Lists",
                "layout_related_list")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("layoutId", "page-layouts", "Layout")
                .withColumnName("layout_id"))
            .addField(FieldDefinition.masterDetail("relatedCollectionId", "collections",
                "Related Collection").withColumnName("related_collection_id"))
            .addField(FieldDefinition.masterDetail("relationshipFieldId", "fields",
                "Relationship Field").withColumnName("relationship_field_id"))
            .addField(FieldDefinition.requiredJson("displayColumns")
                .withColumnName("display_columns"))
            .addField(FieldDefinition.string("sortField", 100)
                .withColumnName("sort_field"))
            .addField(FieldDefinition.string("sortDirection", 4)
                .withColumnName("sort_direction").withDefault("DESC"))
            .addField(FieldDefinition.integer("rowLimit")
                .withColumnName("row_limit").withDefault(10))
            .addField(FieldDefinition.requiredInteger("sortOrder")
                .withColumnName("sort_order"))
            .build();
    }

    public static CollectionDefinition uiMenuItems() {
        return systemBuilder("ui-menu-items", "UI Menu Items", "ui_menu_item")
            .displayFieldName("label")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("menuId", "ui-menus", "Menu")
                .withColumnName("menu_id"))
            .addField(FieldDefinition.requiredString("label", 100))
            .addField(FieldDefinition.requiredString("path", 200))
            .addField(FieldDefinition.string("icon", 100))
            .addField(FieldDefinition.integer("displayOrder")
                .withColumnName("display_order").withDefault(0)
                .withNullable(false))
            .addField(FieldDefinition.bool("active").withDefault(true)
                .withNullable(false))
            .build();
    }

    // =========================================================================
    // Remaining Child Collections
    // =========================================================================

    public static CollectionDefinition picklistDependencies() {
        return systemBuilder("picklist-dependencies", "Picklist Dependencies",
                "picklist_dependency")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("controllingFieldId", "fields",
                "Controlling Field").withColumnName("controlling_field_id"))
            .addField(FieldDefinition.masterDetail("dependentFieldId", "fields",
                "Dependent Field").withColumnName("dependent_field_id"))
            .addField(FieldDefinition.requiredJson("mapping"))
            .build();
    }

    public static CollectionDefinition recordTypePicklists() {
        return systemBuilder("record-type-picklists", "Record Type Picklists",
                "record_type_picklist")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("recordTypeId", "record-types",
                "Record Type").withColumnName("record_type_id"))
            .addField(FieldDefinition.masterDetail("fieldId", "fields", "Field")
                .withColumnName("field_id"))
            .addField(FieldDefinition.requiredJson("availableValues")
                .withColumnName("available_values"))
            .addField(FieldDefinition.string("defaultValue", 255)
                .withColumnName("default_value"))
            .build();
    }

    public static CollectionDefinition workflowActions() {
        return systemBuilder("workflow-actions", "Workflow Actions", "workflow_action")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("workflowRuleId", "workflow-rules",
                "Workflow Rule").withColumnName("workflow_rule_id"))
            .addField(FieldDefinition.requiredString("actionType", 30)
                .withColumnName("action_type"))
            .addField(FieldDefinition.integer("executionOrder")
                .withColumnName("execution_order").withDefault(0))
            .addField(FieldDefinition.requiredJson("config"))
            .addField(FieldDefinition.bool("active").withDefault(true))
            .addField(FieldDefinition.integer("retryCount")
                .withColumnName("retry_count").withDefault(0)
                .withNullable(false))
            .addField(FieldDefinition.integer("retryDelaySeconds")
                .withColumnName("retry_delay_seconds").withDefault(60)
                .withNullable(false))
            .addField(FieldDefinition.requiredString("retryBackoff", 20)
                .withColumnName("retry_backoff").withDefault("FIXED"))
            .build();
    }

    public static CollectionDefinition workflowPendingActions() {
        return systemBuilder("workflow-pending-actions", "Workflow Pending Actions",
                "workflow_pending_action")
            .addField(FieldDefinition.masterDetail("executionLogId",
                "workflow-execution-logs", "Execution Log")
                .withColumnName("execution_log_id"))
            .addField(FieldDefinition.masterDetail("workflowRuleId", "workflow-rules",
                "Workflow Rule").withColumnName("workflow_rule_id"))
            .addField(FieldDefinition.requiredInteger("actionIndex")
                .withColumnName("action_index"))
            .addField(FieldDefinition.string("recordId", 36)
                .withColumnName("record_id"))
            .addField(FieldDefinition.json("recordSnapshot")
                .withColumnName("record_snapshot"))
            .addField(FieldDefinition.datetime("scheduledAt")
                .withColumnName("scheduled_at").withNullable(false))
            .addField(FieldDefinition.string("status", 20).withDefault("PENDING"))
            .build();
    }

    public static CollectionDefinition scriptTriggers() {
        return systemBuilder("script-triggers", "Script Triggers", "script_trigger")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("scriptId", "scripts", "Script")
                .withColumnName("script_id"))
            .addField(FieldDefinition.masterDetail("collectionId", "collections",
                "Collection").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("triggerEvent", 20)
                .withColumnName("trigger_event")
                .withEnumValues(List.of("INSERT", "UPDATE", "DELETE")))
            .addField(FieldDefinition.integer("executionOrder")
                .withColumnName("execution_order").withDefault(0))
            .addField(FieldDefinition.bool("active").withDefault(true))
            .build();
    }

    public static CollectionDefinition approvalSteps() {
        return systemBuilder("approval-steps", "Approval Steps", "approval_step")
            .displayFieldName("name")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("approvalProcessId",
                "approval-processes", "Approval Process")
                .withColumnName("approval_process_id"))
            .addField(FieldDefinition.requiredInteger("stepNumber")
                .withColumnName("step_number"))
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.text("entryCriteria")
                .withColumnName("entry_criteria"))
            .addField(FieldDefinition.requiredString("approverType", 30)
                .withColumnName("approver_type"))
            .addField(FieldDefinition.string("approverId", 36)
                .withColumnName("approver_id"))
            .addField(FieldDefinition.string("approverField", 100)
                .withColumnName("approver_field"))
            .addField(FieldDefinition.bool("unanimityRequired")
                .withColumnName("unanimity_required").withDefault(false))
            .addField(FieldDefinition.integer("escalationTimeoutHours")
                .withColumnName("escalation_timeout_hours"))
            .addField(FieldDefinition.string("escalationAction", 20)
                .withColumnName("escalation_action"))
            .addField(FieldDefinition.string("onApproveAction", 20)
                .withColumnName("on_approve_action").withDefault("NEXT_STEP"))
            .addField(FieldDefinition.string("onRejectAction", 20)
                .withColumnName("on_reject_action").withDefault("REJECT_FINAL"))
            .build();
    }

    public static CollectionDefinition approvalInstances() {
        return systemBuilder("approval-instances", "Approval Instances",
                "approval_instance")
            .addField(FieldDefinition.masterDetail("approvalProcessId",
                "approval-processes", "Approval Process")
                .withColumnName("approval_process_id"))
            .addField(FieldDefinition.masterDetail("collectionId", "collections",
                "Collection").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("recordId", 36)
                .withColumnName("record_id"))
            .addField(FieldDefinition.requiredString("submittedBy", 36)
                .withColumnName("submitted_by"))
            .addField(FieldDefinition.requiredInteger("currentStepNumber")
                .withColumnName("current_step_number").withDefault(1))
            .addField(FieldDefinition.requiredString("status", 20)
                .withDefault("PENDING")
                .withEnumValues(List.of("PENDING", "APPROVED", "REJECTED", "RECALLED")))
            .addField(FieldDefinition.datetime("submittedAt")
                .withColumnName("submitted_at").withNullable(false))
            .addField(FieldDefinition.datetime("completedAt")
                .withColumnName("completed_at"))
            .build();
    }

    public static CollectionDefinition approvalStepInstances() {
        return systemBuilder("approval-step-instances", "Approval Step Instances",
                "approval_step_instance")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("approvalInstanceId",
                "approval-instances", "Approval Instance")
                .withColumnName("approval_instance_id"))
            .addField(FieldDefinition.masterDetail("stepId", "approval-steps",
                "Approval Step").withColumnName("step_id"))
            .addField(FieldDefinition.requiredString("assignedTo", 36)
                .withColumnName("assigned_to"))
            .addField(FieldDefinition.requiredString("status", 20)
                .withDefault("PENDING")
                .withEnumValues(List.of("PENDING", "APPROVED", "REJECTED", "REASSIGNED")))
            .addField(FieldDefinition.text("comments"))
            .addField(FieldDefinition.datetime("actedAt")
                .withColumnName("acted_at"))
            .build();
    }

    public static CollectionDefinition connectedAppTokens() {
        return systemBuilder("connected-app-tokens", "Connected App Tokens",
                "connected_app_token")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("connectedAppId", "connected-apps",
                "Connected App").withColumnName("connected_app_id"))
            .addField(FieldDefinition.requiredString("tokenHash", 200)
                .withColumnName("token_hash"))
            .addField(FieldDefinition.requiredJson("scopes"))
            .addField(FieldDefinition.datetime("issuedAt")
                .withColumnName("issued_at").withNullable(false))
            .addField(FieldDefinition.datetime("expiresAt")
                .withColumnName("expires_at").withNullable(false))
            .addField(FieldDefinition.bool("revoked").withDefault(false))
            .addField(FieldDefinition.datetime("revokedAt")
                .withColumnName("revoked_at"))
            .build();
    }

    public static CollectionDefinition dashboardComponents() {
        return systemBuilder("dashboard-components", "Dashboard Components",
                "dashboard_component")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("dashboardId", "dashboards",
                "Dashboard").withColumnName("dashboard_id"))
            .addField(FieldDefinition.masterDetail("reportId", "reports", "Report")
                .withColumnName("report_id"))
            .addField(FieldDefinition.requiredString("componentType", 20)
                .withColumnName("component_type"))
            .addField(FieldDefinition.string("title", 200))
            .addField(FieldDefinition.requiredInteger("columnPosition")
                .withColumnName("column_position"))
            .addField(FieldDefinition.requiredInteger("rowPosition")
                .withColumnName("row_position"))
            .addField(FieldDefinition.integer("columnSpan")
                .withColumnName("column_span").withDefault(1))
            .addField(FieldDefinition.integer("rowSpan")
                .withColumnName("row_span").withDefault(1))
            .addField(FieldDefinition.json("config").withDefault("{}"))
            .addField(FieldDefinition.requiredInteger("sortOrder")
                .withColumnName("sort_order"))
            .build();
    }

    // =========================================================================
    // Read-Only: Remaining Log Collections
    // =========================================================================

    public static CollectionDefinition workflowActionLogs() {
        return readOnlySystemBuilder("workflow-action-logs", "Workflow Action Logs",
                "workflow_action_log")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("executionLogId",
                "workflow-execution-logs", "Execution Log")
                .withColumnName("execution_log_id"))
            .addField(FieldDefinition.lookup("actionId", "workflow-actions", "Action")
                .withColumnName("action_id"))
            .addField(FieldDefinition.requiredString("actionType", 50)
                .withColumnName("action_type"))
            .addField(FieldDefinition.requiredString("status", 20))
            .addField(FieldDefinition.text("errorMessage")
                .withColumnName("error_message"))
            .addField(FieldDefinition.json("inputSnapshot")
                .withColumnName("input_snapshot"))
            .addField(FieldDefinition.json("outputSnapshot")
                .withColumnName("output_snapshot"))
            .addField(FieldDefinition.integer("durationMs")
                .withColumnName("duration_ms"))
            .addField(FieldDefinition.datetime("executedAt")
                .withColumnName("executed_at"))
            .addField(FieldDefinition.requiredInteger("attemptNumber")
                .withColumnName("attempt_number").withDefault(1))
            .build();
    }

    public static CollectionDefinition workflowRuleVersions() {
        return readOnlySystemBuilder("workflow-rule-versions", "Workflow Rule Versions",
                "workflow_rule_version")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("workflowRuleId", "workflow-rules",
                "Workflow Rule").withColumnName("workflow_rule_id"))
            .addField(FieldDefinition.requiredInteger("versionNumber")
                .withColumnName("version_number"))
            .addField(FieldDefinition.requiredJson("snapshot"))
            .addField(FieldDefinition.string("changeSummary", 500)
                .withColumnName("change_summary"))
            .build();
    }

    public static CollectionDefinition scriptExecutionLogs() {
        return readOnlySystemBuilder("script-execution-logs", "Script Execution Logs",
                "script_execution_log")
            .tenantScoped(true)
            .addField(FieldDefinition.masterDetail("scriptId", "scripts", "Script")
                .withColumnName("script_id"))
            .addField(FieldDefinition.requiredString("status", 20)
                .withEnumValues(List.of("SUCCESS", "FAILURE", "TIMEOUT",
                    "GOVERNOR_LIMIT")))
            .addField(FieldDefinition.string("triggerType", 30)
                .withColumnName("trigger_type"))
            .addField(FieldDefinition.string("recordId", 36)
                .withColumnName("record_id"))
            .addField(FieldDefinition.integer("durationMs")
                .withColumnName("duration_ms"))
            .addField(FieldDefinition.integer("cpuMs")
                .withColumnName("cpu_ms"))
            .addField(FieldDefinition.integer("queriesExecuted")
                .withColumnName("queries_executed").withDefault(0))
            .addField(FieldDefinition.integer("dmlRows")
                .withColumnName("dml_rows").withDefault(0))
            .addField(FieldDefinition.integer("callouts").withDefault(0))
            .addField(FieldDefinition.text("errorMessage")
                .withColumnName("error_message"))
            .addField(FieldDefinition.text("logOutput")
                .withColumnName("log_output"))
            .addField(FieldDefinition.datetime("executedAt")
                .withColumnName("executed_at"))
            .build();
    }

    public static CollectionDefinition flowExecutions() {
        return readOnlySystemBuilder("flow-executions", "Flow Executions",
                "flow_execution")
            .tenantScoped(true)
            .addField(FieldDefinition.masterDetail("flowId", "flows", "Flow")
                .withColumnName("flow_id"))
            .addField(FieldDefinition.requiredString("status", 20)
                .withDefault("RUNNING")
                .withEnumValues(List.of("RUNNING", "COMPLETED", "FAILED",
                    "WAITING", "CANCELLED")))
            .addField(FieldDefinition.string("startedBy", 36)
                .withColumnName("started_by"))
            .addField(FieldDefinition.string("triggerRecordId", 36)
                .withColumnName("trigger_record_id"))
            .addField(FieldDefinition.json("variables").withDefault("{}"))
            .addField(FieldDefinition.string("currentNodeId", 100)
                .withColumnName("current_node_id"))
            .addField(FieldDefinition.text("errorMessage")
                .withColumnName("error_message"))
            .addField(FieldDefinition.datetime("startedAt")
                .withColumnName("started_at").withNullable(false))
            .addField(FieldDefinition.datetime("completedAt")
                .withColumnName("completed_at"))
            .build();
    }

    public static CollectionDefinition jobExecutionLogs() {
        return readOnlySystemBuilder("job-execution-logs", "Job Execution Logs",
                "job_execution_log")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("jobId", "scheduled-jobs",
                "Scheduled Job").withColumnName("job_id"))
            .addField(FieldDefinition.requiredString("status", 20))
            .addField(FieldDefinition.integer("recordsProcessed")
                .withColumnName("records_processed").withDefault(0))
            .addField(FieldDefinition.text("errorMessage")
                .withColumnName("error_message"))
            .addField(FieldDefinition.datetime("startedAt")
                .withColumnName("started_at").withNullable(false))
            .addField(FieldDefinition.datetime("completedAt")
                .withColumnName("completed_at"))
            .addField(FieldDefinition.integer("durationMs")
                .withColumnName("duration_ms"))
            .build();
    }

    public static CollectionDefinition bulkJobResults() {
        return readOnlySystemBuilder("bulk-job-results", "Bulk Job Results",
                "bulk_job_result")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("bulkJobId", "bulk-jobs",
                "Bulk Job").withColumnName("bulk_job_id"))
            .addField(FieldDefinition.requiredInteger("recordIndex")
                .withColumnName("record_index"))
            .addField(FieldDefinition.string("recordId", 36)
                .withColumnName("record_id"))
            .addField(FieldDefinition.requiredString("status", 20)
                .withEnumValues(List.of("SUCCESS", "FAILURE")))
            .addField(FieldDefinition.text("errorMessage")
                .withColumnName("error_message"))
            .build();
    }

    public static CollectionDefinition collectionVersions() {
        return readOnlySystemBuilder("collection-versions", "Collection Versions",
                "collection_version")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("collectionId", "collections",
                "Collection").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredInteger("version"))
            .addField(FieldDefinition.json("schema"))
            .build();
    }

    public static CollectionDefinition fieldVersions() {
        return readOnlySystemBuilder("field-versions", "Field Versions", "field_version")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("collectionVersionId",
                "collection-versions", "Collection Version")
                .withColumnName("collection_version_id"))
            .addField(FieldDefinition.masterDetail("fieldId", "fields", "Field")
                .withColumnName("field_id"))
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.requiredString("type", 50))
            .addField(FieldDefinition.bool("required").withDefault(false)
                .withNullable(false))
            .addField(FieldDefinition.bool("active").withDefault(true)
                .withNullable(false))
            .addField(FieldDefinition.json("constraints"))
            .build();
    }

    public static CollectionDefinition migrationSteps() {
        return readOnlySystemBuilder("migration-steps", "Migration Steps",
                "migration_step")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("migrationRunId", "migration-runs",
                "Migration Run").withColumnName("migration_run_id"))
            .addField(FieldDefinition.requiredInteger("stepNumber")
                .withColumnName("step_number"))
            .addField(FieldDefinition.requiredString("operation", 100))
            .addField(FieldDefinition.requiredString("status", 50)
                .withDefault("PENDING")
                .withEnumValues(List.of("PENDING", "RUNNING", "COMPLETED",
                    "FAILED", "SKIPPED")))
            .addField(FieldDefinition.json("details"))
            .addField(FieldDefinition.string("errorMessage", 2000)
                .withColumnName("error_message"))
            .build();
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Creates a builder pre-configured for a system collection.
     * Default: tenant-scoped, all CRUD enabled, events enabled, system=true.
     */
    private static CollectionDefinitionBuilder systemBuilder(String name, String displayName, String tableName) {
        return CollectionDefinition.builder()
            .name(name)
            .displayName(displayName)
            .storageConfig(StorageConfig.physicalTable(tableName))
            .apiConfig(ApiConfig.allEnabled("/api/" + name))
            .eventsConfig(EventsConfig.allEvents("emf.collections"))
            .systemCollection(true)
            .tenantScoped(true)
            .readOnly(false)
            .addField(FieldDefinition.datetime("createdAt").withColumnName("created_at"))
            .addField(FieldDefinition.string("createdBy", 255).withColumnName("created_by"))
            .addField(FieldDefinition.datetime("updatedAt").withColumnName("updated_at"))
            .addField(FieldDefinition.string("updatedBy", 255).withColumnName("updated_by"));
    }

    /**
     * Creates a builder pre-configured for a read-only system collection.
     * Default: tenant-scoped, read-only API, events disabled, system=true.
     */
    private static CollectionDefinitionBuilder readOnlySystemBuilder(String name, String displayName, String tableName) {
        return CollectionDefinition.builder()
            .name(name)
            .displayName(displayName)
            .storageConfig(StorageConfig.physicalTable(tableName))
            .apiConfig(ApiConfig.readOnly("/api/" + name))
            .eventsConfig(EventsConfig.disabled())
            .systemCollection(true)
            .tenantScoped(true)
            .readOnly(true)
            .addField(FieldDefinition.datetime("createdAt").withColumnName("created_at"))
            .addField(FieldDefinition.string("createdBy", 255).withColumnName("created_by"))
            .addField(FieldDefinition.datetime("updatedAt").withColumnName("updated_at"))
            .addField(FieldDefinition.string("updatedBy", 255).withColumnName("updated_by"));
    }
}
