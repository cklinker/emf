/**
 * Page Components
 * 
 * This module exports all page components used in the application routing.
 * Pages include: CollectionsPage, DashboardPage, RolesPage, PoliciesPage, etc.
 */

// CollectionsPage - Collection management page
export { CollectionsPage } from './CollectionsPage';
export type { CollectionsPageProps, Collection } from './CollectionsPage';

// CollectionDetailPage - Collection detail view page
export { CollectionDetailPage } from './CollectionDetailPage';
export type { CollectionDetailPageProps } from './CollectionDetailPage';

// CollectionFormPage - Collection create/edit form page
export { CollectionFormPage } from './CollectionFormPage';
export type { CollectionFormPageProps } from './CollectionFormPage';

// RolesPage - Authorization roles management page
export { RolesPage } from './RolesPage';
export type { RolesPageProps, Role } from './RolesPage';

// PoliciesPage - Authorization policies management page
export { PoliciesPage } from './PoliciesPage';
export type { PoliciesPageProps, Policy } from './PoliciesPage';

// OIDCProvidersPage - OIDC providers management page
export { OIDCProvidersPage } from './OIDCProvidersPage';
export type { OIDCProvidersPageProps, OIDCProvider } from './OIDCProvidersPage';

// ServicesPage - Domain services management page
export { ServicesPage } from './ServicesPage';
export type { ServicesPageProps, Service } from './ServicesPage';

// PageBuilderPage - UI page builder
export { PageBuilderPage } from './PageBuilderPage';
export type {
  PageBuilderPageProps,
  UIPage,
  PageLayout,
  PageComponent,
  ComponentPosition,
} from './PageBuilderPage';

// MenuBuilderPage - UI menu builder
export { MenuBuilderPage } from './MenuBuilderPage';
export type {
  MenuBuilderPageProps,
  UIMenu,
  UIMenuItem,
} from './MenuBuilderPage';

// PackagesPage - Package management page
export { PackagesPage } from './PackagesPage';
export type {
  PackagesPageProps,
  Package,
  PackageItem,
  ExportOptions,
  ImportPreview,
  ImportConflict,
  ImportResult,
  ImportError,
} from './PackagesPage';

// MigrationsPage - Migration management page
export { MigrationsPage } from './MigrationsPage';
export type {
  MigrationsPageProps,
  MigrationRun,
  MigrationStepResult,
  MigrationStatus,
} from './MigrationsPage';

// ResourceBrowserPage - Resource browser page
export { ResourceBrowserPage } from './ResourceBrowserPage';
export type { ResourceBrowserPageProps, CollectionSummary } from './ResourceBrowserPage';

// ResourceListPage - Resource list page for viewing collection data
export { ResourceListPage } from './ResourceListPage';
export type {
  ResourceListPageProps,
  Resource,
  FieldDefinition,
  FilterCondition,
} from './ResourceListPage';

// ResourceDetailPage - Resource detail view page
export { ResourceDetailPage } from './ResourceDetailPage';
export type {
  ResourceDetailPageProps,
  Resource as ResourceDetailResource,
  FieldDefinition as ResourceDetailFieldDefinition,
  CollectionSchema as ResourceDetailCollectionSchema,
} from './ResourceDetailPage';

// ResourceFormPage - Resource create/edit form page
export { ResourceFormPage } from './ResourceFormPage';
export type {
  ResourceFormPageProps,
  Resource as ResourceFormResource,
  FieldDefinition as ResourceFormFieldDefinition,
  CollectionSchema as ResourceFormCollectionSchema,
  ValidationRule,
  FormData,
  FormErrors,
} from './ResourceFormPage';

// PluginsPage - Plugin configuration page
export { PluginsPage } from './PluginsPage';
export type { PluginSettings, PluginsPageProps } from './PluginsPage';

// DashboardPage - System health and metrics dashboard
export { DashboardPage } from './DashboardPage';
export type {
  DashboardPageProps,
  DashboardData,
  HealthStatus,
  MetricDataPoint,
  SystemMetrics,
  RecentError,
} from './DashboardPage';

// LoginPage - Authentication login page
export { LoginPage } from './LoginPage';
export type { LoginPageProps } from './LoginPage';

// UnauthorizedPage - Access denied page
export { UnauthorizedPage } from './UnauthorizedPage';
export type { UnauthorizedPageProps } from './UnauthorizedPage';

// NotFoundPage - 404 error page
export { NotFoundPage } from './NotFoundPage';
export type { NotFoundPageProps } from './NotFoundPage';
