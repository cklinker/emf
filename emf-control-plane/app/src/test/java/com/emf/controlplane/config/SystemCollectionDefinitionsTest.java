package com.emf.controlplane.config;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import com.emf.runtime.model.StorageMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SystemCollectionDefinitions — verifies all system collection definitions
 * are correctly configured with proper table names, field counts, scoping, and read-only flags.
 */
@DisplayName("SystemCollectionDefinitions")
class SystemCollectionDefinitionsTest {

    @Test
    @DisplayName("all() should return at least 76 system collection definitions")
    void allShouldReturnExpectedDefinitions() {
        List<CollectionDefinition> all = SystemCollectionDefinitions.all();
        assertTrue(all.size() >= 76,
                "Expected at least 76 system collection definitions, got: " + all.size());
    }

    @Test
    @DisplayName("all() should return unmodifiable list")
    void allShouldReturnUnmodifiableList() {
        List<CollectionDefinition> all = SystemCollectionDefinitions.all();
        assertThrows(UnsupportedOperationException.class, () -> all.add(null));
    }

    @Test
    @DisplayName("all definitions should have unique names")
    void allDefinitionsShouldHaveUniqueNames() {
        List<CollectionDefinition> all = SystemCollectionDefinitions.all();
        Set<String> names = all.stream().map(CollectionDefinition::name).collect(Collectors.toSet());
        assertEquals(all.size(), names.size(), "All collection names should be unique");
    }

    @Test
    @DisplayName("all definitions should be system collections")
    void allDefinitionsShouldBeSystemCollections() {
        for (CollectionDefinition def : SystemCollectionDefinitions.all()) {
            assertTrue(def.systemCollection(),
                    "Collection '" + def.name() + "' should be a system collection");
        }
    }

    @Test
    @DisplayName("all definitions should have at least one field")
    void allDefinitionsShouldHaveAtLeastOneField() {
        for (CollectionDefinition def : SystemCollectionDefinitions.all()) {
            assertFalse(def.fields().isEmpty(),
                    "Collection '" + def.name() + "' should have at least one field");
        }
    }

    @Test
    @DisplayName("all definitions should have physical table storage mode")
    void allDefinitionsShouldHavePhysicalTableStorage() {
        for (CollectionDefinition def : SystemCollectionDefinitions.all()) {
            assertNotNull(def.storageConfig(),
                    "Collection '" + def.name() + "' should have storage config");
            assertEquals(StorageMode.PHYSICAL_TABLES, def.storageConfig().mode(),
                    "Collection '" + def.name() + "' should use PHYSICAL_TABLES storage mode");
            assertNotNull(def.storageConfig().tableName(),
                    "Collection '" + def.name() + "' should have a table name");
            assertFalse(def.storageConfig().tableName().startsWith("tbl_"),
                    "Collection '" + def.name() + "' should NOT use tbl_ prefix (uses existing Flyway tables)");
        }
    }

    @Test
    @DisplayName("all definitions should have API config")
    void allDefinitionsShouldHaveApiConfig() {
        for (CollectionDefinition def : SystemCollectionDefinitions.all()) {
            assertNotNull(def.apiConfig(),
                    "Collection '" + def.name() + "' should have API config");
            assertTrue(def.apiConfig().basePath().startsWith("/api/"),
                    "Collection '" + def.name() + "' should have API path starting with /api/");
        }
    }

    @Test
    @DisplayName("byName() should return map with all definitions")
    void byNameShouldReturnMapWithAllDefinitions() {
        Map<String, CollectionDefinition> byName = SystemCollectionDefinitions.byName();
        assertEquals(SystemCollectionDefinitions.all().size(), byName.size());
        assertNotNull(byName.get("users"));
        assertNotNull(byName.get("tenants"));
        assertNotNull(byName.get("collections"));
    }

    @Test
    @DisplayName("byName() should return unmodifiable map")
    void byNameShouldReturnUnmodifiableMap() {
        Map<String, CollectionDefinition> byName = SystemCollectionDefinitions.byName();
        assertThrows(UnsupportedOperationException.class, () -> byName.put("test", null));
    }

    // =========================================================================
    // Non-Tenant-Scoped Collections
    // =========================================================================

    @Nested
    @DisplayName("Tenant scoping")
    class TenantScopingTests {

        @Test
        @DisplayName("tenants should NOT be tenant-scoped")
        void tenantsShouldNotBeTenantScoped() {
            assertFalse(SystemCollectionDefinitions.tenants().tenantScoped());
        }

        @Test
        @DisplayName("fields should NOT be tenant-scoped")
        void fieldsShouldNotBeTenantScoped() {
            assertFalse(SystemCollectionDefinitions.fields().tenantScoped());
        }

        @Test
        @DisplayName("layout-assignments SHOULD be tenant-scoped (table has tenant_id)")
        void layoutAssignmentsShouldBeTenantScoped() {
            assertTrue(SystemCollectionDefinitions.layoutAssignments().tenantScoped());
        }

        @Test
        @DisplayName("picklist-values should NOT be tenant-scoped")
        void picklistValuesShouldNotBeTenantScoped() {
            assertFalse(SystemCollectionDefinitions.picklistValues().tenantScoped());
        }

        @Test
        @DisplayName("workflow-action-types should NOT be tenant-scoped")
        void workflowActionTypesShouldNotBeTenantScoped() {
            assertFalse(SystemCollectionDefinitions.workflowActionTypes().tenantScoped());
        }

        @Test
        @DisplayName("workers should NOT be tenant-scoped")
        void workersShouldNotBeTenantScoped() {
            assertFalse(SystemCollectionDefinitions.workers().tenantScoped());
        }

        @Test
        @DisplayName("collection-assignments should NOT be tenant-scoped")
        void collectionAssignmentsShouldNotBeTenantScoped() {
            assertFalse(SystemCollectionDefinitions.collectionAssignments().tenantScoped());
        }

        @Test
        @DisplayName("field-history SHOULD be tenant-scoped (table has tenant_id)")
        void fieldHistoryShouldBeTenantScoped() {
            assertTrue(SystemCollectionDefinitions.fieldHistory().tenantScoped());
        }

        @Test
        @DisplayName("workflow-execution-logs SHOULD be tenant-scoped (table has tenant_id)")
        void workflowExecutionLogsShouldBeTenantScoped() {
            assertTrue(SystemCollectionDefinitions.workflowExecutionLogs().tenantScoped());
        }

        @Test
        @DisplayName("webhook-deliveries should NOT be tenant-scoped")
        void webhookDeliveriesShouldNotBeTenantScoped() {
            assertFalse(SystemCollectionDefinitions.webhookDeliveries().tenantScoped());
        }

        @Test
        @DisplayName("most collections should be tenant-scoped")
        void mostCollectionsShouldBeTenantScoped() {
            assertTrue(SystemCollectionDefinitions.users().tenantScoped());
            assertTrue(SystemCollectionDefinitions.profiles().tenantScoped());
            assertTrue(SystemCollectionDefinitions.permissionSets().tenantScoped());
            assertTrue(SystemCollectionDefinitions.collections().tenantScoped());
            assertTrue(SystemCollectionDefinitions.pageLayouts().tenantScoped());
            assertTrue(SystemCollectionDefinitions.workflowRules().tenantScoped());
            assertTrue(SystemCollectionDefinitions.emailTemplates().tenantScoped());
            assertTrue(SystemCollectionDefinitions.webhooks().tenantScoped());
            assertTrue(SystemCollectionDefinitions.reports().tenantScoped());
            assertTrue(SystemCollectionDefinitions.dashboards().tenantScoped());
        }
    }

    // =========================================================================
    // Read-Only Collections
    // =========================================================================

    @Nested
    @DisplayName("Read-only collections")
    class ReadOnlyTests {

        private static final List<String> READ_ONLY_COLLECTIONS = List.of(
                "security-audit-logs", "setup-audit-entries", "field-history",
                "workflow-execution-logs", "workflow-action-logs", "workflow-rule-versions",
                "script-execution-logs", "flow-executions",
                "job-execution-logs", "bulk-job-results",
                "email-logs", "webhook-deliveries", "login-history",
                "collection-versions", "field-versions", "migration-steps"
        );

        @Test
        @DisplayName("all read-only collections should be marked as readOnly")
        void allReadOnlyCollectionsShouldBeMarkedReadOnly() {
            Map<String, CollectionDefinition> byName = SystemCollectionDefinitions.byName();
            for (String name : READ_ONLY_COLLECTIONS) {
                assertTrue(byName.get(name).readOnly(),
                        "Collection '" + name + "' should be read-only");
            }
        }

        @Test
        @DisplayName("read-only collections should have list and get enabled")
        void readOnlyCollectionsShouldHaveListAndGetEnabled() {
            Map<String, CollectionDefinition> byName = SystemCollectionDefinitions.byName();
            for (String name : READ_ONLY_COLLECTIONS) {
                CollectionDefinition def = byName.get(name);
                assertTrue(def.apiConfig().listEnabled(),
                        "Read-only collection '" + name + "' should have list enabled");
                assertTrue(def.apiConfig().getEnabled(),
                        "Read-only collection '" + name + "' should have get enabled");
                assertFalse(def.apiConfig().createEnabled(),
                        "Read-only collection '" + name + "' should NOT have create enabled");
            }
        }

        @Test
        @DisplayName("CRUD collections should NOT be read-only")
        void crudCollectionsShouldNotBeReadOnly() {
            assertFalse(SystemCollectionDefinitions.users().readOnly());
            assertFalse(SystemCollectionDefinitions.tenants().readOnly());
            assertFalse(SystemCollectionDefinitions.collections().readOnly());
            assertFalse(SystemCollectionDefinitions.profiles().readOnly());
            assertFalse(SystemCollectionDefinitions.workflowRules().readOnly());
        }

        @Test
        @DisplayName("should have exactly 16 read-only collections")
        void shouldHaveExpectedReadOnlyCollections() {
            long readOnlyCount = SystemCollectionDefinitions.all().stream()
                    .filter(CollectionDefinition::readOnly)
                    .count();
            assertEquals(READ_ONLY_COLLECTIONS.size(), readOnlyCount,
                    "Number of read-only collections should match");
        }
    }

    // =========================================================================
    // Specific Collection Definitions
    // =========================================================================

    @Nested
    @DisplayName("Core entity collections")
    class CoreCollectionTests {

        @Test
        @DisplayName("tenants collection should have correct table and fields")
        void tenantsCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.tenants();
            assertEquals("tenants", def.name());
            assertEquals("Tenants", def.displayName());
            assertEquals("tenant", def.storageConfig().tableName());
            assertFalse(def.tenantScoped());
            assertFalse(def.readOnly());

            assertFieldExists(def, "slug");
            assertFieldExists(def, "name");
            assertFieldExists(def, "edition");
            assertFieldExists(def, "status");
            assertFieldExists(def, "settings");
            assertFieldExists(def, "limits");
        }

        @Test
        @DisplayName("users collection should have correct table and fields")
        void usersCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.users();
            assertEquals("users", def.name());
            assertEquals("Users", def.displayName());
            assertEquals("platform_user", def.storageConfig().tableName());
            assertTrue(def.tenantScoped());
            assertTrue(def.immutableFields().contains("tenantId"));

            assertFieldExists(def, "email");
            assertFieldExists(def, "username");
            assertFieldHasColumnName(def, "firstName", "first_name");
            assertFieldHasColumnName(def, "lastName", "last_name");
            assertFieldHasColumnName(def, "profileId", "profile_id");
            assertFieldHasColumnName(def, "managerId", "manager_id");
            assertFieldHasColumnName(def, "lastLoginAt", "last_login_at");
            assertFieldHasColumnName(def, "loginCount", "login_count");
            assertFieldHasColumnName(def, "mfaEnabled", "mfa_enabled");
        }

        @Test
        @DisplayName("profiles collection should have correct table and fields")
        void profilesCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.profiles();
            assertEquals("profiles", def.name());
            assertEquals("profile", def.storageConfig().tableName());
            assertTrue(def.tenantScoped());

            assertFieldExists(def, "name");
            assertFieldExists(def, "description");
            assertFieldHasColumnName(def, "isSystem", "is_system");
        }

        @Test
        @DisplayName("permission-sets collection should have correct table")
        void permissionSetsCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.permissionSets();
            assertEquals("permission-sets", def.name());
            assertEquals("permission_set", def.storageConfig().tableName());
            assertTrue(def.tenantScoped());
        }

        @Test
        @DisplayName("collections collection should have correct table and fields")
        void collectionsCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.collections();
            assertEquals("collections", def.name());
            assertEquals("collection", def.storageConfig().tableName());
            assertTrue(def.tenantScoped());

            assertFieldHasColumnName(def, "displayName", "display_name");
            assertFieldHasColumnName(def, "storageMode", "storage_mode");
            assertFieldHasColumnName(def, "systemCollection", "system_collection");
            assertFieldHasColumnName(def, "currentVersion", "current_version");
        }

        @Test
        @DisplayName("fields collection should have correct table and NOT be tenant-scoped")
        void fieldsCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.fields();
            assertEquals("fields", def.name());
            assertEquals("field", def.storageConfig().tableName());
            assertFalse(def.tenantScoped());

            assertFieldHasColumnName(def, "collectionId", "collection_id");
            assertFieldHasColumnName(def, "displayName", "display_name");
            assertFieldHasColumnName(def, "uniqueConstraint", "unique_constraint");
            assertFieldHasColumnName(def, "defaultValue", "default_value");
            assertFieldHasColumnName(def, "referenceTarget", "reference_target");
            assertFieldHasColumnName(def, "fieldOrder", "field_order");
            assertFieldHasColumnName(def, "fieldTypeConfig", "field_type_config");
        }
    }

    @Nested
    @DisplayName("Workflow and automation collections")
    class WorkflowCollectionTests {

        @Test
        @DisplayName("workflow-rules should have correct column mappings")
        void workflowRulesColumnMappings() {
            CollectionDefinition def = SystemCollectionDefinitions.workflowRules();
            assertEquals("workflow_rule", def.storageConfig().tableName());

            assertFieldHasColumnName(def, "collectionId", "collection_id");
            assertFieldHasColumnName(def, "triggerType", "trigger_type");
            assertFieldHasColumnName(def, "filterFormula", "filter_formula");
            assertFieldHasColumnName(def, "reEvaluateOnUpdate", "re_evaluate_on_update");
            assertFieldHasColumnName(def, "executionOrder", "execution_order");
            assertFieldHasColumnName(def, "errorHandling", "error_handling");
            assertFieldHasColumnName(def, "triggerFields", "trigger_fields");
            assertFieldHasColumnName(def, "cronExpression", "cron_expression");
            assertFieldHasColumnName(def, "lastScheduledRun", "last_scheduled_run");
            assertFieldHasColumnName(def, "executionMode", "execution_mode");
        }

        @Test
        @DisplayName("validation-rules should have correct table")
        void validationRulesTable() {
            CollectionDefinition def = SystemCollectionDefinitions.validationRules();
            assertEquals("validation_rule", def.storageConfig().tableName());
            assertFieldHasColumnName(def, "errorConditionFormula", "error_condition_formula");
            assertFieldHasColumnName(def, "errorMessage", "error_message");
        }

        @Test
        @DisplayName("approval-processes should have correct table")
        void approvalProcessesTable() {
            CollectionDefinition def = SystemCollectionDefinitions.approvalProcesses();
            assertEquals("approval_process", def.storageConfig().tableName());
            assertFieldHasColumnName(def, "entryCriteria", "entry_criteria");
            assertFieldHasColumnName(def, "initialSubmitterField", "initial_submitter_field");
        }
    }

    @Nested
    @DisplayName("Integration collections")
    class IntegrationCollectionTests {

        @Test
        @DisplayName("connected-apps should have correct table and fields")
        void connectedAppsCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.connectedApps();
            assertEquals("connected_app", def.storageConfig().tableName());
            assertFieldHasColumnName(def, "clientId", "client_id");
            assertFieldHasColumnName(def, "clientSecretHash", "client_secret_hash");
            assertFieldHasColumnName(def, "redirectUris", "redirect_uris");
            assertFieldHasColumnName(def, "rateLimitPerHour", "rate_limit_per_hour");
        }

        @Test
        @DisplayName("oidc-providers should have correct table and fields")
        void oidcProvidersCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.oidcProviders();
            assertEquals("oidc_provider", def.storageConfig().tableName());
            assertFieldHasColumnName(def, "jwksUri", "jwks_uri");
            assertFieldHasColumnName(def, "rolesClaim", "roles_claim");
            assertFieldHasColumnName(def, "emailClaim", "email_claim");
        }

        @Test
        @DisplayName("webhooks should have correct table")
        void webhooksCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.webhooks();
            assertEquals("webhook", def.storageConfig().tableName());
            assertFieldHasColumnName(def, "filterFormula", "filter_formula");
            assertFieldHasColumnName(def, "retryPolicy", "retry_policy");
        }
    }

    @Nested
    @DisplayName("Read-only audit collections")
    class AuditCollectionTests {

        @Test
        @DisplayName("security-audit-logs should be read-only with correct table")
        void securityAuditLogsCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.securityAuditLogs();
            assertEquals("security_audit_log", def.storageConfig().tableName());
            assertTrue(def.readOnly());
            assertTrue(def.systemCollection());
            assertFieldHasColumnName(def, "eventType", "event_type");
            assertFieldHasColumnName(def, "eventCategory", "event_category");
            assertFieldHasColumnName(def, "actorUserId", "actor_user_id");
        }

        @Test
        @DisplayName("login-history should be read-only with correct table")
        void loginHistoryCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.loginHistory();
            assertEquals("login_history", def.storageConfig().tableName());
            assertTrue(def.readOnly());
            assertFieldHasColumnName(def, "loginTime", "login_time");
            assertFieldHasColumnName(def, "sourceIp", "source_ip");
            assertFieldHasColumnName(def, "loginType", "login_type");
            assertFieldHasColumnName(def, "userAgent", "user_agent");
        }

        @Test
        @DisplayName("webhook-deliveries should be read-only and not tenant-scoped")
        void webhookDeliveriesCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.webhookDeliveries();
            assertEquals("webhook_delivery", def.storageConfig().tableName());
            assertTrue(def.readOnly());
            assertFalse(def.tenantScoped());
            assertFieldHasColumnName(def, "webhookId", "webhook_id");
            assertFieldHasColumnName(def, "responseStatus", "response_status");
            assertFieldHasColumnName(def, "attemptCount", "attempt_count");
        }
    }

    @Nested
    @DisplayName("Table name verification")
    class TableNameTests {

        @Test
        @DisplayName("all table names should map to correct Flyway tables")
        void allTableNamesShouldMapCorrectly() {
            Map<String, String> expectedTables = Map.ofEntries(
                    Map.entry("tenants", "tenant"),
                    Map.entry("users", "platform_user"),
                    Map.entry("profiles", "profile"),
                    Map.entry("permission-sets", "permission_set"),
                    Map.entry("collections", "collection"),
                    Map.entry("fields", "field"),
                    Map.entry("page-layouts", "page_layout"),
                    Map.entry("layout-assignments", "layout_assignment"),
                    Map.entry("list-views", "list_view"),
                    Map.entry("ui-pages", "ui_page"),
                    Map.entry("ui-menus", "ui_menu"),
                    Map.entry("global-picklists", "global_picklist"),
                    Map.entry("picklist-values", "picklist_value"),
                    Map.entry("record-types", "record_type"),
                    Map.entry("validation-rules", "validation_rule"),
                    Map.entry("workflow-rules", "workflow_rule"),
                    Map.entry("workflow-action-types", "workflow_action_type"),
                    Map.entry("scripts", "script"),
                    Map.entry("flows", "flow"),
                    Map.entry("approval-processes", "approval_process"),
                    Map.entry("scheduled-jobs", "scheduled_job"),
                    Map.entry("email-templates", "email_template"),
                    Map.entry("webhooks", "webhook"),
                    Map.entry("connected-apps", "connected_app"),
                    Map.entry("oidc-providers", "oidc_provider"),
                    Map.entry("reports", "report"),
                    Map.entry("report-folders", "report_folder"),
                    Map.entry("dashboards", "dashboard"),
                    Map.entry("notes", "note"),
                    Map.entry("attachments", "file_attachment"),
                    Map.entry("workers", "worker"),
                    Map.entry("collection-assignments", "collection_assignment"),
                    Map.entry("bulk-jobs", "bulk_job"),
                    Map.entry("packages", "package"),
                    Map.entry("migration-runs", "migration_run"),
                    Map.entry("security-audit-logs", "security_audit_log"),
                    Map.entry("setup-audit-entries", "setup_audit_trail"),
                    Map.entry("field-history", "field_history"),
                    Map.entry("workflow-execution-logs", "workflow_execution_log"),
                    Map.entry("email-logs", "email_log"),
                    Map.entry("webhook-deliveries", "webhook_delivery"),
                    Map.entry("login-history", "login_history")
            );

            Map<String, CollectionDefinition> byName = SystemCollectionDefinitions.byName();

            for (Map.Entry<String, String> entry : expectedTables.entrySet()) {
                String collectionName = entry.getKey();
                String expectedTableName = entry.getValue();
                CollectionDefinition def = byName.get(collectionName);
                assertNotNull(def, "Collection '" + collectionName + "' should exist");
                assertEquals(expectedTableName, def.storageConfig().tableName(),
                        "Collection '" + collectionName + "' should map to table '" + expectedTableName + "'");
            }
        }
    }

    // =========================================================================
    // Relationship Tests (CRITICAL for ?include= resolution)
    // =========================================================================

    @Nested
    @DisplayName("Relationship fields (LOOKUP and MASTER_DETAIL)")
    class RelationshipTests {

        @Test
        @DisplayName("users.profileId should be a LOOKUP to profiles")
        void usersProfileIdShouldBeLookup() {
            FieldDefinition field = getField(SystemCollectionDefinitions.users(), "profileId");
            assertEquals(FieldType.LOOKUP, field.type());
            assertNotNull(field.referenceConfig());
            assertEquals("profiles", field.referenceConfig().targetCollection());
            assertTrue(field.referenceConfig().isLookup());
            assertTrue(field.nullable());
        }

        @Test
        @DisplayName("fields.collectionId should be a MASTER_DETAIL to collections")
        void fieldsCollectionIdShouldBeMasterDetail() {
            FieldDefinition field = getField(SystemCollectionDefinitions.fields(), "collectionId");
            assertEquals(FieldType.MASTER_DETAIL, field.type());
            assertNotNull(field.referenceConfig());
            assertEquals("collections", field.referenceConfig().targetCollection());
            assertTrue(field.referenceConfig().isMasterDetail());
            assertTrue(field.referenceConfig().cascadeDelete());
            assertFalse(field.nullable());
        }

        @Test
        @DisplayName("page-layouts.collectionId should be MASTER_DETAIL to collections")
        void pageLayoutsCollectionIdShouldBeMasterDetail() {
            FieldDefinition field = getField(SystemCollectionDefinitions.pageLayouts(), "collectionId");
            assertEquals(FieldType.MASTER_DETAIL, field.type());
            assertEquals("collections", field.referenceConfig().targetCollection());
        }

        @Test
        @DisplayName("workflow-rules.collectionId should be MASTER_DETAIL to collections")
        void workflowRulesCollectionIdShouldBeMasterDetail() {
            FieldDefinition field = getField(SystemCollectionDefinitions.workflowRules(), "collectionId");
            assertEquals(FieldType.MASTER_DETAIL, field.type());
            assertEquals("collections", field.referenceConfig().targetCollection());
        }

        @Test
        @DisplayName("reports.primaryCollectionId should be MASTER_DETAIL to collections")
        void reportsPrimaryCollectionIdShouldBeMasterDetail() {
            FieldDefinition field = getField(SystemCollectionDefinitions.reports(), "primaryCollectionId");
            assertEquals(FieldType.MASTER_DETAIL, field.type());
            assertEquals("collections", field.referenceConfig().targetCollection());
        }

        @Test
        @DisplayName("reports.folderId should be LOOKUP to report-folders")
        void reportsFolderIdShouldBeLookup() {
            FieldDefinition field = getField(SystemCollectionDefinitions.reports(), "folderId");
            assertEquals(FieldType.LOOKUP, field.type());
            assertEquals("report-folders", field.referenceConfig().targetCollection());
        }

        @Test
        @DisplayName("dashboards.folderId should be LOOKUP to report-folders")
        void dashboardsFolderIdShouldBeLookup() {
            FieldDefinition field = getField(SystemCollectionDefinitions.dashboards(), "folderId");
            assertEquals(FieldType.LOOKUP, field.type());
            assertEquals("report-folders", field.referenceConfig().targetCollection());
        }

        @Test
        @DisplayName("webhook-deliveries.webhookId should be MASTER_DETAIL to webhooks")
        void webhookDeliveriesWebhookIdShouldBeMasterDetail() {
            FieldDefinition field = getField(SystemCollectionDefinitions.webhookDeliveries(), "webhookId");
            assertEquals(FieldType.MASTER_DETAIL, field.type());
            assertEquals("webhooks", field.referenceConfig().targetCollection());
        }

        @Test
        @DisplayName("email-logs.templateId should be LOOKUP to email-templates")
        void emailLogsTemplateIdShouldBeLookup() {
            FieldDefinition field = getField(SystemCollectionDefinitions.emailLogs(), "templateId");
            assertEquals(FieldType.LOOKUP, field.type());
            assertEquals("email-templates", field.referenceConfig().targetCollection());
        }

        @Test
        @DisplayName("workflow-execution-logs.workflowRuleId should be MASTER_DETAIL")
        void workflowExecutionLogsWorkflowRuleIdShouldBeMasterDetail() {
            FieldDefinition field = getField(SystemCollectionDefinitions.workflowExecutionLogs(), "workflowRuleId");
            assertEquals(FieldType.MASTER_DETAIL, field.type());
            assertEquals("workflow-rules", field.referenceConfig().targetCollection());
        }

        @Test
        @DisplayName("collections.displayFieldId should be LOOKUP to fields")
        void collectionsDisplayFieldIdShouldBeLookup() {
            FieldDefinition field = getField(SystemCollectionDefinitions.collections(), "displayFieldId");
            assertEquals(FieldType.LOOKUP, field.type());
            assertEquals("fields", field.referenceConfig().targetCollection());
        }
    }

    // =========================================================================
    // Default Values and Enum Values
    // =========================================================================

    @Nested
    @DisplayName("Default values and enum constraints")
    class DefaultsAndEnumsTests {

        @Test
        @DisplayName("tenants.edition should have enum values and default")
        void tenantsEditionShouldHaveEnumAndDefault() {
            FieldDefinition field = getField(SystemCollectionDefinitions.tenants(), "edition");
            assertEquals("PROFESSIONAL", field.defaultValue());
            assertNotNull(field.enumValues());
            assertTrue(field.enumValues().contains("PROFESSIONAL"));
        }

        @Test
        @DisplayName("users.status should have default ACTIVE")
        void usersStatusShouldHaveDefaultActive() {
            FieldDefinition field = getField(SystemCollectionDefinitions.users(), "status");
            assertEquals("ACTIVE", field.defaultValue());
            assertNotNull(field.enumValues());
            assertTrue(field.enumValues().contains("ACTIVE"));
        }

        @Test
        @DisplayName("scheduled-jobs.jobType should have enum values")
        void scheduledJobsJobTypeShouldHaveEnums() {
            FieldDefinition field = getField(SystemCollectionDefinitions.scheduledJobs(), "jobType");
            assertNotNull(field.enumValues());
            assertTrue(field.enumValues().contains("FLOW"));
            assertTrue(field.enumValues().contains("SCRIPT"));
        }

        @Test
        @DisplayName("workers should have correct defaults")
        void workersShouldHaveCorrectDefaults() {
            CollectionDefinition def = SystemCollectionDefinitions.workers();
            assertEquals(8080, getField(def, "port").defaultValue());
            assertEquals("default", getField(def, "pool").defaultValue());
            assertEquals(50, getField(def, "capacity").defaultValue());
        }
    }

    // =========================================================================
    // Corrected Collection Definitions
    // =========================================================================

    @Nested
    @DisplayName("Corrected collection definitions")
    class CorrectedDefinitionTests {

        @Test
        @DisplayName("workers should have correct fields (host, port, baseUrl)")
        void workersCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.workers();
            assertFieldExists(def, "host");
            assertFieldExists(def, "port");
            assertFieldExists(def, "baseUrl");
            assertFieldExists(def, "pool");
            assertFieldExists(def, "capacity");
            assertFieldExists(def, "currentLoad");
            assertFieldExists(def, "podName");
        }

        @Test
        @DisplayName("scheduled-jobs should have jobType (not jobClass)")
        void scheduledJobsCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.scheduledJobs();
            assertFieldExists(def, "jobType");
            assertFieldHasColumnName(def, "jobType", "job_type");
        }

        @Test
        @DisplayName("ui-pages should have path, config, active (not slug, content)")
        void uiPagesCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.uiPages();
            assertFieldExists(def, "path");
            assertFieldExists(def, "config");
            assertFieldExists(def, "active");
            assertEquals(5, def.fields().size());
        }

        @Test
        @DisplayName("workflow-action-types should have key, category, handlerClass")
        void workflowActionTypesCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.workflowActionTypes();
            assertFieldExists(def, "key");
            assertFieldExists(def, "category");
            assertFieldExists(def, "handlerClass");
        }

        @Test
        @DisplayName("email-logs should have correct fields")
        void emailLogsCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.emailLogs();
            assertFieldExists(def, "templateId");
            assertFieldExists(def, "recipientEmail");
            assertFieldExists(def, "subject");
        }

        @Test
        @DisplayName("field-history should be tenant-scoped with correct fields")
        void fieldHistoryCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.fieldHistory();
            assertTrue(def.tenantScoped());
            assertFieldExists(def, "collectionId");
            assertFieldExists(def, "recordId");
            assertFieldExists(def, "fieldName");
            assertFieldExists(def, "changedBy");
            assertFieldExists(def, "changeSource");
        }

        @Test
        @DisplayName("dashboards should have all correct fields")
        void dashboardsCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.dashboards();
            assertFieldExists(def, "folderId");
            assertFieldExists(def, "accessLevel");
            assertFieldExists(def, "dynamic");
            assertFieldExists(def, "runningUserId");
            assertFieldExists(def, "columnCount");
            assertFieldExists(def, "createdBy");
        }

        @Test
        @DisplayName("migration-runs should have correct fields")
        void migrationRunsCollection() {
            CollectionDefinition def = SystemCollectionDefinitions.migrationRuns();
            assertFieldExists(def, "collectionId");
            assertFieldExists(def, "fromVersion");
            assertFieldExists(def, "toVersion");
            assertFieldExists(def, "status");
            assertFieldExists(def, "errorMessage");
        }
    }

    // =========================================================================
    // Immutable Fields
    // =========================================================================

    @Nested
    @DisplayName("Immutable fields")
    class ImmutableFieldTests {

        @Test
        @DisplayName("fields collection should have collectionId as immutable")
        void fieldsCollectionIdShouldBeImmutable() {
            assertTrue(SystemCollectionDefinitions.fields().immutableFields().contains("collectionId"));
        }

        @Test
        @DisplayName("notes should have collectionId and recordId as immutable")
        void notesImmutableFields() {
            CollectionDefinition def = SystemCollectionDefinitions.notes();
            assertTrue(def.immutableFields().contains("collectionId"));
            assertTrue(def.immutableFields().contains("recordId"));
        }

        @Test
        @DisplayName("connected-apps should have clientId as immutable")
        void connectedAppsClientIdShouldBeImmutable() {
            assertTrue(SystemCollectionDefinitions.connectedApps().immutableFields().contains("clientId"));
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private void assertFieldExists(CollectionDefinition def, String fieldName) {
        boolean found = def.fields().stream()
                .anyMatch(f -> f.name().equals(fieldName));
        assertTrue(found, "Collection '" + def.name() + "' should have field '" + fieldName + "'");
    }

    private void assertFieldHasColumnName(CollectionDefinition def, String fieldName, String expectedColumnName) {
        FieldDefinition field = def.fields().stream()
                .filter(f -> f.name().equals(fieldName))
                .findFirst()
                .orElse(null);
        assertNotNull(field, "Collection '" + def.name() + "' should have field '" + fieldName + "'");
        assertEquals(expectedColumnName, field.columnName(),
                "Field '" + fieldName + "' in collection '" + def.name() + "' should have column name '" + expectedColumnName + "'");
    }

    private static FieldDefinition getField(CollectionDefinition def, String fieldName) {
        return def.fields().stream()
                .filter(f -> f.name().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Collection '" + def.name() + "' should have field '" + fieldName + "'"));
    }
}
