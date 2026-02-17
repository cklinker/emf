/**
 * Custom React Hooks
 *
 * This module exports all custom React hooks used throughout the application.
 * Hooks include: useAuth, useConfig, useTheme, useI18n, usePlugins, useToast, etc.
 */

// Keyboard shortcuts hook for accessibility
export {
  useKeyboardShortcuts,
  useEscapeKey,
  formatShortcut,
  type KeyboardShortcut,
  type KeyboardModifiers,
  type UseKeyboardShortcutsOptions,
} from './useKeyboardShortcuts'

// Recent records hook for tracking user activity
export {
  useRecentRecords,
  type RecentRecord,
  type UseRecentRecordsReturn,
} from './useRecentRecords'

// Favorites hook for starred/pinned items
export { useFavorites, type FavoriteItem, type UseFavoritesReturn } from './useFavorites'

// Global keyboard shortcuts hook
export { useGlobalShortcuts } from './useGlobalShortcuts'

// Tenant ID hook for centralized tenant resolution
export { useTenantId, getTenantId } from './useTenantId'

// Collection schema hook for fetching collection metadata
export {
  useCollectionSchema,
  type FieldType,
  type FieldDefinition,
  type CollectionSchema,
  type UseCollectionSchemaReturn,
} from './useCollectionSchema'

// Collection records hook for fetching paginated record data
export {
  useCollectionRecords,
  type CollectionRecord,
  type SortState,
  type FilterOperator,
  type FilterCondition,
  type PaginatedResponse,
  type UseCollectionRecordsOptions,
  type UseCollectionRecordsReturn,
} from './useCollectionRecords'

// Record mutation hook for CRUD operations
export {
  useRecordMutation,
  type UseRecordMutationOptions,
  type UseRecordMutationReturn,
} from './useRecordMutation'

// Single record hook for fetching a record by ID
export { useRecord, type UseRecordOptions, type UseRecordReturn } from './useRecord'

// Object-level permission hook (CRUD permissions per collection)
export {
  useObjectPermissions,
  type ObjectPermissions,
  type UseObjectPermissionsReturn,
} from './useObjectPermissions'

// Field-level permission hook (visibility per field)
export {
  useFieldPermissions,
  type FieldVisibility,
  type FieldPermissionMap,
  type UseFieldPermissionsReturn,
} from './useFieldPermissions'

// Quick actions hook for fetching action definitions
export {
  useQuickActions,
  type UseQuickActionsOptions,
  type UseQuickActionsReturn,
} from './useQuickActions'

// Script execution hook for running server-side scripts
export {
  useScriptExecution,
  type ExecuteScriptParams,
  type UseScriptExecutionReturn,
} from './useScriptExecution'
