/**
 * @kelta/components - Reusable React component library for Kelta
 *
 * This package provides pre-built React components for building Kelta UIs,
 * including data tables, forms, navigation, and layout components.
 */

// Context
export { KeltaProvider, useKeltaClient, useCurrentUser } from './context/KeltaContext';
export type { KeltaProviderProps } from './context/KeltaContext';

// Detail-page rail block components (see ./detail)
export * from './detail';

// DataTable
export { DataTable } from './DataTable/DataTable';
export type { DataTableProps, ColumnDefinition } from './DataTable/types';

// Chat primitives (telehealth slice 3)
export { MessageList } from './Chat/MessageList';
export { MessageComposer } from './Chat/MessageComposer';
export { ConversationListItem } from './Chat/ConversationListItem';
export type {
  ChatMessageItem,
  ChatConversationItem,
  ChatSenderType,
  MessageListProps,
  MessageComposerProps,
  ConversationListItemProps,
} from './Chat/types';

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

// UI primitives — exported so consumers can compose slots (e.g.
// RecordHeader's `moreMenu`) using the SAME DropdownMenu instance the
// shared components render. Mixing the consumer's own radix-based
// primitives into @kelta/components' DropdownMenu splits the radix
// context and the menu items never become interactive.
export {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from './ui/dropdown-menu';

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
export {
  parseVisibilityRule,
  evaluateVisibilityRule,
  isVisible,
} from './LayoutRenderer/visibilityRule';

// Layout client-side rules engine (compute / validate / default / transform)
export {
  RuleEngine,
  topologicalSort,
  downstreamRules,
  useLayoutRules,
} from './LayoutRenderer/clientRules';
export type {
  RuleEngineOptions,
  UseLayoutRulesOptions,
  UseLayoutRulesResult,
  FormBinding,
  RuleViolation,
  BeforeSaveResult,
  RuleEngineDiagnostic,
  RuleNode,
  TopoResult,
} from './LayoutRenderer/clientRules';

// Hooks
export { useResource } from './hooks/useResource';
export { useResourceList } from './hooks/useResourceList';
export { useDiscovery } from './hooks/useDiscovery';
