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
            .addImmutableField("tenantId")
            .addField(FieldDefinition.requiredString("slug"))
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("edition"))
            .addField(FieldDefinition.string("status"))
            .addField(FieldDefinition.json("settings"))
            .addField(FieldDefinition.json("limits"))
            .build();
    }

    public static CollectionDefinition users() {
        return systemBuilder("users", "Users", "platform_user")
            .addImmutableField("tenantId")
            .addField(FieldDefinition.requiredString("email"))
            .addField(FieldDefinition.string("username"))
            .addField(FieldDefinition.string("firstName").withColumnName("first_name"))
            .addField(FieldDefinition.string("lastName").withColumnName("last_name"))
            .addField(FieldDefinition.string("status"))
            .addField(FieldDefinition.string("locale"))
            .addField(FieldDefinition.string("timezone"))
            .addField(FieldDefinition.string("profileId").withColumnName("profile_id"))
            .addField(FieldDefinition.string("managerId").withColumnName("manager_id"))
            .addField(FieldDefinition.datetime("lastLoginAt").withColumnName("last_login_at"))
            .addField(FieldDefinition.integer("loginCount").withColumnName("login_count"))
            .addField(FieldDefinition.bool("mfaEnabled").withColumnName("mfa_enabled"))
            .addField(FieldDefinition.json("settings"))
            .build();
    }

    public static CollectionDefinition profiles() {
        return systemBuilder("profiles", "Profiles", "profile")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.bool("isSystem").withColumnName("is_system"))
            .build();
    }

    public static CollectionDefinition permissionSets() {
        return systemBuilder("permission-sets", "Permission Sets", "permission_set")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.bool("isSystem").withColumnName("is_system"))
            .build();
    }

    public static CollectionDefinition collections() {
        return systemBuilder("collections", "Collections", "collection")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("displayName").withColumnName("display_name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.string("path"))
            .addField(FieldDefinition.string("storageMode").withColumnName("storage_mode"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.bool("systemCollection").withColumnName("system_collection"))
            .addField(FieldDefinition.integer("currentVersion").withColumnName("current_version"))
            .addField(FieldDefinition.string("displayFieldId").withColumnName("display_field_id"))
            .build();
    }

    public static CollectionDefinition fields() {
        return systemBuilder("fields", "Fields", "field")
            .tenantScoped(false)
            .addField(FieldDefinition.requiredString("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("displayName").withColumnName("display_name"))
            .addField(FieldDefinition.requiredString("type"))
            .addField(FieldDefinition.bool("required"))
            .addField(FieldDefinition.bool("uniqueConstraint").withColumnName("unique_constraint"))
            .addField(FieldDefinition.bool("indexed"))
            .addField(FieldDefinition.json("defaultValue").withColumnName("default_value"))
            .addField(FieldDefinition.string("referenceTarget").withColumnName("reference_target"))
            .addField(FieldDefinition.integer("fieldOrder").withColumnName("field_order"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.json("constraints"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.json("fieldTypeConfig").withColumnName("field_type_config"))
            .addField(FieldDefinition.string("autoNumberSequenceName").withColumnName("auto_number_sequence_name"))
            .addField(FieldDefinition.string("relationshipType").withColumnName("relationship_type"))
            .addField(FieldDefinition.string("relationshipName").withColumnName("relationship_name"))
            .addField(FieldDefinition.bool("cascadeDelete").withColumnName("cascade_delete"))
            .addField(FieldDefinition.string("referenceCollectionId").withColumnName("reference_collection_id"))
            .addField(FieldDefinition.bool("trackHistory").withColumnName("track_history"))
            .build();
    }

    // =========================================================================
    // UI & Layout Collections
    // =========================================================================

    public static CollectionDefinition pageLayouts() {
        return systemBuilder("page-layouts", "Page Layouts", "page_layout")
            .addField(FieldDefinition.requiredString("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.string("layoutType").withColumnName("layout_type"))
            .addField(FieldDefinition.bool("isDefault").withColumnName("is_default"))
            .build();
    }

    public static CollectionDefinition layoutAssignments() {
        return systemBuilder("layout-assignments", "Layout Assignments", "layout_assignment")
            .tenantScoped(false)
            .addField(FieldDefinition.requiredString("layoutId").withColumnName("layout_id"))
            .addField(FieldDefinition.string("recordTypeId").withColumnName("record_type_id"))
            .addField(FieldDefinition.integer("assignmentOrder").withColumnName("assignment_order"))
            .build();
    }

    public static CollectionDefinition listViews() {
        return systemBuilder("list-views", "List Views", "list_view")
            .addField(FieldDefinition.requiredString("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("createdBy").withColumnName("created_by"))
            .addField(FieldDefinition.string("visibility"))
            .addField(FieldDefinition.bool("isDefault").withColumnName("is_default"))
            .addField(FieldDefinition.json("columns"))
            .addField(FieldDefinition.string("filterLogic").withColumnName("filter_logic"))
            .addField(FieldDefinition.json("filters"))
            .addField(FieldDefinition.string("sortField").withColumnName("sort_field"))
            .addField(FieldDefinition.string("sortDirection").withColumnName("sort_direction"))
            .addField(FieldDefinition.integer("rowLimit").withColumnName("row_limit"))
            .addField(FieldDefinition.json("chartConfig").withColumnName("chart_config"))
            .build();
    }

    public static CollectionDefinition uiPages() {
        return systemBuilder("ui-pages", "UI Pages", "ui_page")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("slug"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.string("content"))
            .addField(FieldDefinition.bool("isPublished").withColumnName("is_published"))
            .addField(FieldDefinition.string("publishedBy").withColumnName("published_by"))
            .addField(FieldDefinition.datetime("publishedAt").withColumnName("published_at"))
            .addField(FieldDefinition.string("createdBy").withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition uiMenus() {
        return systemBuilder("ui-menus", "UI Menus", "ui_menu")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.integer("displayOrder").withColumnName("display_order"))
            .build();
    }

    // =========================================================================
    // Picklist Collections
    // =========================================================================

    public static CollectionDefinition globalPicklists() {
        return systemBuilder("global-picklists", "Global Picklists", "global_picklist")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.bool("sorted"))
            .addField(FieldDefinition.bool("restricted"))
            .build();
    }

    public static CollectionDefinition picklistValues() {
        return systemBuilder("picklist-values", "Picklist Values", "picklist_value")
            .tenantScoped(false)
            .addField(FieldDefinition.requiredString("picklistSourceType").withColumnName("picklist_source_type"))
            .addField(FieldDefinition.requiredString("picklistSourceId").withColumnName("picklist_source_id"))
            .addField(FieldDefinition.requiredString("value"))
            .addField(FieldDefinition.requiredString("label"))
            .addField(FieldDefinition.bool("isDefault").withColumnName("is_default"))
            .addField(FieldDefinition.bool("isActive").withColumnName("is_active"))
            .addField(FieldDefinition.integer("sortOrder").withColumnName("sort_order"))
            .addField(FieldDefinition.string("color"))
            .addField(FieldDefinition.string("description"))
            .build();
    }

    // =========================================================================
    // Record Types & Validation
    // =========================================================================

    public static CollectionDefinition recordTypes() {
        return systemBuilder("record-types", "Record Types", "record_type")
            .addField(FieldDefinition.requiredString("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.bool("isActive").withColumnName("is_active"))
            .addField(FieldDefinition.bool("isDefault").withColumnName("is_default"))
            .build();
    }

    public static CollectionDefinition validationRules() {
        return systemBuilder("validation-rules", "Validation Rules", "validation_rule")
            .addField(FieldDefinition.requiredString("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.requiredString("errorConditionFormula").withColumnName("error_condition_formula"))
            .addField(FieldDefinition.requiredString("errorMessage").withColumnName("error_message"))
            .addField(FieldDefinition.string("errorField").withColumnName("error_field"))
            .addField(FieldDefinition.string("evaluateOn").withColumnName("evaluate_on"))
            .build();
    }

    // =========================================================================
    // Workflow & Automation Collections
    // =========================================================================

    public static CollectionDefinition workflowRules() {
        return systemBuilder("workflow-rules", "Workflow Rules", "workflow_rule")
            .addField(FieldDefinition.requiredString("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.requiredString("triggerType").withColumnName("trigger_type"))
            .addField(FieldDefinition.string("filterFormula").withColumnName("filter_formula"))
            .addField(FieldDefinition.bool("reEvaluateOnUpdate").withColumnName("re_evaluate_on_update"))
            .addField(FieldDefinition.integer("executionOrder").withColumnName("execution_order"))
            .addField(FieldDefinition.string("errorHandling").withColumnName("error_handling"))
            .addField(FieldDefinition.json("triggerFields").withColumnName("trigger_fields"))
            .addField(FieldDefinition.string("cronExpression").withColumnName("cron_expression"))
            .addField(FieldDefinition.string("timezone"))
            .addField(FieldDefinition.datetime("lastScheduledRun").withColumnName("last_scheduled_run"))
            .addField(FieldDefinition.string("executionMode").withColumnName("execution_mode"))
            .build();
    }

    public static CollectionDefinition workflowActionTypes() {
        return systemBuilder("workflow-action-types", "Workflow Action Types", "workflow_action_type")
            .tenantScoped(false)
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.json("configurationSchema").withColumnName("configuration_schema"))
            .build();
    }

    public static CollectionDefinition scripts() {
        return systemBuilder("scripts", "Scripts", "script")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.string("scriptType").withColumnName("script_type"))
            .addField(FieldDefinition.string("language"))
            .addField(FieldDefinition.string("sourceCode").withColumnName("source_code"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.integer("version"))
            .addField(FieldDefinition.string("createdBy").withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition flows() {
        return systemBuilder("flows", "Flows", "flow")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.string("flowType").withColumnName("flow_type"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.integer("version"))
            .addField(FieldDefinition.json("triggerConfig").withColumnName("trigger_config"))
            .addField(FieldDefinition.json("definition"))
            .addField(FieldDefinition.string("createdBy").withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition approvalProcesses() {
        return systemBuilder("approval-processes", "Approval Processes", "approval_process")
            .addField(FieldDefinition.requiredString("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.string("entryCriteria").withColumnName("entry_criteria"))
            .addField(FieldDefinition.string("recordEditability").withColumnName("record_editability"))
            .addField(FieldDefinition.string("initialSubmitterField").withColumnName("initial_submitter_field"))
            .addField(FieldDefinition.json("onSubmitFieldUpdates").withColumnName("on_submit_field_updates"))
            .addField(FieldDefinition.json("onApprovalFieldUpdates").withColumnName("on_approval_field_updates"))
            .addField(FieldDefinition.json("onRejectionFieldUpdates").withColumnName("on_rejection_field_updates"))
            .addField(FieldDefinition.json("onRecallFieldUpdates").withColumnName("on_recall_field_updates"))
            .addField(FieldDefinition.bool("allowRecall").withColumnName("allow_recall"))
            .addField(FieldDefinition.integer("executionOrder").withColumnName("execution_order"))
            .build();
    }

    public static CollectionDefinition scheduledJobs() {
        return systemBuilder("scheduled-jobs", "Scheduled Jobs", "scheduled_job")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("jobClass").withColumnName("job_class"))
            .addField(FieldDefinition.string("cronExpression").withColumnName("cron_expression"))
            .addField(FieldDefinition.string("timezone"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.datetime("lastExecutedAt").withColumnName("last_executed_at"))
            .addField(FieldDefinition.datetime("nextExecutionAt").withColumnName("next_execution_at"))
            .build();
    }

    // =========================================================================
    // Communication Collections
    // =========================================================================

    public static CollectionDefinition emailTemplates() {
        return systemBuilder("email-templates", "Email Templates", "email_template")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.string("subject"))
            .addField(FieldDefinition.string("bodyHtml").withColumnName("body_html"))
            .addField(FieldDefinition.string("bodyText").withColumnName("body_text"))
            .addField(FieldDefinition.string("relatedCollectionId").withColumnName("related_collection_id"))
            .addField(FieldDefinition.string("folder"))
            .addField(FieldDefinition.bool("isActive").withColumnName("is_active"))
            .addField(FieldDefinition.string("createdBy").withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition webhooks() {
        return systemBuilder("webhooks", "Webhooks", "webhook")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.requiredString("url"))
            .addField(FieldDefinition.json("events"))
            .addField(FieldDefinition.string("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.string("filterFormula").withColumnName("filter_formula"))
            .addField(FieldDefinition.json("headers"))
            .addField(FieldDefinition.string("secret"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.json("retryPolicy").withColumnName("retry_policy"))
            .addField(FieldDefinition.string("createdBy").withColumnName("created_by"))
            .build();
    }

    // =========================================================================
    // Integration Collections
    // =========================================================================

    public static CollectionDefinition connectedApps() {
        return systemBuilder("connected-apps", "Connected Apps", "connected_app")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.string("clientId").withColumnName("client_id"))
            .addField(FieldDefinition.string("clientSecretHash").withColumnName("client_secret_hash"))
            .addField(FieldDefinition.json("redirectUris").withColumnName("redirect_uris"))
            .addField(FieldDefinition.json("scopes"))
            .addField(FieldDefinition.json("ipRestrictions").withColumnName("ip_restrictions"))
            .addField(FieldDefinition.integer("rateLimitPerHour").withColumnName("rate_limit_per_hour"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.datetime("lastUsedAt").withColumnName("last_used_at"))
            .addField(FieldDefinition.string("createdBy").withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition oidcProviders() {
        return systemBuilder("oidc-providers", "OIDC Providers", "oidc_provider")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.requiredString("issuer"))
            .addField(FieldDefinition.requiredString("jwksUri").withColumnName("jwks_uri"))
            .addField(FieldDefinition.bool("active"))
            .addField(FieldDefinition.string("clientId").withColumnName("client_id"))
            .addField(FieldDefinition.string("audience"))
            .addField(FieldDefinition.string("rolesClaim").withColumnName("roles_claim"))
            .addField(FieldDefinition.string("rolesMapping").withColumnName("roles_mapping"))
            .addField(FieldDefinition.string("emailClaim").withColumnName("email_claim"))
            .addField(FieldDefinition.string("usernameClaim").withColumnName("username_claim"))
            .addField(FieldDefinition.string("nameClaim").withColumnName("name_claim"))
            .build();
    }

    // =========================================================================
    // Reports & Dashboards
    // =========================================================================

    public static CollectionDefinition reports() {
        return systemBuilder("reports", "Reports", "report")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.string("reportType").withColumnName("report_type"))
            .addField(FieldDefinition.string("primaryCollectionId").withColumnName("primary_collection_id"))
            .addField(FieldDefinition.json("relatedJoins").withColumnName("related_joins"))
            .addField(FieldDefinition.json("columns"))
            .addField(FieldDefinition.json("filters"))
            .addField(FieldDefinition.string("filterLogic").withColumnName("filter_logic"))
            .addField(FieldDefinition.json("rowGroupings").withColumnName("row_groupings"))
            .addField(FieldDefinition.json("columnGroupings").withColumnName("column_groupings"))
            .addField(FieldDefinition.json("sortOrder").withColumnName("sort_order"))
            .addField(FieldDefinition.string("chartType").withColumnName("chart_type"))
            .addField(FieldDefinition.json("chartConfig").withColumnName("chart_config"))
            .addField(FieldDefinition.string("scope"))
            .addField(FieldDefinition.string("folderId").withColumnName("folder_id"))
            .addField(FieldDefinition.string("accessLevel").withColumnName("access_level"))
            .addField(FieldDefinition.string("createdBy").withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition reportFolders() {
        return systemBuilder("report-folders", "Report Folders", "report_folder")
            .addField(FieldDefinition.string("parentFolderId").withColumnName("parent_folder_id"))
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.string("accessLevel").withColumnName("access_level"))
            .build();
    }

    public static CollectionDefinition dashboards() {
        return systemBuilder("dashboards", "Dashboards", "user_dashboard")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.json("config"))
            .addField(FieldDefinition.string("createdBy").withColumnName("created_by"))
            .build();
    }

    // =========================================================================
    // Collaboration Collections
    // =========================================================================

    public static CollectionDefinition notes() {
        return systemBuilder("notes", "Notes", "note")
            .addField(FieldDefinition.requiredString("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("recordId").withColumnName("record_id"))
            .addField(FieldDefinition.requiredString("content"))
            .addField(FieldDefinition.requiredString("createdBy").withColumnName("created_by"))
            .build();
    }

    public static CollectionDefinition attachments() {
        return systemBuilder("attachments", "Attachments", "file_attachment")
            .addField(FieldDefinition.requiredString("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("recordId").withColumnName("record_id"))
            .addField(FieldDefinition.requiredString("fileName").withColumnName("file_name"))
            .addField(FieldDefinition.longField("fileSize").withColumnName("file_size"))
            .addField(FieldDefinition.requiredString("contentType").withColumnName("content_type"))
            .addField(FieldDefinition.string("storageKey").withColumnName("storage_key"))
            .addField(FieldDefinition.requiredString("uploadedBy").withColumnName("uploaded_by"))
            .addField(FieldDefinition.datetime("uploadedAt").withColumnName("uploaded_at"))
            .build();
    }

    // =========================================================================
    // Platform Management Collections
    // =========================================================================

    public static CollectionDefinition workers() {
        return systemBuilder("workers", "Workers", "worker")
            .tenantScoped(false)
            .addField(FieldDefinition.requiredString("hostname"))
            .addField(FieldDefinition.datetime("lastHeartbeat").withColumnName("last_heartbeat"))
            .addField(FieldDefinition.string("status"))
            .build();
    }

    public static CollectionDefinition collectionAssignments() {
        return systemBuilder("collection-assignments", "Collection Assignments", "collection_assignment")
            .tenantScoped(false)
            .addField(FieldDefinition.requiredString("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("workerId").withColumnName("worker_id"))
            .addField(FieldDefinition.string("status"))
            .build();
    }

    public static CollectionDefinition bulkJobs() {
        return systemBuilder("bulk-jobs", "Bulk Jobs", "bulk_job")
            .addField(FieldDefinition.requiredString("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.requiredString("operation"))
            .addField(FieldDefinition.string("status"))
            .addField(FieldDefinition.integer("totalRecords").withColumnName("total_records"))
            .addField(FieldDefinition.integer("processedRecords").withColumnName("processed_records"))
            .addField(FieldDefinition.integer("successRecords").withColumnName("success_records"))
            .addField(FieldDefinition.integer("errorRecords").withColumnName("error_records"))
            .addField(FieldDefinition.string("externalIdField").withColumnName("external_id_field"))
            .addField(FieldDefinition.string("contentType").withColumnName("content_type"))
            .addField(FieldDefinition.integer("batchSize").withColumnName("batch_size"))
            .addField(FieldDefinition.string("createdBy").withColumnName("created_by"))
            .addField(FieldDefinition.datetime("startedAt").withColumnName("started_at"))
            .addField(FieldDefinition.datetime("completedAt").withColumnName("completed_at"))
            .build();
    }

    public static CollectionDefinition packages() {
        return systemBuilder("packages", "Packages", "package")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.requiredString("version"))
            .addField(FieldDefinition.string("description"))
            .build();
    }

    public static CollectionDefinition migrationRuns() {
        return systemBuilder("migration-runs", "Migration Runs", "migration_run")
            .addField(FieldDefinition.string("sourceTenantId").withColumnName("source_tenant_id"))
            .addField(FieldDefinition.string("status"))
            .addField(FieldDefinition.datetime("startedAt").withColumnName("started_at"))
            .addField(FieldDefinition.datetime("completedAt").withColumnName("completed_at"))
            .build();
    }

    // =========================================================================
    // Read-Only Audit/Log Collections
    // =========================================================================

    public static CollectionDefinition securityAuditLogs() {
        return readOnlySystemBuilder("security-audit-logs", "Security Audit Logs", "security_audit_log")
            .addField(FieldDefinition.string("eventType").withColumnName("event_type"))
            .addField(FieldDefinition.string("userId").withColumnName("user_id"))
            .addField(FieldDefinition.string("resourceType").withColumnName("resource_type"))
            .addField(FieldDefinition.string("resourceId").withColumnName("resource_id"))
            .addField(FieldDefinition.string("action"))
            .addField(FieldDefinition.string("result"))
            .addField(FieldDefinition.json("details"))
            .build();
    }

    public static CollectionDefinition setupAuditEntries() {
        return readOnlySystemBuilder("setup-audit-entries", "Setup Audit Entries", "setup_audit_trail")
            .addField(FieldDefinition.string("entityType").withColumnName("entity_type"))
            .addField(FieldDefinition.string("entityId").withColumnName("entity_id"))
            .addField(FieldDefinition.string("action"))
            .addField(FieldDefinition.json("oldValues").withColumnName("old_values"))
            .addField(FieldDefinition.json("newValues").withColumnName("new_values"))
            .addField(FieldDefinition.string("changedBy").withColumnName("changed_by"))
            .build();
    }

    public static CollectionDefinition fieldHistory() {
        return readOnlySystemBuilder("field-history", "Field History", "field_history")
            .tenantScoped(false)
            .addField(FieldDefinition.string("fieldId").withColumnName("field_id"))
            .addField(FieldDefinition.integer("version"))
            .addField(FieldDefinition.json("oldDefinition").withColumnName("old_definition"))
            .build();
    }

    public static CollectionDefinition workflowExecutionLogs() {
        return readOnlySystemBuilder("workflow-execution-logs", "Workflow Execution Logs", "workflow_execution_log")
            .tenantScoped(false)
            .addField(FieldDefinition.string("workflowRuleId").withColumnName("workflow_rule_id"))
            .addField(FieldDefinition.string("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.string("recordId").withColumnName("record_id"))
            .addField(FieldDefinition.string("status"))
            .addField(FieldDefinition.datetime("startedAt").withColumnName("started_at"))
            .addField(FieldDefinition.datetime("completedAt").withColumnName("completed_at"))
            .build();
    }

    public static CollectionDefinition emailLogs() {
        return readOnlySystemBuilder("email-logs", "Email Logs", "email_log")
            .addField(FieldDefinition.string("collectionId").withColumnName("collection_id"))
            .addField(FieldDefinition.string("recordId").withColumnName("record_id"))
            .addField(FieldDefinition.string("recipient"))
            .addField(FieldDefinition.string("subject"))
            .addField(FieldDefinition.string("body"))
            .addField(FieldDefinition.string("status"))
            .addField(FieldDefinition.datetime("sentAt").withColumnName("sent_at"))
            .addField(FieldDefinition.string("deliveryStatus").withColumnName("delivery_status"))
            .addField(FieldDefinition.string("errorMessage").withColumnName("error_message"))
            .build();
    }

    public static CollectionDefinition webhookDeliveries() {
        return readOnlySystemBuilder("webhook-deliveries", "Webhook Deliveries", "webhook_delivery")
            .tenantScoped(false)
            .addField(FieldDefinition.string("webhookId").withColumnName("webhook_id"))
            .addField(FieldDefinition.string("eventType").withColumnName("event_type"))
            .addField(FieldDefinition.json("payload"))
            .addField(FieldDefinition.integer("responseStatus").withColumnName("response_status"))
            .addField(FieldDefinition.string("responseBody").withColumnName("response_body"))
            .addField(FieldDefinition.integer("attemptCount").withColumnName("attempt_count"))
            .addField(FieldDefinition.string("status"))
            .addField(FieldDefinition.datetime("nextRetryAt").withColumnName("next_retry_at"))
            .addField(FieldDefinition.datetime("deliveredAt").withColumnName("delivered_at"))
            .build();
    }

    public static CollectionDefinition loginHistory() {
        return readOnlySystemBuilder("login-history", "Login History", "login_history")
            .addField(FieldDefinition.string("userId").withColumnName("user_id"))
            .addField(FieldDefinition.datetime("loginTime").withColumnName("login_time"))
            .addField(FieldDefinition.string("sourceIp").withColumnName("source_ip"))
            .addField(FieldDefinition.string("loginType").withColumnName("login_type"))
            .addField(FieldDefinition.string("status"))
            .addField(FieldDefinition.string("userAgent").withColumnName("user_agent"))
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
