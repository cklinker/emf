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

// Related records hook for fetching child records in master-detail relationships
export {
  useRelatedRecords,
  type RelatedRecordsOptions,
  type UseRelatedRecordsReturn,
} from './useRelatedRecords'

// Table keyboard navigation hook for accessible data table interaction
export {
  useTableKeyboardNav,
  type UseTableKeyboardNavOptions,
  type UseTableKeyboardNavReturn,
} from './useTableKeyboardNav'

// System-level permission hook (feature access per user)
export {
  useSystemPermissions,
  type SystemPermissions,
  type UseSystemPermissionsReturn,
} from './useSystemPermissions'

// Generic resource hooks — unified API for all collections (system + user-defined)
export { useResources, type UseResourcesOptions, type UseResourcesReturn } from './useResources'

export { useResource, type UseResourceOptions, type UseResourceReturn } from './useResource'

export {
  useCreateResource,
  type UseCreateResourceOptions,
  type UseCreateResourceReturn,
} from './useCreateResource'

export {
  useUpdateResource,
  type UseUpdateResourceOptions,
  type UpdateParams,
  type UseUpdateResourceReturn,
} from './useUpdateResource'

export {
  useDeleteResource,
  type UseDeleteResourceOptions,
  type UseDeleteResourceReturn,
} from './useDeleteResource'

export {
  useResourceAction,
  type UseResourceActionOptions,
  type ActionParams,
  type UseResourceActionReturn,
} from './useResourceAction'

// Included resources hook — extract display maps from JSON:API includes
export {
  useIncludedResources,
  useExtractIncluded,
  type IncludedResourceConfig,
  type UseIncludedResourcesReturn,
} from './useIncludedResources'
