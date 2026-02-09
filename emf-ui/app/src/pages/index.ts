/**
 * Page Components
 *
 * This module exports all page components used in the application routing.
 * Pages include: CollectionsPage, DashboardPage, RolesPage, PoliciesPage, etc.
 */

// CollectionsPage - Collection management page
export { CollectionsPage } from './CollectionsPage'
export type { CollectionsPageProps, Collection } from './CollectionsPage'

// CollectionDetailPage - Collection detail view page
export { CollectionDetailPage } from './CollectionDetailPage'
export type { CollectionDetailPageProps } from './CollectionDetailPage'

// CollectionFormPage - Collection create/edit form page
export { CollectionFormPage } from './CollectionFormPage'
export type { CollectionFormPageProps } from './CollectionFormPage'

// CollectionWizardPage - Multi-step wizard for creating collections
export { CollectionWizardPage } from './CollectionWizardPage'
export type { CollectionWizardPageProps } from './CollectionWizardPage'

// RolesPage - Authorization roles management page
export { RolesPage } from './RolesPage'
export type { RolesPageProps, Role } from './RolesPage'

// PoliciesPage - Authorization policies management page
export { PoliciesPage } from './PoliciesPage'
export type { PoliciesPageProps, Policy } from './PoliciesPage'

// OIDCProvidersPage - OIDC providers management page
export { OIDCProvidersPage } from './OIDCProvidersPage'
export type { OIDCProvidersPageProps, OIDCProvider } from './OIDCProvidersPage'

// ServicesPage - Domain services management page
export { ServicesPage } from './ServicesPage'
export type { ServicesPageProps, Service } from './ServicesPage'

// PageBuilderPage - UI page builder
export { PageBuilderPage } from './PageBuilderPage'
export type {
  PageBuilderPageProps,
  UIPage,
  PageLayout,
  PageComponent,
  ComponentPosition,
} from './PageBuilderPage'

// MenuBuilderPage - UI menu builder
export { MenuBuilderPage } from './MenuBuilderPage'
export type { MenuBuilderPageProps, UIMenu, UIMenuItem } from './MenuBuilderPage'

// PackagesPage - Package management page
export { PackagesPage } from './PackagesPage'
export type {
  PackagesPageProps,
  Package,
  PackageItem,
  ExportOptions,
  ImportPreview,
  ImportConflict,
  ImportResult,
  ImportError,
} from './PackagesPage'

// MigrationsPage - Migration management page
export { MigrationsPage } from './MigrationsPage'
export type {
  MigrationsPageProps,
  MigrationRun,
  MigrationStepResult,
  MigrationStatus,
} from './MigrationsPage'

// ResourceBrowserPage - Resource browser page
export { ResourceBrowserPage } from './ResourceBrowserPage'
export type { ResourceBrowserPageProps, CollectionSummary } from './ResourceBrowserPage'

// ResourceListPage - Resource list page for viewing collection data
export { ResourceListPage } from './ResourceListPage'
export type {
  ResourceListPageProps,
  Resource,
  FieldDefinition,
  FilterCondition,
} from './ResourceListPage'

// ResourceDetailPage - Resource detail view page
export { ResourceDetailPage } from './ResourceDetailPage'
export type {
  ResourceDetailPageProps,
  Resource as ResourceDetailResource,
  FieldDefinition as ResourceDetailFieldDefinition,
  CollectionSchema as ResourceDetailCollectionSchema,
} from './ResourceDetailPage'

// ResourceFormPage - Resource create/edit form page
export { ResourceFormPage } from './ResourceFormPage'
export type {
  ResourceFormPageProps,
  Resource as ResourceFormResource,
  FieldDefinition as ResourceFormFieldDefinition,
  CollectionSchema as ResourceFormCollectionSchema,
  ValidationRule,
  FormData,
  FormErrors,
} from './ResourceFormPage'

// PluginsPage - Plugin configuration page
export { PluginsPage } from './PluginsPage'
export type { PluginSettings, PluginsPageProps } from './PluginsPage'

// TenantsPage - Platform admin tenant management page
export { TenantsPage } from './TenantsPage'
export type { TenantsPageProps, Tenant } from './TenantsPage'

// TenantDashboardPage - Per-tenant usage and health metrics
export { TenantDashboardPage } from './TenantDashboardPage'
export type { TenantDashboardPageProps } from './TenantDashboardPage'

// UsersPage - User management page
export { UsersPage } from './UsersPage'
export type { UsersPageProps } from './UsersPage'

// UserDetailPage - User detail view page
export { UserDetailPage } from './UserDetailPage'
export type { UserDetailPageProps } from './UserDetailPage'

// PicklistsPage - Global picklist management page
export { PicklistsPage } from './PicklistsPage'
export type { PicklistsPageProps } from './PicklistsPage'

// HomePage - User-centric landing page
export { HomePage } from './HomePage'
export type { HomePageProps } from './HomePage'

// DashboardPage - System health and metrics dashboard
export { DashboardPage } from './DashboardPage'
export type {
  DashboardPageProps,
  DashboardData,
  HealthStatus,
  MetricDataPoint,
  SystemMetrics,
  RecentError,
} from './DashboardPage'

// LoginPage - Authentication login page
export { LoginPage } from './LoginPage'
export type { LoginPageProps } from './LoginPage'

// UnauthorizedPage - Access denied page
export { UnauthorizedPage } from './UnauthorizedPage'
export type { UnauthorizedPageProps } from './UnauthorizedPage'

// ProfilesPage - Profile-based permission management page
export { ProfilesPage } from './ProfilesPage'
export type { ProfilesPageProps } from './ProfilesPage'

// PermissionSetsPage - Permission set management page
export { PermissionSetsPage } from './PermissionSetsPage'
export type { PermissionSetsPageProps } from './PermissionSetsPage'

// SharingSettingsPage - Record-level sharing settings
export { SharingSettingsPage } from './SharingSettingsPage'
export type { SharingSettingsPageProps } from './SharingSettingsPage'

// RoleHierarchyPage - Role hierarchy visualization
export { RoleHierarchyPage } from './RoleHierarchyPage'
export type { RoleHierarchyPageProps } from './RoleHierarchyPage'

// SetupAuditTrailPage - Configuration audit trail
export { SetupAuditTrailPage } from './SetupAuditTrailPage'
export type { SetupAuditTrailPageProps } from './SetupAuditTrailPage'

// GovernorLimitsPage - Governor limits dashboard
export { GovernorLimitsPage } from './GovernorLimitsPage'
export type { GovernorLimitsPageProps } from './GovernorLimitsPage'

// PageLayoutsPage - Page layout management page
export { PageLayoutsPage } from './PageLayoutsPage'
export type { PageLayoutsPageProps } from './PageLayoutsPage'

// ListViewsPage - List view management page
export { ListViewsPage } from './ListViewsPage'
export type { ListViewsPageProps } from './ListViewsPage'

// ReportsPage - Report builder management page
export { ReportsPage } from './ReportsPage'
export type { ReportsPageProps } from './ReportsPage'

// DashboardsPage - Dashboard builder management page
export { DashboardsPage } from './DashboardsPage'
export type { DashboardsPageProps } from './DashboardsPage'

// WorkflowRulesPage - Workflow rules management page
export { WorkflowRulesPage } from './WorkflowRulesPage'
export type { WorkflowRulesPageProps } from './WorkflowRulesPage'

// ApprovalProcessesPage - Approval processes management page
export { ApprovalProcessesPage } from './ApprovalProcessesPage'
export type { ApprovalProcessesPageProps } from './ApprovalProcessesPage'

// FlowsPage - Flow engine management page
export { FlowsPage } from './FlowsPage'
export type { FlowsPageProps } from './FlowsPage'

// ScheduledJobsPage - Scheduled jobs management page
export { ScheduledJobsPage } from './ScheduledJobsPage'
export type { ScheduledJobsPageProps } from './ScheduledJobsPage'

// EmailTemplatesPage - Email templates management page
export { EmailTemplatesPage } from './EmailTemplatesPage'
export type { EmailTemplatesPageProps } from './EmailTemplatesPage'

// ScriptsPage - Server-side scripts management page
export { ScriptsPage } from './ScriptsPage'
export type { ScriptsPageProps } from './ScriptsPage'

// WebhooksPage - Webhook management page
export { WebhooksPage } from './WebhooksPage'
export type { WebhooksPageProps } from './WebhooksPage'

// ConnectedAppsPage - Connected apps management page
export { ConnectedAppsPage } from './ConnectedAppsPage'
export type { ConnectedAppsPageProps } from './ConnectedAppsPage'

// BulkJobsPage - Bulk job management page
export { BulkJobsPage } from './BulkJobsPage'
export type { BulkJobsPageProps } from './BulkJobsPage'

// SetupHomePage - Setup directory page
export { SetupHomePage } from './SetupHomePage'
export type { SetupHomePageProps } from './SetupHomePage'

// WorkersPage - Worker management page
export { WorkersPage } from './WorkersPage'
export type { WorkersPageProps, Worker, WorkerAssignment, RebalanceResult } from './WorkersPage'

// NotFoundPage - 404 error page
export { NotFoundPage } from './NotFoundPage'
export type { NotFoundPageProps } from './NotFoundPage'
