/**
 * Shared UI Components
 *
 * This module exports all shared UI components used throughout the application.
 * Components include: Header, Sidebar, Toast, ConfirmDialog, LoadingSpinner, ErrorMessage, etc.
 */

// Export shared components
export { AppShell, useAppShell } from './AppShell'
export type { AppShellProps, AppShellContextValue } from './AppShell'
export { BREAKPOINTS } from './AppShell'
export type { ScreenSize } from './AppShell'

// Header component
export { Header } from './Header'
export type { HeaderProps } from './Header'

// Sidebar component
export { Sidebar } from './Sidebar'
export type { SidebarProps } from './Sidebar'

// ErrorBoundary component
export { ErrorBoundary, ErrorFallback } from './ErrorBoundary'
export type { ErrorBoundaryProps, ErrorBoundaryState, ErrorFallbackProps } from './ErrorBoundary'

// Toast notification system
export {
  Toast,
  ToastProvider,
  useToast,
  ToastContext,
  DEFAULT_DURATION,
  DEFAULT_MAX_TOASTS,
} from './Toast'
export type {
  ToastType,
  ToastData,
  ToastProps,
  ToastContextValue,
  ToastProviderProps,
} from './Toast'

// ConfirmDialog component
export { ConfirmDialog } from './ConfirmDialog'
export type { ConfirmDialogProps, ConfirmDialogVariant } from './ConfirmDialog'

// LoadingSpinner component
export { LoadingSpinner } from './LoadingSpinner'
export type { LoadingSpinnerProps, SpinnerSize } from './LoadingSpinner'

// ErrorMessage component
export { ErrorMessage } from './ErrorMessage'
export type { ErrorMessageProps, ErrorType, ErrorVariant } from './ErrorMessage'

// CollectionForm component
export { CollectionForm, collectionFormSchema } from './CollectionForm'
export type {
  CollectionFormProps,
  CollectionFormData,
  CollectionFormSchema,
  Collection as CollectionFormCollection,
  StorageMode,
} from './CollectionForm'

// FieldsPanel component
export { FieldsPanel } from './FieldsPanel'
export type { FieldsPanelProps, FieldDefinition, FieldType, ValidationRule } from './FieldsPanel'

// FieldEditor component
export {
  FieldEditor,
  fieldEditorSchema,
  FIELD_TYPES,
  VALIDATION_RULES_BY_TYPE,
} from './FieldEditor'
export type {
  FieldEditorProps,
  FieldEditorFormData,
  ValidationRuleType,
  CollectionSummary,
} from './FieldEditor'

// AuthorizationPanel component
export { AuthorizationPanel, ROUTE_OPERATIONS, FIELD_OPERATIONS } from './AuthorizationPanel'
export type {
  AuthorizationPanelProps,
  RouteOperation,
  FieldOperation,
  PolicySummary,
  RoutePolicyConfig,
  FieldPolicyConfig,
  FieldDefinition as AuthorizationFieldDefinition,
  CollectionAuthz,
} from './AuthorizationPanel'

// LiveRegion component for screen reader announcements
export { LiveRegion, LiveRegionProvider, useAnnounce } from './LiveRegion'
export type {
  LiveRegionProps,
  LiveRegionPoliteness,
  LiveRegionContextValue,
  LiveRegionProviderProps,
} from './LiveRegion'

// ProtectedRoute component for route guards
export { ProtectedRoute, hasRequiredRoles, hasRequiredPolicies } from './ProtectedRoute'
export type { ProtectedRouteProps } from './ProtectedRoute'

// PageTransition component for page animations
export { PageTransition, usePrefersReducedMotion } from './PageTransition'
export type { PageTransitionProps, TransitionType } from './PageTransition'

// ValidationRuleEditor component
export { ValidationRuleEditor } from './ValidationRuleEditor'
export type { ValidationRuleEditorProps } from './ValidationRuleEditor'

// RecordTypeEditor component
export { RecordTypeEditor } from './RecordTypeEditor'
export type { RecordTypeEditorProps } from './RecordTypeEditor'

// PicklistValuesEditor component
export { PicklistValuesEditor } from './PicklistValuesEditor'
export type { PicklistValuesEditorProps } from './PicklistValuesEditor'

// PicklistDependencyEditor component
export { PicklistDependencyEditor } from './PicklistDependencyEditor'
export type { PicklistDependencyEditorProps } from './PicklistDependencyEditor'

// ExecutionLogModal component
export { ExecutionLogModal } from './ExecutionLogModal'
export type { ExecutionLogModalProps, LogColumn } from './ExecutionLogModal'

// SearchModal component for global search (Cmd+K)
export { SearchModal } from './SearchModal'
export type { SearchModalProps } from './SearchModal'

// RecentItemsDropdown component for header
export { RecentItemsDropdown } from './RecentItemsDropdown'
export type { RecentItemsDropdownProps } from './RecentItemsDropdown'

// PageLoader component for loading states
export { PageLoader, Skeleton, ContentLoader } from './PageLoader'
export type {
  PageLoaderProps,
  SkeletonProps,
  SkeletonVariant,
  ContentLoaderProps,
} from './PageLoader'
