package com.emf.controlplane.config;

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
 */
public final class SystemCollectionDefinitions {

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

        // UI & Layout
        definitions.add(pageLayouts());
        definitions.add(layoutAssignments());
        definitions.add(listViews());
        definitions.add(uiPages());
        definitions.add(uiMenus());

        // Picklists
        definitions.add(globalPicklists());
        definitions.add(picklistValues());

        // Record types & Validation
        definitions.add(recordTypes());
        definitions.add(validationRules());

        // Workflows & Automation
        definitions.add(workflowRules());
        definitions.add(workflowActionTypes());
        definitions.add(scripts());
        definitions.add(flows());
        definitions.add(approvalProcesses());
        definitions.add(scheduledJobs());

        // Communication
        definitions.add(emailTemplates());
        definitions.add(webhooks());

        // Integration
        definitions.add(connectedApps());
        definitions.add(oidcProviders());

        // Reports & Dashboards
        definitions.add(reports());
        definitions.add(reportFolders());
        definitions.add(dashboards());

        // Collaboration
        definitions.add(notes());
        definitions.add(attachments());

        // Platform management
        definitions.add(workers());
        definitions.add(collectionAssignments());
        definitions.add(bulkJobs());
        definitions.add(packages());
        definitions.add(migrationRuns());

        // Read-only audit/log collections
        definitions.add(securityAuditLogs());
        definitions.add(setupAuditEntries());
        definitions.add(fieldHistory());
        definitions.add(workflowExecutionLogs());
        definitions.add(emailLogs());
        definitions.add(webhookDeliveries());
        definitions.add(loginHistory());

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
            .addField(FieldDefinition.requiredString("name", 255))
            .addField(FieldDefinition.text("description"))
            .addField(FieldDefinition.bool("isSystem").withColumnName("is_system").withDefault(false))
            .build();
    }

    public static CollectionDefinition permissionSets() {
        return systemBuilder("permission-sets", "Permission Sets", "permission_set")
            .addField(FieldDefinition.requiredString("name", 255))
            .addField(FieldDefinition.text("description"))
            .addField(FieldDefinition.bool("isSystem").withColumnName("is_system").withDefault(false))
            .build();
    }

    public static CollectionDefinition collections() {
        return systemBuilder("collections", "Collections", "collection")
            .addField(FieldDefinition.requiredString("name", 100))
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
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.requiredString("createdBy").withColumnName("created_by"))
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
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.requiredString("path", 200))
            .addField(FieldDefinition.string("title", 200))
            .addField(FieldDefinition.json("config"))
            .addField(FieldDefinition.bool("active").withDefault(true).withNullable(false))
            .build();
    }

    public static CollectionDefinition uiMenus() {
        return systemBuilder("ui-menus", "UI Menus", "ui_menu")
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
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.bool("sorted").withDefault(false))
            .addField(FieldDefinition.bool("restricted").withDefault(true))
            .build();
    }

    public static CollectionDefinition picklistValues() {
        return systemBuilder("picklist-values", "Picklist Values", "picklist_value")
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
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.requiredString("scriptType", 30).withColumnName("script_type")
                .withEnumValues(List.of("BEFORE_TRIGGER", "AFTER_TRIGGER", "SCHEDULED",
                    "API_ENDPOINT", "VALIDATION", "EVENT_HANDLER", "EMAIL_HANDLER")))
            .addField(FieldDefinition.requiredString("language", 20).withDefault("javascript"))
            .addField(FieldDefinition.requiredText("sourceCode").withColumnName("source_code"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.integer("version"))
            .addField(FieldDefinition.requiredString("createdBy", 36).withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition flows() {
        return systemBuilder("flows", "Flows", "flow")
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 1000))
            .addField(FieldDefinition.requiredString("flowType", 30).withColumnName("flow_type")
                .withEnumValues(List.of("RECORD_TRIGGERED", "SCHEDULED",
                    "AUTOLAUNCHED", "SCREEN")))
            .addField(FieldDefinition.bool("active").withDefault(false))
            .addField(FieldDefinition.integer("version").withDefault(1))
            .addField(FieldDefinition.json("triggerConfig").withColumnName("trigger_config"))
            .addField(FieldDefinition.requiredJson("definition"))
            .addField(FieldDefinition.requiredString("createdBy", 36).withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition approvalProcesses() {
        return systemBuilder("approval-processes", "Approval Processes", "approval_process")
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
            .addField(FieldDefinition.requiredString("createdBy", 36)
                .withColumnName("created_by"))
            .build();
    }

    // =========================================================================
    // Communication Collections
    // =========================================================================

    public static CollectionDefinition emailTemplates() {
        return systemBuilder("email-templates", "Email Templates", "email_template")
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.string("description", 500))
            .addField(FieldDefinition.requiredString("subject", 500))
            .addField(FieldDefinition.requiredText("bodyHtml").withColumnName("body_html"))
            .addField(FieldDefinition.text("bodyText").withColumnName("body_text"))
            .addField(FieldDefinition.string("relatedCollectionId")
                .withColumnName("related_collection_id"))
            .addField(FieldDefinition.string("folder"))
            .addField(FieldDefinition.bool("isActive").withColumnName("is_active"))
            .addField(FieldDefinition.requiredString("createdBy", 36)
                .withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition webhooks() {
        return systemBuilder("webhooks", "Webhooks", "webhook")
            .addField(FieldDefinition.requiredString("name", 200))
            .addField(FieldDefinition.requiredString("url", 2048))
            .addField(FieldDefinition.requiredJson("events"))
            .addField(FieldDefinition.string("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.string("filterFormula").withColumnName("filter_formula"))
            .addField(FieldDefinition.json("headers"))
            .addField(FieldDefinition.string("secret", 200))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.json("retryPolicy").withColumnName("retry_policy"))
            .addField(FieldDefinition.requiredString("createdBy", 36)
                .withColumnName("created_by"))
            .build();
    }

    // =========================================================================
    // Integration Collections
    // =========================================================================

    public static CollectionDefinition connectedApps() {
        return systemBuilder("connected-apps", "Connected Apps", "connected_app")
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
            .addField(FieldDefinition.requiredString("createdBy", 36)
                .withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition oidcProviders() {
        return systemBuilder("oidc-providers", "OIDC Providers", "oidc_provider")
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
            .addField(FieldDefinition.requiredString("createdBy", 36)
                .withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition reportFolders() {
        return systemBuilder("report-folders", "Report Folders", "report_folder")
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.string("accessLevel", 20)
                .withColumnName("access_level").withDefault("PRIVATE"))
            .addField(FieldDefinition.requiredString("createdBy", 36)
                .withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition dashboards() {
        return systemBuilder("dashboards", "Dashboards", "dashboard")
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
            .addField(FieldDefinition.requiredString("createdBy", 36)
                .withColumnName("created_by"))
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
            .addField(FieldDefinition.requiredString("createdBy", 320)
                .withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition attachments() {
        return systemBuilder("attachments", "Attachments", "file_attachment")
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

    public static CollectionDefinition workers() {
        return systemBuilder("workers", "Workers", "worker")
            .tenantScoped(false)
            .addField(FieldDefinition.string("podName", 253).withColumnName("pod_name"))
            .addField(FieldDefinition.string("namespace", 63))
            .addField(FieldDefinition.requiredString("host", 253))
            .addField(FieldDefinition.requiredInteger("port").withDefault(8080))
            .addField(FieldDefinition.requiredString("baseUrl", 500)
                .withColumnName("base_url"))
            .addField(FieldDefinition.requiredString("pool", 50).withDefault("default"))
            .addField(FieldDefinition.requiredInteger("capacity").withDefault(50))
            .addField(FieldDefinition.requiredInteger("currentLoad")
                .withColumnName("current_load").withDefault(0))
            .addField(FieldDefinition.requiredString("status", 20))
            .addField(FieldDefinition.string("tenantAffinity", 36)
                .withColumnName("tenant_affinity"))
            .addField(FieldDefinition.json("labels"))
            .addField(FieldDefinition.datetime("lastHeartbeat").withColumnName("last_heartbeat"))
            .build();
    }

    public static CollectionDefinition collectionAssignments() {
        return systemBuilder("collection-assignments", "Collection Assignments", "collection_assignment")
            .tenantScoped(false)
            .addField(FieldDefinition.masterDetail("collectionId", "collections", "Collection")
                .withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("workerId", 253)
                .withColumnName("worker_id"))
            .addField(FieldDefinition.requiredString("status", 20).withDefault("PENDING"))
            .addField(FieldDefinition.datetime("assignedAt").withColumnName("assigned_at"))
            .addField(FieldDefinition.datetime("readyAt").withColumnName("ready_at"))
            .build();
    }

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
            .addField(FieldDefinition.requiredString("createdBy", 36)
                .withColumnName("created_by"))
            .addField(FieldDefinition.datetime("startedAt").withColumnName("started_at"))
            .addField(FieldDefinition.datetime("completedAt").withColumnName("completed_at"))
            .build();
    }

    public static CollectionDefinition packages() {
        return systemBuilder("packages", "Packages", "package")
            .addField(FieldDefinition.requiredString("name", 100))
            .addField(FieldDefinition.requiredString("version", 50))
            .addField(FieldDefinition.string("description"))
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
            .readOnly(false);
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
            .readOnly(true);
    }
}
