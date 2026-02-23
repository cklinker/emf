package com.emf.controlplane.config;

import com.emf.runtime.model.CollectionDefinition;

import java.util.List;
import java.util.Map;

/**
 * Thin delegation layer to the canonical definitions in runtime-core.
 *
 * <p>This class delegates all methods to
 * {@link com.emf.runtime.model.system.SystemCollectionDefinitions} which is now
 * the single source of truth for system collection definitions.
 *
 * @deprecated Use {@link com.emf.runtime.model.system.SystemCollectionDefinitions} directly.
 */
@Deprecated(forRemoval = true)
public final class SystemCollectionDefinitions {

    private static final com.emf.runtime.model.system.SystemCollectionDefinitions DELEGATE = null; // utility class

    private SystemCollectionDefinitions() {
        // Utility class
    }

    // =========================================================================
    // Core lookup methods
    // =========================================================================

    public static List<CollectionDefinition> all() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.all();
    }

    public static Map<String, CollectionDefinition> byName() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.byName();
    }

    // =========================================================================
    // Core Entity Collections
    // =========================================================================

    public static CollectionDefinition tenants() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.tenants();
    }

    public static CollectionDefinition users() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.users();
    }

    public static CollectionDefinition profiles() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.profiles();
    }

    public static CollectionDefinition permissionSets() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.permissionSets();
    }

    public static CollectionDefinition collections() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.collections();
    }

    public static CollectionDefinition fields() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.fields();
    }

    // =========================================================================
    // Groups & Membership
    // =========================================================================

    public static CollectionDefinition userGroups() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.userGroups();
    }

    public static CollectionDefinition groupMemberships() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.groupMemberships();
    }

    // =========================================================================
    // Permission Collections
    // =========================================================================

    public static CollectionDefinition profileSystemPermissions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.profileSystemPermissions();
    }

    public static CollectionDefinition profileObjectPermissions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.profileObjectPermissions();
    }

    public static CollectionDefinition profileFieldPermissions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.profileFieldPermissions();
    }

    public static CollectionDefinition permsetSystemPermissions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.permsetSystemPermissions();
    }

    public static CollectionDefinition permsetObjectPermissions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.permsetObjectPermissions();
    }

    public static CollectionDefinition permsetFieldPermissions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.permsetFieldPermissions();
    }

    // =========================================================================
    // Permission Assignments
    // =========================================================================

    public static CollectionDefinition userPermissionSets() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.userPermissionSets();
    }

    public static CollectionDefinition groupPermissionSets() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.groupPermissionSets();
    }

    // =========================================================================
    // UI & Layout
    // =========================================================================

    public static CollectionDefinition pageLayouts() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.pageLayouts();
    }

    public static CollectionDefinition layoutSections() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.layoutSections();
    }

    public static CollectionDefinition layoutFields() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.layoutFields();
    }

    public static CollectionDefinition layoutRelatedLists() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.layoutRelatedLists();
    }

    public static CollectionDefinition layoutAssignments() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.layoutAssignments();
    }

    public static CollectionDefinition listViews() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.listViews();
    }

    public static CollectionDefinition uiPages() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.uiPages();
    }

    public static CollectionDefinition uiMenus() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.uiMenus();
    }

    public static CollectionDefinition uiMenuItems() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.uiMenuItems();
    }

    // =========================================================================
    // Picklists
    // =========================================================================

    public static CollectionDefinition globalPicklists() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.globalPicklists();
    }

    public static CollectionDefinition picklistValues() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.picklistValues();
    }

    public static CollectionDefinition picklistDependencies() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.picklistDependencies();
    }

    // =========================================================================
    // Record Types & Validation
    // =========================================================================

    public static CollectionDefinition recordTypes() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.recordTypes();
    }

    public static CollectionDefinition recordTypePicklists() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.recordTypePicklists();
    }

    public static CollectionDefinition validationRules() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.validationRules();
    }

    // =========================================================================
    // Workflows & Automation
    // =========================================================================

    public static CollectionDefinition workflowRules() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.workflowRules();
    }

    public static CollectionDefinition workflowActions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.workflowActions();
    }

    public static CollectionDefinition workflowActionTypes() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.workflowActionTypes();
    }

    public static CollectionDefinition workflowPendingActions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.workflowPendingActions();
    }

    public static CollectionDefinition scripts() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.scripts();
    }

    public static CollectionDefinition scriptTriggers() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.scriptTriggers();
    }

    public static CollectionDefinition flows() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.flows();
    }

    public static CollectionDefinition approvalProcesses() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.approvalProcesses();
    }

    public static CollectionDefinition approvalSteps() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.approvalSteps();
    }

    public static CollectionDefinition approvalInstances() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.approvalInstances();
    }

    public static CollectionDefinition approvalStepInstances() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.approvalStepInstances();
    }

    public static CollectionDefinition scheduledJobs() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.scheduledJobs();
    }

    // =========================================================================
    // Communication
    // =========================================================================

    public static CollectionDefinition emailTemplates() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.emailTemplates();
    }

    public static CollectionDefinition webhooks() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.webhooks();
    }

    // =========================================================================
    // Integration
    // =========================================================================

    public static CollectionDefinition connectedApps() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.connectedApps();
    }

    public static CollectionDefinition connectedAppTokens() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.connectedAppTokens();
    }

    public static CollectionDefinition oidcProviders() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.oidcProviders();
    }

    // =========================================================================
    // Reports & Dashboards
    // =========================================================================

    public static CollectionDefinition reports() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.reports();
    }

    public static CollectionDefinition reportFolders() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.reportFolders();
    }

    public static CollectionDefinition dashboards() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.dashboards();
    }

    public static CollectionDefinition dashboardComponents() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.dashboardComponents();
    }

    // =========================================================================
    // Collaboration
    // =========================================================================

    public static CollectionDefinition notes() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.notes();
    }

    public static CollectionDefinition attachments() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.attachments();
    }

    // =========================================================================
    // Platform Management
    // =========================================================================

    public static CollectionDefinition bulkJobs() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.bulkJobs();
    }

    public static CollectionDefinition packages() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.packages();
    }

    public static CollectionDefinition packageItems() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.packageItems();
    }

    public static CollectionDefinition migrationRuns() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.migrationRuns();
    }

    // =========================================================================
    // Read-Only Audit/Log Collections
    // =========================================================================

    public static CollectionDefinition securityAuditLogs() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.securityAuditLogs();
    }

    public static CollectionDefinition setupAuditEntries() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.setupAuditEntries();
    }

    public static CollectionDefinition fieldHistory() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.fieldHistory();
    }

    public static CollectionDefinition workflowExecutionLogs() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.workflowExecutionLogs();
    }

    public static CollectionDefinition workflowActionLogs() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.workflowActionLogs();
    }

    public static CollectionDefinition workflowRuleVersions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.workflowRuleVersions();
    }

    public static CollectionDefinition scriptExecutionLogs() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.scriptExecutionLogs();
    }

    public static CollectionDefinition flowExecutions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.flowExecutions();
    }

    public static CollectionDefinition jobExecutionLogs() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.jobExecutionLogs();
    }

    public static CollectionDefinition bulkJobResults() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.bulkJobResults();
    }

    public static CollectionDefinition emailLogs() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.emailLogs();
    }

    public static CollectionDefinition webhookDeliveries() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.webhookDeliveries();
    }

    public static CollectionDefinition loginHistory() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.loginHistory();
    }

    public static CollectionDefinition collectionVersions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.collectionVersions();
    }

    public static CollectionDefinition fieldVersions() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.fieldVersions();
    }

    public static CollectionDefinition migrationSteps() {
        return com.emf.runtime.model.system.SystemCollectionDefinitions.migrationSteps();
    }
}
