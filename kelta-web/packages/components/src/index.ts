/**
 * @kelta/components - Reusable React component library for Kelta
 *
 * This package provides pre-built React components for building Kelta UIs,
 * including data tables, forms, navigation, and layout components.
 */

// Context
export { KeltaProvider, useKeltaClient, useCurrentUser } from './context/KeltaContext';
export type { KeltaProviderProps } from './context/KeltaContext';

// DataTable
export { DataTable } from './DataTable/DataTable';
export type { DataTableProps, ColumnDefinition } from './DataTable/types';

// ResourceForm
export {
  ResourceForm,
  setComponentRegistry,
  getComponentRegistry,
} from './ResourceForm/ResourceForm';
export type {
  ResourceFormProps,
  FieldRendererProps,
  FieldRendererComponent,
} from './ResourceForm/types';

// ResourceDetail
export {
  ResourceDetail,
  setComponentRegistry as setDetailComponentRegistry,
  getComponentRegistry as getDetailComponentRegistry,
} from './ResourceDetail/ResourceDetail';
export type { ResourceDetailProps, FieldRenderer } from './ResourceDetail/types';

// FilterBuilder
export { FilterBuilder } from './FilterBuilder/FilterBuilder';
export type { FilterBuilderProps } from './FilterBuilder/types';

// Navigation
export { Navigation } from './Navigation/Navigation';
export type { NavigationProps, MenuItem } from './Navigation/types';

// Layout
export { PageLayout } from './Layout/PageLayout';
export { TwoColumnLayout } from './Layout/TwoColumnLayout';
export { ThreeColumnLayout } from './Layout/ThreeColumnLayout';
export type {
  PageLayoutProps,
  TwoColumnLayoutProps,
  ThreeColumnLayoutProps,
  ResponsiveBreakpoints,
} from './Layout/types';

// LayoutRenderer
export { LayoutRenderer } from './LayoutRenderer/LayoutRenderer';
export type { LayoutRendererProps, FieldRendererFn } from './LayoutRenderer/types';

// Hooks
export { useResource } from './hooks/useResource';
export { useResourceList } from './hooks/useResourceList';
export { useDiscovery } from './hooks/useDiscovery';
