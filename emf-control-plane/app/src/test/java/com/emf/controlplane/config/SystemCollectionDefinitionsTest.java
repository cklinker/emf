package com.emf.controlplane.config;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
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
    @DisplayName("all() should return 42 system collection definitions")
    void allShouldReturn42Definitions() {
        List<CollectionDefinition> all = SystemCollectionDefinitions.all();
        assertEquals(42, all.size());
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
        assertEquals(42, byName.size());
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
        @DisplayName("layout-assignments should NOT be tenant-scoped")
        void layoutAssignmentsShouldNotBeTenantScoped() {
            assertFalse(SystemCollectionDefinitions.layoutAssignments().tenantScoped());
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
        @DisplayName("field-history should NOT be tenant-scoped")
        void fieldHistoryShouldNotBeTenantScoped() {
            assertFalse(SystemCollectionDefinitions.fieldHistory().tenantScoped());
        }

        @Test
        @DisplayName("workflow-execution-logs should NOT be tenant-scoped")
        void workflowExecutionLogsShouldNotBeTenantScoped() {
            assertFalse(SystemCollectionDefinitions.workflowExecutionLogs().tenantScoped());
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
                "workflow-execution-logs", "email-logs", "webhook-deliveries", "login-history"
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
        @DisplayName("should have exactly 7 read-only collections")
        void shouldHaveExactly7ReadOnlyCollections() {
            long readOnlyCount = SystemCollectionDefinitions.all().stream()
                    .filter(CollectionDefinition::readOnly)
                    .count();
            assertEquals(7, readOnlyCount);
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
            assertTrue(def.immutableFields().contains("tenantId"));

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
            assertFieldHasColumnName(def, "userId", "user_id");
            assertFieldHasColumnName(def, "resourceType", "resource_type");
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
                    Map.entry("dashboards", "user_dashboard"),
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
}
